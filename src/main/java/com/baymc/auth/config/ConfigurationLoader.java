package com.baymc.auth.config;

import com.baymc.auth.Settings;
import com.baymc.auth.command.CommandPermissionState;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

public final class ConfigurationLoader {

  private static final String CONFIG_FILE = "config.yml";
  private static final String MESSAGES_FILE = "messages.yml";
  private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

  private ConfigurationLoader() {
  }

  public static Loaded load(Path directory) {
    Path configPath = directory.resolve(CONFIG_FILE);
    Path messagesPath = directory.resolve(MESSAGES_FILE);
    String configDefaultsText = resource(CONFIG_FILE);
    String messagesDefaultsText = resource(MESSAGES_FILE);
    Document configDefaults = parse("内置 " + CONFIG_FILE, configDefaultsText);
    Document messagesDefaults = parse("内置 " + MESSAGES_FILE, messagesDefaultsText);
    boolean createConfig = !Files.exists(configPath);
    boolean createMessages = !Files.exists(messagesPath);

    String configText = createConfig
        ? initializeToken(configDefaultsText, configDefaults) : read(configPath);
    String messagesText = createMessages ? messagesDefaultsText : read(messagesPath);
    Document config = parse(configPath.toString(), configText);
    Document messages = parse(messagesPath.toString(), messagesText);
    List<String> warnings = new ArrayList<>();
    Map<String, Object> configValues = merge(configDefaults.values(), config.values(), "", config, warnings);
    Map<String, Object> messageValues = merge(messagesDefaults.values(), messages.values(), "", messages, warnings);
    BindingContext context = new BindingContext(config, messages);

    Settings settings = bind(Settings.class, configValues, "", context);
    settings.PREFIX = (String) convert(messageValues.get("prefix"), String.class, "prefix", context);
    settings.MAIN.STRINGS = bind(Settings.MAIN.STRINGS.class,
        mapping(messageValues.get("messages"), "main.strings", context), "main.strings", context);
    validate(settings, context);

    // 两份配置均校验成功后才准备补全, 已有值由源文件保留
    String completedConfig = createConfig ? configText
        : YamlDefaultsUpdater.complete(configPath.toString(), configText, configDefaultsText);
    String completedMessages = createMessages ? messagesText
        : YamlDefaultsUpdater.complete(messagesPath.toString(), messagesText, messagesDefaultsText);
    verifyCompleted(configDefaults, config, configValues, completedConfig);
    verifyCompleted(messagesDefaults, messages, messageValues, completedMessages);

    ConfigurationWriter.save(List.of(
        new ConfigurationWriter.Change(configPath, createConfig ? null : configText, completedConfig),
        new ConfigurationWriter.Change(messagesPath, createMessages ? null : messagesText, completedMessages)
    ));
    return new Loaded(settings, List.copyOf(warnings));
  }

  private static void verifyCompleted(Document defaults, Document original, Map<String, Object> expected, String text) {
    Document completed = parse(original.file(), text);
    requireDefaultKeys(defaults.values(), completed.values(), "", completed);
    Map<String, Object> actual = merge(defaults.values(), completed.values(), "", completed, new ArrayList<>());
    if (!expected.equals(actual)
        || !preservesValues(original.values(), completed.values(), defaults.values(), new IdentityHashMap<>())) {
      throw failure(original.file(), "$", 1, "自动补全结果改变了已有配置值, 已取消保存");
    }
  }

  private static boolean preservesValues(Object before, Object after, Object defaults,
      Map<Object, Map<Object, Map<Object, Boolean>>> visited) {
    if (before == after) {
      return true;
    }
    if (before instanceof Map<?, ?> beforeMap && after instanceof Map<?, ?> afterMap) {
      // 别名可能形成循环, 只比较一次相同的节点对
      if (alreadyCompared(before, after, defaults, visited)) {
        return true;
      }
      Map<?, ?> defaultMap = defaults instanceof Map<?, ?> values ? values : Map.of();
      for (Map.Entry<?, ?> entry : beforeMap.entrySet()) {
        if (!afterMap.containsKey(entry.getKey())
            || !preservesValues(entry.getValue(), afterMap.get(entry.getKey()), defaultMap.get(entry.getKey()), visited)) {
          return false;
        }
      }
      return afterMap.keySet().stream().allMatch(key -> beforeMap.containsKey(key) || defaultMap.containsKey(key));
    }
    if (before instanceof List<?> beforeList && after instanceof List<?> afterList) {
      if (beforeList.size() != afterList.size()) {
        return false;
      }
      if (alreadyCompared(before, after, null, visited)) {
        return true;
      }
      for (int i = 0; i < beforeList.size(); i++) {
        if (!preservesValues(beforeList.get(i), afterList.get(i), null, visited)) {
          return false;
        }
      }
      return true;
    }
    return Objects.equals(before, after);
  }

  private static boolean alreadyCompared(Object before, Object after, Object defaults,
      Map<Object, Map<Object, Map<Object, Boolean>>> visited) {
    Map<Object, Map<Object, Boolean>> matches = visited.computeIfAbsent(before, ignored -> new IdentityHashMap<>());
    Map<Object, Boolean> contexts = matches.computeIfAbsent(after, ignored -> new IdentityHashMap<>());
    return contexts.put(defaults, Boolean.TRUE) != null;
  }

  private static void requireDefaultKeys(Map<?, ?> defaults, Map<?, ?> values, String path, Document document) {
    for (Map.Entry<?, ?> entry : defaults.entrySet()) {
      String child = childPath(path, (String) entry.getKey());
      if (!values.containsKey(entry.getKey())) {
        throw failure(document.file(), child, document.line(child), "无法自动补全缺失字段, 已取消保存");
      }
      if (entry.getValue() instanceof Map<?, ?> nested && values.get(entry.getKey()) instanceof Map<?, ?> actual) {
        requireDefaultKeys(nested, actual, child, document);
      }
    }
  }

  private static String resource(String name) {
    try (InputStream input = ConfigurationLoader.class.getResourceAsStream("/" + name)) {
      if (input == null) {
        throw failure("内置 " + name, "$", 1, "插件中缺少默认配置资源");
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw failure("内置 " + name, "$", 1, "无法读取默认配置资源");
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw failure(file.toString(), "$", 1, "无法以 UTF-8 读取配置文件");
    }
  }

  private static Document parse(String file, String text) {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setAllowRecursiveKeys(false);
    options.setMaxAliasesForCollections(50);
    options.setNestingDepthLimit(50);
    Yaml yaml = new Yaml(new SafeConstructor(options));
    Map<String, Integer> lines = new LinkedHashMap<>();
    try {
      Node root = yaml.compose(new StringReader(text));
      if (root == null) {
        return new Document(file, Map.of(), lines);
      }
      if (!(root instanceof MappingNode)) {
        throw failure(file, "$", root.getStartMark().getLine() + 1, "配置根节点必须是键值映射");
      }
      collectLines(root, "", lines, new IdentityHashMap<>());
      Object loaded = yaml.load(text);
      if (!(loaded instanceof Map<?, ?> values)) {
        throw failure(file, "$", 1, "配置根节点必须是键值映射");
      }
      return new Document(file, stringKeys(values, file, "$", 1), lines);
    } catch (MarkedYAMLException e) {
      int line = e.getProblemMark() == null ? 1 : e.getProblemMark().getLine() + 1;
      String reason = e instanceof DuplicateKeyException
          ? "存在重复配置键" : "YAML 语法或标签无效, 请检查缩进, 引号及数据类型";
      // 不附加原始异常, 其消息可能包含密码, 令牌或整段配置内容
      throw failure(file, closestPath(lines, line), line, reason);
    } catch (YAMLException e) {
      throw failure(file, "$", 1, "YAML 内容无法安全解析, 不允许任意对象标签");
    } catch (ConfigurationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw failure(file, "$", 1, "YAML 标量或数据结构无效");
    }
  }

  private static void collectLines(Node node, String path, Map<String, Integer> lines, Map<Node, Boolean> visited) {
    // 别名共享的节点只遍历一次, 避免循环或重复展开
    if (visited.put(node, Boolean.TRUE) != null) {
      return;
    }
    if (!path.isEmpty()) {
      lines.putIfAbsent(path, node.getStartMark().getLine() + 1);
    }
    if (node instanceof MappingNode mapping) {
      for (NodeTuple tuple : mapping.getValue()) {
        if (tuple.getKeyNode() instanceof ScalarNode key) {
          String child = childPath(path, key.getValue());
          lines.put(child, key.getStartMark().getLine() + 1);
          collectLines(tuple.getValueNode(), child, lines, visited);
        }
      }
    } else if (node instanceof SequenceNode sequence) {
      for (int i = 0; i < sequence.getValue().size(); i++) {
        collectLines(sequence.getValue().get(i), path + "[" + i + "]", lines, visited);
      }
    }
  }

  private static String closestPath(Map<String, Integer> lines, int line) {
    String path = "$";
    int closest = 0;
    for (Map.Entry<String, Integer> entry : lines.entrySet()) {
      if (entry.getValue() <= line && entry.getValue() >= closest) {
        closest = entry.getValue();
        path = entry.getKey();
      }
    }
    return path;
  }

  private static Map<String, Object> stringKeys(Map<?, ?> values, String file, String path, int line) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : values.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw failure(file, path, line, "配置字段名必须是字符串");
      }
      result.put(key, entry.getValue());
    }
    return result;
  }

  private static Map<String, Object> merge(Map<String, Object> defaults, Map<String, Object> overrides,
      String path, Document document, List<String> warnings) {
    Map<String, Object> merged = new LinkedHashMap<>(defaults);
    for (Map.Entry<String, Object> entry : overrides.entrySet()) {
      String child = childPath(path, entry.getKey());
      boolean legacyMessage = document.file().endsWith(CONFIG_FILE) && (child.equals("prefix") || child.equals("main.strings"));
      if (legacyMessage) {
        warnings.add(document.location(child) + "旧消息配置已忽略, 请手动搬至 messages.yml");
        continue;
      }
      if (!defaults.containsKey(entry.getKey())) {
        warnings.add(document.location(child) + "未知配置键已忽略, 该字段保持不变");
        continue;
      }
      Object defaultValue = defaults.get(entry.getKey());
      Object overrideValue = entry.getValue();
      if (defaultValue instanceof Map<?, ?> defaultMap && overrideValue instanceof Map<?, ?> overrideMap) {
        merged.put(entry.getKey(), merge(
            stringKeys(defaultMap, document.file(), child, document.line(child)),
            stringKeys(overrideMap, document.file(), child, document.line(child)),
            child, document, warnings));
      } else {
        merged.put(entry.getKey(), overrideValue);
      }
    }
    return merged;
  }

  private static String initializeToken(String text, Document defaults) {
    Object main = defaults.values().get("main");
    Object backend = main instanceof Map<?, ?> values ? values.get("backend-api") : null;
    Object token = backend instanceof Map<?, ?> values ? values.get("token") : null;
    if (token instanceof String value && !value.isBlank()) {
      return text;
    }
    String path = "main.backend-api.token";
    Integer line = defaults.lines().get(path);
    if (line == null) {
      throw failure(defaults.file(), path, 1, "默认配置缺少后端令牌字段");
    }
    int start = 0;
    for (int i = 1; i < line; i++) {
      start = text.indexOf('\n', start) + 1;
    }
    int end = text.indexOf('\n', start);
    if (end < 0) {
      end = text.length();
    }
    String originalLine = text.substring(start, end);
    Matcher matcher = Pattern.compile("^(\\s*token\\s*:\\s*)(?:\"\"|'')?(\\s*(?:#.*)?)$").matcher(originalLine);
    if (!matcher.matches()) {
      throw failure(defaults.file(), path, line, "默认后端令牌必须是单行空字符串");
    }
    byte[] random = new byte[32];
    TOKEN_RANDOM.nextBytes(random);
    String replacement = matcher.group(1) + "\"" + HexFormat.of().formatHex(random) + "\"" + matcher.group(2);
    return text.substring(0, start) + replacement + text.substring(end);
  }

  private static <T> T bind(Class<T> type, Map<String, Object> values, String path, BindingContext context) {
    T instance;
    try {
      instance = type.getDeclaredConstructor().newInstance();
      for (Field field : type.getFields()) {
        if (Modifier.isStatic(field.getModifiers())
            || type == Settings.class && field.getName().equals("PREFIX")
            || type == Settings.MAIN.class && field.getName().equals("STRINGS")) {
          continue;
        }
        String key = field.getName().toLowerCase(Locale.ROOT).replace('_', '-');
        String child = childPath(path, key);
        if (!values.containsKey(key)) {
          throw context.error(child, "默认配置资源缺少此字段");
        }
        field.set(instance, convert(values.get(key), field.getGenericType(), child, context));
      }
    } catch (ReflectiveOperationException e) {
      throw context.error(path, "无法初始化配置模型");
    }
    return instance;
  }

  private static Object convert(Object value, Type type, String path, BindingContext context) {
    if (value == null) {
      throw context.error(path, "配置值不能为 null");
    }
    if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == List.class) {
      if (!(value instanceof List<?> list)) {
        throw context.error(path, "必须是 YAML 列表");
      }
      List<Object> converted = new ArrayList<>(list.size());
      for (int i = 0; i < list.size(); i++) {
        converted.add(convert(list.get(i), parameterized.getActualTypeArguments()[0], path + "[" + i + "]", context));
      }
      return List.copyOf(converted);
    }
    if (!(type instanceof Class<?> target)) {
      throw context.error(path, "配置模型包含不支持的字段类型");
    }
    if (target == String.class) {
      if (value instanceof String || value instanceof Number || value instanceof Boolean) {
        return value.toString();
      }
      throw context.error(path, "必须是字符串, 数字或布尔值, 不能是映射或列表");
    }
    if (target == boolean.class || target == Boolean.class) {
      if (value instanceof Boolean) {
        return value;
      }
      if (value instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) {
        return Boolean.parseBoolean(text);
      }
      throw context.error(path, "必须是 true 或 false");
    }
    if (target.isEnum()) {
      String name;
      if (value instanceof String text) {
        name = text;
      } else if (target == CommandPermissionState.class && value instanceof Boolean flag) {
        name = flag.toString();
      } else {
        throw context.error(path, "必须是有效的枚举名称");
      }
      for (Object option : target.getEnumConstants()) {
        if (((Enum<?>) option).name().equalsIgnoreCase(name)) {
          return option;
        }
      }
      throw context.error(path, "枚举名称无效, 请参考默认配置中的可用值");
    }
    if (target == int.class || target == Integer.class || target == long.class || target == Long.class
        || target == double.class || target == Double.class || target == float.class || target == Float.class) {
      return number(value, target, path, context);
    }
    if (target.getName().startsWith(Settings.class.getName() + "$")) {
      return bind(target, mapping(value, path, context), path, context);
    }
    throw context.error(path, "配置模型包含不支持的字段类型");
  }

  private static Object number(Object value, Class<?> type, String path, BindingContext context) {
    if (!(value instanceof Number) && !(value instanceof String)) {
      throw context.error(path, "必须是有效数字");
    }
    try {
      if (type == int.class || type == Integer.class) {
        return new BigDecimal(value.toString()).intValueExact();
      }
      if (type == long.class || type == Long.class) {
        return new BigDecimal(value.toString()).longValueExact();
      }
      double parsed = Double.parseDouble(value.toString());
      if (!Double.isFinite(parsed)) {
        throw context.error(path, "必须是有限数值");
      }
      if (type == float.class || type == Float.class) {
        float converted = (float) parsed;
        if (!Float.isFinite(converted)) {
          throw context.error(path, "数值超出允许范围");
        }
        return converted;
      }
      return parsed;
    } catch (NumberFormatException | ArithmeticException e) {
      throw context.error(path, "数值格式错误或超出字段允许范围");
    }
  }

  private static Map<String, Object> mapping(Object value, String path, BindingContext context) {
    if (!(value instanceof Map<?, ?> values)) {
      throw context.error(path, "必须是 YAML 键值映射");
    }
    Document document = context.document(path);
    String sourcePath = context.sourcePath(path);
    return stringKeys(values, document.file(), sourcePath, document.line(sourcePath));
  }

  private static void validate(Settings settings, BindingContext context) {
    Settings.MAIN main = settings.MAIN;
    positive(main.AUTH_TIME, "main.auth-time", context);
    positive(main.LOGIN_ATTEMPTS, "main.login-attempts", context);
    positive(main.PURGE_CACHE_MILLIS, "main.purge-cache-millis", context);
    positive(main.PURGE_PREMIUM_CACHE_MILLIS, "main.purge-premium-cache-millis", context);
    positive(main.PURGE_BRUTEFORCE_CACHE_MILLIS, "main.purge-bruteforce-cache-millis", context);
    between(main.BCRYPT_COST, 4, 31, "main.bcrypt-cost", context);
    positive(main.MIN_PASSWORD_LENGTH, "main.min-password-length", context);
    between(main.MAX_PASSWORD_LENGTH, 1, 71, "main.max-password-length", context);
    if (main.MAX_PASSWORD_LENGTH < main.MIN_PASSWORD_LENGTH) {
      throw context.error("main.max-password-length", "不能小于 main.min-password-length");
    }
    nonnegative(main.IP_LIMIT_REGISTRATIONS, "main.ip-limit-registrations", context);
    nonnegative(main.IP_LIMIT_VALID_TIME, "main.ip-limit-valid-time", context);
    positive(main.BRUTEFORCE_MAX_ATTEMPTS, "main.bruteforce-max-attempts", context);
    positive(main.TOTP_RECOVERY_CODES_AMOUNT, "main.totp-recovery-codes-amount", context);
    nonnegative(main.PREMIUM_AND_FLOODGATE_MESSAGES_DELAY, "main.premium-and-floodgate-messages-delay", context);
    nonnegative(main.CRACKED_TITLE_SETTINGS.FADE_IN, "main.cracked-title-settings.fade-in", context);
    nonnegative(main.CRACKED_TITLE_SETTINGS.STAY, "main.cracked-title-settings.stay", context);
    nonnegative(main.CRACKED_TITLE_SETTINGS.FADE_OUT, "main.cracked-title-settings.fade-out", context);
    nonnegative(main.PREMIUM_TITLE_SETTINGS.FADE_IN, "main.premium-title-settings.fade-in", context);
    nonnegative(main.PREMIUM_TITLE_SETTINGS.STAY, "main.premium-title-settings.stay", context);
    nonnegative(main.PREMIUM_TITLE_SETTINGS.FADE_OUT, "main.premium-title-settings.fade-out", context);
    between(main.WORLD_LIGHT_LEVEL, 0, 15, "main.world-light-level", context);
    if (!Float.isFinite((float) main.AUTH_COORDS.YAW)) {
      throw context.error("main.auth-coords.yaw", "数值超出可用朝向范围");
    }
    if (!Float.isFinite((float) main.AUTH_COORDS.PITCH)) {
      throw context.error("main.auth-coords.pitch", "数值超出可用朝向范围");
    }
    if (main.BACKEND_API.ENABLED && main.BACKEND_API.TOKEN.isBlank()) {
      throw context.error("main.backend-api.token", "启用后端 API 时必须配置非空令牌, 已有配置不会自动改写");
    }
    try {
      Pattern.compile(main.ALLOWED_NICKNAME_REGEX);
    } catch (PatternSyntaxException e) {
      throw context.error("main.allowed-nickname-regex", "玩家名称正则表达式无效");
    }
    validateUrls(main, context);
    validateMessages(settings, context);
  }

  private static void validateUrls(Settings.MAIN main, BindingContext context) {
    String premiumPath = "main.ispremium-auth-url";
    try {
      requireHttp(String.format(Locale.ROOT, main.ISPREMIUM_AUTH_URL, "ExamplePlayer"), premiumPath, context);
    } catch (IllegalArgumentException e) {
      throw context.error(premiumPath, "正版查询 URL 模板无效");
    }
    String qrPath = "main.qr-generator-url";
    requireHttp(main.QR_GENERATOR_URL.replace("{data}", "ExampleData"), qrPath, context);
  }

  private static void requireHttp(String value, String path, BindingContext context) {
    try {
      URI uri = URI.create(value);
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
        throw context.error(path, "必须是包含主机名的 http 或 https URL");
      }
    } catch (IllegalArgumentException e) {
      throw context.error(path, "URL 格式无效");
    }
  }

  private static void validateMessages(Settings settings, BindingContext context) {
    Component prefix;
    try {
      prefix = MiniMessage.miniMessage().deserialize(settings.PREFIX);
    } catch (RuntimeException e) {
      throw context.error("prefix", "MiniMessage 前缀格式无效");
    }
    MiniMessage serializer = MiniMessage.builder()
        .tags(TagResolver.builder()
            .resolver(StandardTags.defaults())
            .tag("prefix", Tag.selfClosingInserting(prefix))
            .build())
        .build();
    TagResolver parameters = TagResolver.builder()
        .resolver(Formatter.number("seconds", 1))
        .resolver(Formatter.number("attempts", 1))
        .resolver(Placeholder.unparsed("expected_name", "示例玩家"))
        .resolver(Placeholder.unparsed("current_name", "示例玩家"))
        .resolver(Placeholder.unparsed("player", "示例玩家"))
        .resolver(Placeholder.unparsed("password", "示例密码"))
        .resolver(Placeholder.unparsed("secret", "示例密钥"))
        .resolver(Placeholder.unparsed("recovery_codes", "示例恢复码"))
        .resolver(Placeholder.unparsed("reason", "配置错误"))
        .build();
    for (Field field : Settings.MAIN.STRINGS.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      String path = "main.strings." + field.getName().toLowerCase(Locale.ROOT).replace('_', '-');
      try {
        serializer.deserialize((String) field.get(settings.MAIN.STRINGS), parameters);
      } catch (IllegalAccessException | RuntimeException e) {
        throw context.error(path, "MiniMessage 消息格式无效");
      }
    }
  }

  private static void positive(long value, String path, BindingContext context) {
    if (value <= 0) {
      throw context.error(path, "必须大于 0");
    }
  }

  private static void nonnegative(long value, String path, BindingContext context) {
    if (value < 0) {
      throw context.error(path, "不能小于 0");
    }
  }

  private static void between(long value, long minimum, long maximum, String path, BindingContext context) {
    if (value < minimum || value > maximum) {
      throw context.error(path, "必须在 " + minimum + " 到 " + maximum + " 之间");
    }
  }

  private static String childPath(String path, String key) {
    return path.isEmpty() ? key : path + "." + key;
  }

  private static ConfigurationException failure(String file, String path, int line, String reason) {
    return new ConfigurationException(file + ", 字段 " + (path.isEmpty() ? "$" : path) + ", 第 " + line + " 行: " + reason);
  }

  private record Document(String file, Map<String, Object> values, Map<String, Integer> lines) {

    int line(String path) {
      String current = path;
      while (!current.isEmpty()) {
        Integer line = this.lines.get(current);
        if (line != null) {
          return line;
        }
        int separator = Math.max(current.lastIndexOf('.'), current.lastIndexOf('['));
        if (separator < 0) {
          break;
        }
        current = current.substring(0, separator);
      }
      return 1;
    }

    String location(String path) {
      return this.file + ", 字段 " + path + ", 第 " + this.line(path) + " 行: ";
    }
  }

  private record BindingContext(Document config, Document messages) {

    Document document(String path) {
      return path.equals("prefix") || path.equals("main.strings") || path.startsWith("main.strings.") ? this.messages : this.config;
    }

    String sourcePath(String path) {
      return path.equals("main.strings") || path.startsWith("main.strings.")
          ? "messages" + path.substring("main.strings".length()) : path;
    }

    ConfigurationException error(String path, String reason) {
      Document document = this.document(path);
      String sourcePath = this.sourcePath(path);
      return failure(document.file(), sourcePath, document.line(sourcePath), reason);
    }
  }

  public record Loaded(Settings settings, List<String> warnings) {
  }
}

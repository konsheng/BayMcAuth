package com.baymc.auth.common.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/*
 * YAML 文件加载和缺键补全
 *
 * <p>运行时读取会把用户文件覆盖到默认树上
 * 文件缺失时写入完整默认文本, 文件存在但缺少键时追加缺失键块
 * 这样保留用户原有文本和注释, 同时让新键能够出现在文件末尾供管理员调整
 */
public final class YamlDocument {
    private final Map<String, Object> values;

    private YamlDocument(Map<String, Object> values) {
        this.values = values;
    }

    public static YamlDocument load(Path path, String defaultText, Consumer<String> warnings) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(defaultText, "defaultText");
        Consumer<String> warn = warnings == null ? ignored -> { } : warnings;
        Yaml yaml = createYaml();
        Map<String, Object> defaults = asMap(yaml.load(defaultText));
        if (Files.notExists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, defaultText, StandardCharsets.UTF_8);
            return new YamlDocument(defaults);
        }

        String userText = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, Object> user = asMap(yaml.load(userText));
        Map<String, Object> missing = missingTree(defaults, user);
        if (!missing.isEmpty()) {
            String appended = "\n\n# 自动补全缺失键, 已保留原有用户值\n" + yaml.dump(missing);
            Files.writeString(path, userText + appended, StandardCharsets.UTF_8);
            warn.accept("已自动补全缺失配置或语言键: " + path);
        }
        Map<String, Object> merged = deepMerge(defaults, user);
        return new YamlDocument(merged);
    }

    public static YamlDocument fromString(String text) {
        return new YamlDocument(asMap(createYaml().load(text)));
    }

    public Map<String, Object> values() {
        return values;
    }

    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMerge(Map<String, Object> defaults, Map<String, Object> user) {
        Map<String, Object> result = new LinkedHashMap<>(defaults);
        for (Map.Entry<String, Object> entry : user.entrySet()) {
            Object defaultValue = result.get(entry.getKey());
            Object userValue = entry.getValue();
            if (defaultValue instanceof Map<?, ?> defaultMap && userValue instanceof Map<?, ?> userMap) {
                result.put(entry.getKey(), deepMerge(asMap(defaultMap), asMap(userMap)));
            } else {
                result.put(entry.getKey(), userValue);
            }
        }
        return result;
    }

    private static Map<String, Object> asMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Map<String, Object> missingTree(Map<String, Object> defaults, Map<String, Object> user) {
        Map<String, Object> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!user.containsKey(entry.getKey())) {
                missing.put(entry.getKey(), entry.getValue());
                continue;
            }
            Object defaultValue = entry.getValue();
            Object userValue = user.get(entry.getKey());
            if (defaultValue instanceof Map<?, ?> defaultMap && userValue instanceof Map<?, ?> userMap) {
                Map<String, Object> childMissing = missingTree(asMap(defaultMap), asMap(userMap));
                if (!childMissing.isEmpty()) {
                    missing.put(entry.getKey(), childMissing);
                }
            }
        }
        return missing;
    }
}

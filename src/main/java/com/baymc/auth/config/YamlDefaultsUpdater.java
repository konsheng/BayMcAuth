package com.baymc.auth.config;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.events.CommentEvent;
import org.yaml.snakeyaml.events.DocumentEndEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

final class YamlDefaultsUpdater {

  private YamlDefaultsUpdater() {
  }

  static String complete(String file, String currentText, String defaultsText) {
    Source current = parse(file, currentText);
    Source defaults = parse("内置 " + file, defaultsText);
    if (!(defaults.root instanceof MappingNode defaultMap)) {
      throw error(defaults, "$", defaults.root, "默认配置必须是键值映射");
    }
    if (current.root == null || current.root instanceof ScalarNode scalar
        && scalar.getTag().equals(Tag.NULL) && scalar.getValue().isEmpty()) {
      String result = fillEmpty(current, defaults, defaultMap);
      parse(file, result);
      return result;
    }
    if (!(current.root instanceof MappingNode currentMap)) {
      throw error(current, "$", current.root, "现有配置必须是键值映射");
    }
    List<Insertion> insertions = new ArrayList<>();
    plan(current, defaults, currentMap, defaultMap, "", true, insertions);
    if (insertions.isEmpty()) {
      return currentText;
    }
    insertions.sort(Comparator.comparingInt(Insertion::offset).reversed());
    StringBuilder result = new StringBuilder(currentText);
    for (Insertion insertion : insertions) {
      result.insert(insertion.offset(), insertion.text());
    }
    String completed = result.toString();
    parse(file, completed);
    return completed;
  }

  private static Source parse(String file, String text) {
    Yaml yaml = newYaml(false);
    try {
      // SafeConstructor 校验重复键及对象标签, compose 保留源节点位置
      yaml.load(text);
      Node root = yaml.compose(new StringReader(text));
      Source source = new Source(file, text, root);
      // 注释只通过事件流读取, 避免只有注释的文件进入注释节点构造流程
      for (Event event : newYaml(true).parse(new StringReader(text))) {
        if (event instanceof CommentEvent comment && comment.getCommentType() != CommentType.BLANK_LINE) {
          source.comments.add(new Comment(source.offset(comment.getStartMark()), "#" + comment.getValue()));
        } else if (event instanceof DocumentEndEvent end && end.getExplicit()) {
          source.documentEnd = source.offset(end.getStartMark());
        }
      }
      countReferences(root, source.references, new IdentityHashMap<>());
      return source;
    } catch (ConfigurationException e) {
      throw e;
    } catch (MarkedYAMLException e) {
      int line = e.getProblemMark() == null ? 1 : e.getProblemMark().getLine() + 1;
      throw new ConfigurationException(file + ", 字段 $, 第 " + line + " 行: YAML 无法安全解析, 请检查重复键, 缩进或标签");
    } catch (RuntimeException e) {
      throw new ConfigurationException(file + ", 字段 $, 第 1 行: YAML 数据结构无效, 未补写默认字段");
    }
  }

  private static Yaml newYaml(boolean processComments) {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setAllowRecursiveKeys(false);
    options.setMaxAliasesForCollections(50);
    options.setNestingDepthLimit(50);
    options.setProcessComments(processComments);
    return new Yaml(new SafeConstructor(options));
  }

  private static void countReferences(Node node, Map<Node, Integer> counts, Map<Node, Boolean> visited) {
    if (node == null) {
      return;
    }
    counts.merge(node, 1, Integer::sum);
    if (visited.put(node, Boolean.TRUE) != null) {
      return;
    }
    if (node instanceof MappingNode mapping) {
      for (NodeTuple tuple : mapping.getValue()) {
        countReferences(tuple.getValueNode(), counts, visited);
      }
    } else if (node instanceof SequenceNode sequence) {
      for (Node child : sequence.getValue()) {
        countReferences(child, counts, visited);
      }
    }
  }

  private static void plan(Source current, Source defaults, MappingNode actual, MappingNode expected,
      String path, boolean local, List<Insertion> insertions) {
    int initialSize = insertions.size();
    Map<String, Entry> effective = entries(current, actual, path, new IdentityHashMap<>());
    List<NodeTuple> missing = new ArrayList<>();
    for (NodeTuple tuple : expected.getValue()) {
      String key = key(defaults, tuple.getKeyNode(), path);
      String child = path.isEmpty() ? key : path + "." + key;
      Entry existing = effective.get(key);
      if (existing == null) {
        missing.add(tuple);
      } else if (tuple.getValueNode() instanceof MappingNode nestedDefaults) {
        if (!(existing.node() instanceof MappingNode nestedActual)) {
          throw error(current, child, existing.node(), "已有值不是映射, 无法安全补写子字段");
        }
        plan(current, defaults, nestedActual, nestedDefaults, child, existing.local(), insertions);
      }
    }
    if (!missing.isEmpty()) {
      if (actual.getFlowStyle() == DumperOptions.FlowStyle.FLOW) {
        insertions.add(flowInsertion(current, defaults, actual, missing, path));
      } else {
        insertions.add(blockInsertion(current, defaults, actual, missing, path));
      }
    }
    if (insertions.size() != initialSize && (!local || current.references.getOrDefault(actual, 0) > 1)) {
      throw error(current, path, actual, "此映射由锚点或合并键共享, 无法安全补写, 请先将该配置段展开为独立映射");
    }
  }

  private static Map<String, Entry> entries(Source source, MappingNode mapping, String path, Map<Node, Boolean> visiting) {
    if (visiting.put(mapping, Boolean.TRUE) != null) {
      throw error(source, path, mapping, "合并键存在循环引用, 无法安全补写");
    }
    Map<String, Entry> result = new LinkedHashMap<>();
    for (NodeTuple tuple : mapping.getValue()) {
      if (tuple.getKeyNode().getTag().equals(Tag.MERGE)) {
        Node value = tuple.getValueNode();
        if (value instanceof MappingNode merged) {
          inherit(source, merged, path, visiting, result);
        } else if (value instanceof SequenceNode sequence) {
          for (Node item : sequence.getValue()) {
            if (!(item instanceof MappingNode merged)) {
              throw error(source, path, item, "合并键必须引用映射");
            }
            inherit(source, merged, path, visiting, result);
          }
        } else {
          throw error(source, path, value, "合并键必须引用映射或映射列表");
        }
      }
    }
    for (NodeTuple tuple : mapping.getValue()) {
      if (!tuple.getKeyNode().getTag().equals(Tag.MERGE)) {
        result.put(key(source, tuple.getKeyNode(), path), new Entry(tuple.getValueNode(), true));
      }
    }
    visiting.remove(mapping);
    return result;
  }

  private static void inherit(Source source, MappingNode mapping, String path, Map<Node, Boolean> visiting,
      Map<String, Entry> result) {
    for (Map.Entry<String, Entry> entry : entries(source, mapping, path, visiting).entrySet()) {
      result.putIfAbsent(entry.getKey(), new Entry(entry.getValue().node(), false));
    }
  }

  private static String key(Source source, Node node, String path) {
    if (!(node instanceof ScalarNode scalar) || !node.getTag().equals(Tag.STR)) {
      throw error(source, path, node, "配置键必须是字符串");
    }
    return scalar.getValue();
  }

  private static Insertion blockInsertion(Source current, Source defaults, MappingNode actual,
      List<NodeTuple> missing, String path) {
    if (actual.getValue().isEmpty()) {
      throw error(current, path, actual, "无法定位空映射的插入位置");
    }
    Node first = actual.getValue().get(0).getKeyNode();
    int keyLine = first.getStartMark().getLine();
    int indent = first.getStartMark().getColumn();
    int keyOffset = current.offset(first.getStartMark());
    int lineStart = current.lines.get(keyLine).start();
    if (lineStart == 0 && current.text.startsWith("\uFEFF")) {
      lineStart++;
    }
    if (!current.text.substring(lineStart, keyOffset).isBlank()) {
      throw error(current, path, first, "此紧凑映射无法安全插入字段, 请将键值移到独立行");
    }
    int start = leadingStart(current, keyLine, indent);
    if (start == 0 && current.text.startsWith("\uFEFF")) {
      start++;
    }
    StringBuilder added = new StringBuilder();
    for (NodeTuple tuple : missing) {
      added.append(blockSnippet(defaults, tuple, indent, current.newline));
    }
    return new Insertion(start, added.toString());
  }

  private static Insertion flowInsertion(Source current, Source defaults, MappingNode actual,
      List<NodeTuple> missing, String path) {
    int start = current.offset(actual.getStartMark());
    int end = current.offset(actual.getEndMark());
    int brace = current.text.indexOf('{', start);
    if (brace < start || brace >= end || end == 0 || current.text.charAt(end - 1) != '}') {
      throw error(current, path, actual, "无法定位行内映射边界, 未补写默认字段");
    }
    String indent = " ".repeat(actual.getStartMark().getColumn() + 2);
    StringBuilder added = new StringBuilder(current.newline);
    for (int i = 0; i < missing.size(); i++) {
      NodeTuple tuple = missing.get(i);
      Span span = defaultSpan(defaults, tuple);
      for (Comment comment : defaults.comments) {
        if (comment.offset() >= span.start() && comment.offset() < span.end()) {
          added.append(indent).append(comment.text()).append(current.newline);
        }
      }
      added.append(indent).append(flowEntry(defaults, tuple, path));
      if (i + 1 < missing.size() || !actual.getValue().isEmpty()) {
        added.append(',');
      }
      added.append(current.newline);
    }
    // 仅插入新内容, 原花括号及其内部文本不改写
    return new Insertion(brace + 1, added.toString());
  }

  private static String flowEntry(Source defaults, NodeTuple tuple, String path) {
    String snippet = blockSnippet(defaults, tuple, 0, "\n");
    LoaderOptions loading = new LoaderOptions();
    loading.setAllowDuplicateKeys(false);
    Object value = new Yaml(new SafeConstructor(loading)).load(snippet);
    DumperOptions dumping = new DumperOptions();
    dumping.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
    dumping.setPrettyFlow(false);
    dumping.setSplitLines(false);
    dumping.setWidth(Integer.MAX_VALUE);
    String rendered = new Yaml(dumping).dump(value).strip();
    if (!rendered.startsWith("{") || !rendered.endsWith("}")) {
      throw error(defaults, path, tuple.getKeyNode(), "默认字段无法转换为行内映射条目");
    }
    return rendered.substring(1, rendered.length() - 1);
  }

  private static Span defaultSpan(Source defaults, NodeTuple tuple) {
    int keyLine = tuple.getKeyNode().getStartMark().getLine();
    int indent = tuple.getKeyNode().getStartMark().getColumn();
    int start = leadingStart(defaults, keyLine, indent);
    int end = contentEnd(defaults, tuple.getValueNode(), new IdentityHashMap<>());
    end = Math.max(end, defaults.offset(tuple.getKeyNode().getEndMark()));
    int lastLine = defaults.lineAt(Math.max(start, end - 1));
    return new Span(start, defaults.lines.get(lastLine).end(), indent);
  }

  private static int contentEnd(Source source, Node node, Map<Node, Boolean> visited) {
    if (visited.put(node, Boolean.TRUE) != null) {
      return source.offset(node.getStartMark());
    }
    if (node instanceof MappingNode mapping && mapping.getFlowStyle() == DumperOptions.FlowStyle.BLOCK) {
      int end = source.offset(node.getStartMark());
      for (NodeTuple tuple : mapping.getValue()) {
        end = Math.max(end, source.offset(tuple.getKeyNode().getEndMark()));
        end = Math.max(end, contentEnd(source, tuple.getValueNode(), visited));
      }
      return end;
    }
    if (node instanceof SequenceNode sequence && sequence.getFlowStyle() == DumperOptions.FlowStyle.BLOCK) {
      int end = source.offset(node.getStartMark());
      for (Node child : sequence.getValue()) {
        end = Math.max(end, contentEnd(source, child, visited));
      }
      return end;
    }
    return source.offset(node.getEndMark());
  }

  private static String blockSnippet(Source defaults, NodeTuple tuple, int indent, String newline) {
    Span span = defaultSpan(defaults, tuple);
    String source = defaults.text.substring(span.start(), span.end());
    StringBuilder result = new StringBuilder();
    String[] lines = source.split("\\r\\n|\\r|\\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (i == lines.length - 1 && line.isEmpty() && endsWithNewline(source)) {
        // 末尾空元素由统一追加的换行表示
        continue;
      }
      if (line.isEmpty()) {
        result.append(newline);
      } else {
        int spaces = leadingSpaces(line);
        if (spaces < span.indent() && !line.isBlank()) {
          throw error(defaults, "$", tuple.getKeyNode(), "默认字段缩进不完整, 无法安全复制");
        }
        // 块字符串中的空白行可能包含有效空格, 只移除所在键的基础缩进
        result.append(" ".repeat(indent)).append(line.substring(Math.min(spaces, span.indent()))).append(newline);
      }
    }
    return result.toString();
  }

  private static int leadingStart(Source source, int keyLine, int indent) {
    int line = keyLine;
    while (line > 0) {
      Line previous = source.lines.get(line - 1);
      String text = source.text.substring(previous.start(), previous.contentEnd());
      if (text.isBlank() || text.stripLeading().startsWith("#") && leadingSpaces(text) >= indent) {
        line--;
      } else {
        break;
      }
    }
    return source.lines.get(line).start();
  }

  private static int leadingSpaces(String value) {
    int spaces = 0;
    while (spaces < value.length() && value.charAt(spaces) == ' ') {
      spaces++;
    }
    return spaces;
  }

  private static String fillEmpty(Source current, Source defaults, MappingNode expected) {
    if (expected.getValue().isEmpty()) {
      return current.text;
    }
    StringBuilder added = new StringBuilder();
    for (NodeTuple tuple : expected.getValue()) {
      added.append(blockSnippet(defaults, tuple, 0, current.newline));
    }
    int offset = current.documentEnd < 0 ? current.text.length() : current.documentEnd;
    if (offset > 0 && current.text.charAt(offset - 1) != '\n' && current.text.charAt(offset - 1) != '\r') {
      added.insert(0, current.newline);
    }
    if (offset == current.text.length() && !endsWithNewline(current.text)) {
      while (!added.isEmpty() && (added.charAt(added.length() - 1) == '\r' || added.charAt(added.length() - 1) == '\n')) {
        added.setLength(added.length() - 1);
      }
    }
    return current.text.substring(0, offset) + added + current.text.substring(offset);
  }

  private static boolean endsWithNewline(String value) {
    return value.endsWith("\n") || value.endsWith("\r");
  }

  private static ConfigurationException error(Source source, String path, Node node, String reason) {
    int line = node == null ? 1 : node.getStartMark().getLine() + 1;
    return new ConfigurationException(source.file + ", 字段 " + (path.isEmpty() ? "$" : path) + ", 第 " + line + " 行: " + reason);
  }

  private record Entry(Node node, boolean local) {
  }

  private record Insertion(int offset, String text) {
  }

  private record Span(int start, int end, int indent) {
  }

  private record Line(int start, int contentEnd, int end) {
  }

  private record Comment(int offset, String text) {
  }

  private static final class Source {

    private final String file;
    private final String text;
    private final Node root;
    private final String newline;
    private final int[] offsets;
    private final List<Line> lines = new ArrayList<>();
    private final List<Comment> comments = new ArrayList<>();
    private final Map<Node, Integer> references = new IdentityHashMap<>();
    private int documentEnd = -1;

    private Source(String file, String text, Node root) {
      this.file = file;
      this.text = text;
      this.root = root;
      this.offsets = new int[text.codePointCount(0, text.length()) + 1];
      int offset = 0;
      for (int i = 0; i < this.offsets.length; i++) {
        this.offsets[i] = offset;
        if (offset < text.length()) {
          offset += Character.charCount(text.codePointAt(offset));
        }
      }
      Matcher endings = Pattern.compile("\\r\\n|\\r|\\n").matcher(text);
      String firstEnding = null;
      int start = 0;
      while (endings.find()) {
        this.lines.add(new Line(start, endings.start(), endings.end()));
        start = endings.end();
        if (firstEnding == null) {
          firstEnding = endings.group();
        }
      }
      this.lines.add(new Line(start, text.length(), text.length()));
      this.newline = firstEnding == null ? "\n" : firstEnding;
    }

    private int offset(Mark mark) {
      int index = mark.getIndex();
      if (index < 0 || index >= this.offsets.length) {
        throw error(this, "$", this.root, "源位置超出文本范围, 无法安全补写");
      }
      return this.offsets[index];
    }

    private int lineAt(int offset) {
      for (int i = this.lines.size() - 1; i >= 0; i--) {
        if (offset >= this.lines.get(i).start()) {
          return i;
        }
      }
      return 0;
    }
  }
}

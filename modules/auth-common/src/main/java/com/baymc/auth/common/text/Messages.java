package com.baymc.auth.common.text;

import com.baymc.auth.common.config.YamlDocument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/*
 * 语言文件访问器
 *
 * <p>所有玩家可见文本从语言 YAML 读取
 * Chat 和帮助类消息支持 list, 单行通道读取时自动取第一行
 */
public final class Messages {
    private final Map<String, Object> values;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Messages(Map<String, Object> values) {
        this.values = values;
    }

    public static Messages load(Path path, String defaultText, Consumer<String> warnings) throws IOException {
        return new Messages(YamlDocument.load(path, defaultText, warnings).values());
    }

    public static Messages fromString(String text) {
        return new Messages(YamlDocument.fromString(text).values());
    }

    public String plain(String path) {
        List<String> lines = lines(path);
        return lines.isEmpty() ? path : lines.getFirst();
    }

    public List<String> lines(String path) {
        Object value = get(path);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        if (value != null) {
            return List.of(String.valueOf(value));
        }
        return List.of("<red>Missing language key: " + path + "</red>");
    }

    public Component component(String path, Map<String, String> placeholders) {
        return miniMessage.deserialize(replace(plain(path), placeholders));
    }

    public List<Component> components(String path, Map<String, String> placeholders) {
        return lines(path).stream().map(line -> miniMessage.deserialize(replace(line, placeholders))).toList();
    }

    public String replace(String text, Map<String, String> placeholders) {
        String result = text;
        String prefix = String.valueOf(get("common.prefix"));
        result = result.replace("<prefix>", prefix == null ? "" : prefix);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object get(String path) {
        Object current = values;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }
}

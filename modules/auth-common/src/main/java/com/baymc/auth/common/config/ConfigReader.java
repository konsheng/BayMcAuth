package com.baymc.auth.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/*
 * 类型安全配置读取器
 *
 * <p>配置文件类型错误时使用默认值并输出中文警告
 * 读取路径使用层级键, 不支持扁平点分配置键
 */
public final class ConfigReader {
    private final Map<String, Object> root;
    private final Map<String, Object> defaults;
    private final Consumer<String> warnings;

    public ConfigReader(YamlDocument document, Consumer<String> warnings) {
        this(document.values(), document.defaults(), warnings);
    }

    private ConfigReader(Map<String, Object> root, Map<String, Object> defaults, Consumer<String> warnings) {
        this.root = root;
        this.defaults = defaults;
        this.warnings = warnings == null ? ignored -> { } : warnings;
    }

    public String string(String path) {
        Object value = get(root, path);
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        String defaultValue = defaultString(path);
        warn(path, "字符串", defaultValue);
        return defaultValue;
    }

    public int integer(String path) {
        Object value = get(root, path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        int defaultValue = defaultInteger(path);
        warn(path, "数字", defaultValue);
        return defaultValue;
    }

    public long longValue(String path) {
        Object value = get(root, path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        long defaultValue = defaultLong(path);
        warn(path, "数字", defaultValue);
        return defaultValue;
    }

    public boolean bool(String path) {
        Object value = get(root, path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String normalized = text.toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "false".equals(normalized)) {
                return Boolean.parseBoolean(normalized);
            }
        }
        boolean defaultValue = defaultBoolean(path);
        warn(path, "布尔值", defaultValue);
        return defaultValue;
    }

    public List<String> stringList(String path) {
        Object value = get(root, path);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        List<String> defaultValue = defaultStringList(path);
        warn(path, "列表", defaultValue);
        return defaultValue;
    }

    public List<Integer> intList(String path) {
        Object value = get(root, path);
        if (value instanceof List<?> list) {
            return integerList(list);
        }
        List<Integer> defaultValue = defaultIntegerList(path);
        warn(path, "数字列表", defaultValue);
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> mapList(String path) {
        Object value = get(root, path);
        if (value instanceof List<?> list) {
            return mapList(list);
        }
        List<Map<String, Object>> defaultValue = defaultMapList(path);
        warn(path, "映射列表", defaultValue);
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Object get(Map<String, Object> source, String path) {
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }

    private String defaultString(String path) {
        Object value = requiredDefault(path, "字符串");
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw invalidDefault(path, "字符串");
    }

    private int defaultInteger(String path) {
        Object value = requiredDefault(path, "数字");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        throw invalidDefault(path, "数字");
    }

    private long defaultLong(String path) {
        Object value = requiredDefault(path, "数字");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        throw invalidDefault(path, "数字");
    }

    private boolean defaultBoolean(String path) {
        Object value = requiredDefault(path, "布尔值");
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String normalized = text.toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "false".equals(normalized)) {
                return Boolean.parseBoolean(normalized);
            }
        }
        throw invalidDefault(path, "布尔值");
    }

    private List<String> defaultStringList(String path) {
        Object value = requiredDefault(path, "列表");
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        throw invalidDefault(path, "列表");
    }

    private List<Integer> defaultIntegerList(String path) {
        Object value = requiredDefault(path, "数字列表");
        if (value instanceof List<?> list) {
            return integerList(list);
        }
        throw invalidDefault(path, "数字列表");
    }

    private List<Integer> integerList(List<?> list) {
        List<Integer> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                result.add(number.intValue());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> defaultMapList(String path) {
        Object value = requiredDefault(path, "映射列表");
        if (value instanceof List<?> list) {
            return mapList(list);
        }
        throw invalidDefault(path, "映射列表");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private Object requiredDefault(String path, String expected) {
        Object value = get(defaults, path);
        if (value == null) {
            throw new IllegalStateException("默认配置键 " + path + " 缺失, 需要 " + expected);
        }
        return value;
    }

    private IllegalStateException invalidDefault(String path, String expected) {
        return new IllegalStateException("默认配置键 " + path + " 类型错误, 需要 " + expected);
    }

    private void warn(String path, String expected, Object defaultValue) {
        warnings.accept("配置键 " + path + " 类型错误, 需要 " + expected + ", 已使用默认值 " + defaultValue);
    }
}

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
    private final Consumer<String> warnings;

    public ConfigReader(Map<String, Object> root, Consumer<String> warnings) {
        this.root = root;
        this.warnings = warnings == null ? ignored -> { } : warnings;
    }

    public String string(String path, String defaultValue) {
        Object value = get(path);
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        warn(path, "字符串", defaultValue);
        return defaultValue;
    }

    public int integer(String path, int defaultValue) {
        Object value = get(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                warn(path, "数字", defaultValue);
            }
        } else {
            warn(path, "数字", defaultValue);
        }
        return defaultValue;
    }

    public long longValue(String path, long defaultValue) {
        Object value = get(path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                warn(path, "数字", defaultValue);
            }
        } else {
            warn(path, "数字", defaultValue);
        }
        return defaultValue;
    }

    public boolean bool(String path, boolean defaultValue) {
        Object value = get(path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String normalized = text.toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "false".equals(normalized)) {
                return Boolean.parseBoolean(normalized);
            }
        }
        warn(path, "布尔值", defaultValue);
        return defaultValue;
    }

    public List<String> stringList(String path) {
        Object value = get(path);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        if (value == null) {
            return List.of();
        }
        warn(path, "列表", "[]");
        return List.of();
    }

    public List<Integer> intList(String path) {
        Object value = get(path);
        if (value instanceof List<?> list) {
            List<Integer> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number number) {
                    result.add(number.intValue());
                }
            }
            return result;
        }
        warn(path, "数字列表", "[]");
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> mapList(String path) {
        Object value = get(path);
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Object get(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }

    private void warn(String path, String expected, Object defaultValue) {
        warnings.accept("配置键 " + path + " 类型错误, 需要 " + expected + ", 已使用默认值 " + defaultValue);
    }
}

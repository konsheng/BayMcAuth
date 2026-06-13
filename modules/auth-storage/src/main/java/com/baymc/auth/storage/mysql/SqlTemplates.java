package com.baymc.auth.storage.mysql;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SqlTemplates {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private SqlTemplates() {
    }

    static String render(String name, Map<String, String> placeholders) {
        String sql = CACHE.computeIfAbsent(name, SqlTemplates::load);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            sql = sql.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return sql;
    }

    private static String load(String name) {
        String resource = "/mysql/" + name + ".sql";
        try (var stream = SqlTemplates.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing SQL template: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read SQL template: " + resource, exception);
        }
    }
}

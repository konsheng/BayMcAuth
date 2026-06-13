package com.baymc.auth.storage.mysql;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MySqlStorageTest {
    @Test
    void hikariConfigSetsMysqlDriverClassName() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-storage/src/main/java/com/baymc/auth/storage/mysql/MySqlStorage.java");
        String text = Files.readString(source);

        assertTrue(text.contains("config.setDriverClassName(\"com.mysql.cj.jdbc.Driver\")"));
    }

    @Test
    void invalidTablePrefixDoesNotUseHardcodedFallback() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-storage/src/main/java/com/baymc/auth/storage/mysql/MySqlStorage.java");
        String text = Files.readString(source);

        assertTrue(text.contains("throw new IllegalArgumentException(\"Invalid database.table-prefix: \" + value)"));
    }

    private static Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Project root not found");
    }
}

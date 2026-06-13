package com.baymc.auth.storage.mysql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void sqlTemplatesReplacePlaceholders() {
        String sql = SqlTemplates.render("user/find-by-uuid", Map.of("table", "baymcauth_users"));

        assertEquals("SELECT * FROM baymcauth_users WHERE uuid = ?", sql.strip());
    }

    @Test
    void sqlTemplatesFailWhenMissing() {
        assertThrows(IllegalArgumentException.class, () -> SqlTemplates.render("missing-template", Map.of()));
    }

    @Test
    void mysqlStorageDoesNotHardcodeSqlStatements() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-storage/src/main/java/com/baymc/auth/storage/mysql/MySqlStorage.java");
        String text = Files.readString(source);

        assertFalse(text.contains("CREATE TABLE"));
        assertFalse(text.contains("INSERT INTO"));
        assertFalse(text.contains("SELECT * FROM"));
        assertFalse(text.contains("UPDATE "));
        assertFalse(text.contains("DELETE FROM"));
        assertFalse(text.contains("ALTER TABLE"));
        assertFalse(text.contains("information_schema"));
    }

    @Test
    void sqlResourcesContainRequiredTemplates() throws Exception {
        Path root = findProjectRoot().resolve("modules/auth-storage/src/main/resources/mysql");
        assertSqlResource(root, "migration/create-users.sql", "CREATE TABLE IF NOT EXISTS {users}");
        assertSqlResource(root, "migration/create-invite-codes.sql", "CREATE TABLE IF NOT EXISTS {invite_codes}");
        assertSqlResource(root, "migration/create-name-locks.sql", "CREATE TABLE IF NOT EXISTS {name_locks}");
        assertSqlResource(root, "migration/create-failures.sql", "CREATE TABLE IF NOT EXISTS {failures}");
        assertSqlResource(root, "migration/create-audit-logs.sql", "CREATE TABLE IF NOT EXISTS {audit_logs}");
        assertSqlResource(root, "migration/create-identity-contexts.sql", "CREATE TABLE IF NOT EXISTS {identity_contexts}");
        assertSqlResource(root, "user/save.sql", "INSERT INTO {table}");
        assertSqlResource(root, "identity-context/save.sql", "ON DUPLICATE KEY UPDATE");

        try (Stream<Path> files = Files.walk(root)) {
            assertTrue(files.anyMatch(path -> path.toString().endsWith(".sql")));
        }
    }

    private static void assertSqlResource(Path root, String relative, String expected) throws IOException {
        Path file = root.resolve(relative);
        assertTrue(Files.exists(file), relative);
        assertTrue(Files.readString(file).contains(expected), relative);
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

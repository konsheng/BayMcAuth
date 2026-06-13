package com.baymc.auth.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * 配置读取用法回归测试
 *
 * <p>防止重新引入嵌套回退读取或硬编码默认值
 */
final class ConfigReaderUsageTest {
    private static final Pattern READER_CALL = Pattern.compile("reader\\.[A-Za-z][A-Za-z0-9_]*\\s*\\(");
    private static final Pattern DEFAULTABLE_READER_CALL = Pattern.compile("reader\\.(?:string|integer|longValue|bool)\\s*\\(");

    @Test
    void configReaderCallsDoNotNestOtherReaderCalls() throws Exception {
        Path root = findProjectRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root.resolve("modules"))) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(ConfigReaderUsageTest::isMainSourceFile)
                .forEach(path -> collectNestedReaderCalls(root, path, violations));
        }

        assertTrue(violations.isEmpty(), () -> "Nested ConfigReader calls are not allowed:\n" + String.join("\n", violations));
    }

    @Test
    void authConfigReaderCallsDoNotProvideHardcodedDefaults() throws Exception {
        Path root = findProjectRoot();
        Path source = root.resolve("modules/auth-common/src/main/java/com/baymc/auth/common/config/AuthConfig.java");
        String text = Files.readString(source);
        Matcher matcher = DEFAULTABLE_READER_CALL.matcher(text);
        List<String> violations = new ArrayList<>();
        while (matcher.find()) {
            int end = findCallEnd(text, matcher.end() - 1);
            if (end > matcher.end() && text.substring(matcher.end(), end).contains(",")) {
                violations.add(root.relativize(source) + ":" + lineOf(text, matcher.start()));
            }
        }

        assertTrue(violations.isEmpty(), () -> "Config defaults must come from config.yml:\n" + String.join("\n", violations));
    }

    private static boolean isMainSourceFile(Path path) {
        return path.toString().replace('\\', '/').contains("/src/main/java/");
    }

    private static void collectNestedReaderCalls(Path root, Path path, List<String> violations) {
        String source;
        try {
            source = Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }

        Matcher matcher = READER_CALL.matcher(source);
        while (matcher.find()) {
            int end = findCallEnd(source, matcher.end() - 1);
            if (end <= matcher.end()) {
                continue;
            }
            String arguments = source.substring(matcher.end(), end);
            if (READER_CALL.matcher(arguments).find()) {
                violations.add(root.relativize(path) + ":" + lineOf(source, matcher.start()));
            }
        }
    }

    private static int findCallEnd(String source, int openParenthesis) {
        int depth = 0;
        for (int index = openParenthesis; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int lineOf(String source, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (source.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
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

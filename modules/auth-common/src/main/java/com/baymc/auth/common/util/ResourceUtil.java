package com.baymc.auth.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ResourceUtil {
    private ResourceUtil() {
    }

    public static String readText(Class<?> owner, String path) {
        try (InputStream input = owner.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("缺失资源文件: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取资源文件失败: " + path, exception);
        }
    }
}

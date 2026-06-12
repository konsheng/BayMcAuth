package com.baymc.auth.common.blacklist;

import com.baymc.auth.common.config.AuthConfig;
import com.baymc.auth.common.util.ByteSizeParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class BlacklistLoader {
    private BlacklistLoader() {
    }

    public static List<String> load(AuthConfig.BlacklistGroup config, Path cacheFile, Consumer<String> warnings) {
        List<String> values = new ArrayList<>(config.localExtra());
        if (!config.enabled()) {
            return values;
        }
        boolean remoteOk = false;
        for (String url : config.remoteUrls()) {
            try {
                values.addAll(readRemote(url, config));
                remoteOk = true;
            } catch (RuntimeException exception) {
                if (warnings != null) {
                    warnings.accept("远程黑名单加载失败: " + url + ", " + exception.getMessage());
                }
            }
        }
        if (remoteOk) {
            try {
                Files.createDirectories(cacheFile.getParent());
                Files.writeString(cacheFile, String.join("\n", values), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                if (warnings != null) {
                    warnings.accept("黑名单缓存写入失败: " + exception.getMessage());
                }
            }
        } else if (Files.exists(cacheFile)) {
            try {
                values.addAll(Files.readAllLines(cacheFile, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                if (warnings != null) {
                    warnings.accept("本地黑名单缓存读取失败: " + exception.getMessage());
                }
            }
        }
        return values.stream().map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
    }

    private static List<String> readRemote(String url, AuthConfig.BlacklistGroup config) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(Math.toIntExact(config.connectTimeout().toMillis()));
            connection.setReadTimeout(Math.toIntExact(config.readTimeout().toMillis()));
            connection.setRequestMethod("GET");
            long maxBytes = ByteSizeParser.parse(config.maxFileSize());
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(Math.toIntExact(maxBytes + 1));
                if (bytes.length > maxBytes) {
                    throw new IllegalStateException("远程黑名单超过大小限制");
                }
                String text = new String(bytes, StandardCharsets.UTF_8);
                return text.lines().map(String::trim).filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }
}

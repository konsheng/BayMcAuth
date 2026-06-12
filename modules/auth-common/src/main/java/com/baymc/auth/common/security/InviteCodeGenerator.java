package com.baymc.auth.common.security;

import com.baymc.auth.common.config.AuthConfig;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InviteCodeGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodeGenerator() {
    }

    public static String generate(AuthConfig.InviteFormat format) {
        List<String> parts = new ArrayList<>();
        if (format.prefix() != null && !format.prefix().isBlank()) {
            parts.add(format.prefix());
        }
        for (Integer size : format.groups()) {
            parts.add(randomPart(format.charset(), size));
        }
        String code = String.join(format.separator(), parts);
        return format.uppercase() ? code.toUpperCase(Locale.ROOT) : code;
    }

    public static String key(String code) {
        return code == null ? "" : code.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String randomPart(String charset, int size) {
        StringBuilder builder = new StringBuilder(size);
        for (int index = 0; index < size; index++) {
            builder.append(charset.charAt(RANDOM.nextInt(charset.length())));
        }
        return builder.toString();
    }
}

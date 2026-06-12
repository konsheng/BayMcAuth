package com.baymc.auth.common.util;

import java.util.Locale;

public final class ByteSizeParser {
    private ByteSizeParser() {
    }

    public static long parse(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        String value = text.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1L;
        if (value.endsWith("kb")) {
            multiplier = 1024L;
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("mb")) {
            multiplier = 1024L * 1024L;
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("gb")) {
            multiplier = 1024L * 1024L * 1024L;
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("b")) {
            value = value.substring(0, value.length() - 1);
        }
        return Long.parseLong(value.trim()) * multiplier;
    }
}

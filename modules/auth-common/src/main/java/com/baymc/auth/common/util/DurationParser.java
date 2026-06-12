package com.baymc.auth.common.util;

import java.time.Duration;
import java.util.Locale;

public final class DurationParser {
    private DurationParser() {
    }

    public static Duration parse(String text) {
        if (text == null || text.isBlank()) {
            return Duration.ZERO;
        }
        String value = text.trim().toLowerCase(Locale.ROOT);
        long multiplier;
        if (value.endsWith("ms")) {
            multiplier = 1L;
            value = value.substring(0, value.length() - 2);
            return Duration.ofMillis(Long.parseLong(value));
        } else if (value.endsWith("s")) {
            multiplier = 1L;
        } else if (value.endsWith("m")) {
            multiplier = 60L;
        } else if (value.endsWith("h")) {
            multiplier = 3600L;
        } else if (value.endsWith("d")) {
            multiplier = 86400L;
        } else {
            multiplier = 1L;
            return Duration.ofSeconds(Long.parseLong(value));
        }
        long number = Long.parseLong(value.substring(0, value.length() - 1));
        return Duration.ofSeconds(number * multiplier);
    }
}

package com.baymc.auth.common.util;

import java.util.ArrayList;
import java.util.List;

public final class ExceptionSummary {
    private ExceptionSummary() {
    }

    public static List<String> databaseFailureLines(Throwable throwable) {
        if (throwable == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        Throwable current = throwable;
        while (current != null) {
            lines.add("- " + current.getClass().getName() + ": " + normalizedMessage(current));
            current = current.getCause();
        }
        return List.copyOf(lines);
    }

    private static String normalizedMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            return "";
        }
        return message.replaceAll("\\s+", " ").trim();
    }
}

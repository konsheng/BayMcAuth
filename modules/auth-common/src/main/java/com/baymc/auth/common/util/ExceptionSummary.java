package com.baymc.auth.common.util;

import java.util.ArrayList;
import java.util.List;

/*
 * 异常摘要工具
 *
 * <p>为非 debug 日志生成数据库失败原因列表, 避免默认输出完整堆栈
 */
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

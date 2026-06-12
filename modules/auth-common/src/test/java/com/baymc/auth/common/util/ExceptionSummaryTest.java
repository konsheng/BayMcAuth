package com.baymc.auth.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExceptionSummaryTest {
    @Test
    void formatsOuterExceptionAndCauses() {
        IllegalStateException cause = new IllegalStateException("root reason");
        RuntimeException exception = new RuntimeException("outer", cause);

        assertEquals(List.of(
            "- java.lang.RuntimeException: outer",
            "- java.lang.IllegalStateException: root reason"
        ), ExceptionSummary.databaseFailureLines(exception));
    }

    @Test
    void formatsEmptyMessageAsEmptyText() {
        IllegalArgumentException exception = new IllegalArgumentException();

        assertEquals(List.of("- java.lang.IllegalArgumentException: "), ExceptionSummary.databaseFailureLines(exception));
    }

    @Test
    void collapsesMultilineMessagesToSingleLine() {
        RuntimeException exception = new RuntimeException("first line\r\n\r\nsecond line");

        assertEquals(List.of("- java.lang.RuntimeException: first line second line"), ExceptionSummary.databaseFailureLines(exception));
    }

    @Test
    void formatsAllCausesWithoutLimit() {
        RuntimeException cause3 = new RuntimeException("third");
        RuntimeException cause2 = new RuntimeException("second", cause3);
        RuntimeException cause1 = new RuntimeException("first", cause2);
        RuntimeException exception = new RuntimeException(
            "Failed to initialize pool: Communications link failure",
            cause1
        );

        assertEquals(List.of(
            "- java.lang.RuntimeException: Failed to initialize pool: Communications link failure",
            "- java.lang.RuntimeException: first",
            "- java.lang.RuntimeException: second",
            "- java.lang.RuntimeException: third"
        ), ExceptionSummary.databaseFailureLines(exception));
    }

    @Test
    void returnsEmptyListForNullThrowable() {
        assertEquals(List.of(), ExceptionSummary.databaseFailureLines(null));
    }
}

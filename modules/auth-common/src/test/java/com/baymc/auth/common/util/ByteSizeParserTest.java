package com.baymc.auth.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ByteSizeParserTest {
    @Test
    void parsesByteSizes() {
        assertEquals(512L, ByteSizeParser.parse("512b"));
        assertEquals(1024L, ByteSizeParser.parse("1kb"));
        assertEquals(1024L * 1024L, ByteSizeParser.parse("1mb"));
    }
}

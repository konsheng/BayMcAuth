package com.baymc.auth.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * 字节大小解析测试
 *
 * <p>验证无单位和 b, kb, mb, gb 单位的配置字符串解析
 */
final class ByteSizeParserTest {
    @Test
    void parsesByteSizes() {
        assertEquals(512L, ByteSizeParser.parse("512b"));
        assertEquals(1024L, ByteSizeParser.parse("1kb"));
        assertEquals(1024L * 1024L, ByteSizeParser.parse("1mb"));
    }
}

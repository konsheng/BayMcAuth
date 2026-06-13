package com.baymc.auth.common.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * 时间长度解析测试
 *
 * <p>验证毫秒, 秒, 分钟, 小时和天等配置单位解析
 */
final class DurationParserTest {
    @Test
    void parsesSupportedUnits() {
        assertEquals(Duration.ofMillis(500), DurationParser.parse("500ms"));
        assertEquals(Duration.ofSeconds(10), DurationParser.parse("10s"));
        assertEquals(Duration.ofMinutes(2), DurationParser.parse("2m"));
        assertEquals(Duration.ofHours(3), DurationParser.parse("3h"));
        assertEquals(Duration.ofDays(4), DurationParser.parse("4d"));
    }
}

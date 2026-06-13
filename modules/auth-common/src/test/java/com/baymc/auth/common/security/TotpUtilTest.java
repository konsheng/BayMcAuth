package com.baymc.auth.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * TOTP 工具测试
 *
 * <p>覆盖密钥生成和时间窗口内外验证码校验行为
 */
final class TotpUtilTest {
    @Test
    void verifiesCodeInsideWindow() {
        String secret = TotpUtil.generateSecret();
        long step = Instant.now().getEpochSecond() / 30L;
        String code = TotpUtil.code(secret, step, 6);

        assertTrue(TotpUtil.verify(secret, code, 6, Duration.ofSeconds(30), 1));
        assertFalse(TotpUtil.verify(secret, "000000".equals(code) ? "111111" : "000000", 6, Duration.ofSeconds(30), 0));
    }
}

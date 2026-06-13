package com.baymc.auth.common.security;

import com.baymc.auth.common.config.AuthConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * 邀请码生成测试
 *
 * <p>验证邀请码格式, 分组, 前缀和查询键规范化
 */
final class InviteCodeGeneratorTest {
    @Test
    void generatesConfiguredFormat() {
        AuthConfig.InviteFormat format = new AuthConfig.InviteFormat("BAYMC", List.of(4, 4), "-", "ABCDEFGHJKLMNPQRSTUVWXYZ23456789", true);
        String code = InviteCodeGenerator.generate(format);

        assertTrue(code.matches("BAYMC-[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertEquals(code, InviteCodeGenerator.key(code.toLowerCase()));
    }
}

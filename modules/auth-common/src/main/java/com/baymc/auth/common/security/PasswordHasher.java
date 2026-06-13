package com.baymc.auth.common.security;

import org.mindrot.jbcrypt.BCrypt;

/*
 * 密码哈希工具
 *
 * <p>封装 BCrypt 加密和校验, 避免业务层直接依赖具体哈希实现
 */
public final class PasswordHasher {
    private PasswordHasher() {
    }

    public static String hash(String password, int cost) {
        return BCrypt.hashpw(password, BCrypt.gensalt(cost));
    }

    public static boolean verify(String password, String cipher) {
        return cipher != null && BCrypt.checkpw(password, cipher);
    }
}

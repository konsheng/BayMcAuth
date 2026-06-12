package com.baymc.auth.common.security;

import org.mindrot.jbcrypt.BCrypt;

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

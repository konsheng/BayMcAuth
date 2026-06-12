package com.baymc.auth.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/*
 * TOTP 手动密钥工具
 *
 * <p>只生成和验证 Base32 secret, 不生成二维码
 * 验证流程不保存 code, step 或 hash, 避免审计输出敏感信息
 */
public final class TotpUtil {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public static boolean verify(String secret, String code, int digits, Duration period, int window) {
        if (secret == null || code == null || !code.matches("\\d+")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / period.toSeconds();
        for (int offset = -window; offset <= window; offset++) {
            String expected = code(secret, currentStep + offset, digits);
            if (constantTimeEquals(expected, code)) {
                return true;
            }
        }
        return false;
    }

    public static String code(String secret, long step, int digits) {
        try {
            byte[] key = decodeBase32(secret);
            byte[] counter = ByteBuffer.allocate(8).putLong(step).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
            int modulo = (int) Math.pow(10, digits);
            return String.format(Locale.ROOT, "%0" + digits + "d", binary % modulo);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP 计算失败", exception);
        }
    }

    private static String encodeBase32(byte[] bytes) {
        StringBuilder output = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte item : bytes) {
            buffer = (buffer << 8) | (item & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                output.append(BASE32[(buffer >> (bitsLeft - 5)) & 31]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            output.append(BASE32[(buffer << (5 - bitsLeft)) & 31]);
        }
        return output.toString();
    }

    private static byte[] decodeBase32(String secret) {
        String value = secret.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer output = ByteBuffer.allocate(value.length() * 5 / 8 + 8);
        int buffer = 0;
        int bitsLeft = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            int decoded = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(current);
            if (decoded < 0) {
                throw new IllegalArgumentException("无效 TOTP secret");
            }
            buffer = (buffer << 5) | decoded;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.put((byte) ((buffer >> (bitsLeft - 8)) & 0xff));
                bitsLeft -= 8;
            }
        }
        byte[] result = new byte[output.position()];
        output.flip();
        output.get(result);
        return result;
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int diff = 0;
        for (int index = 0; index < left.length(); index++) {
            diff |= left.charAt(index) ^ right.charAt(index);
        }
        return diff == 0;
    }
}

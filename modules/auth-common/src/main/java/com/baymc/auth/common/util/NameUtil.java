package com.baymc.auth.common.util;

import java.util.Locale;

/*
 * 玩家名工具
 *
 * <p>提供玩家名规范化, Minecraft 名称校验和中文字符检测
 */
public final class NameUtil {
    private NameUtil() {
    }

    public static String lower(String playerName) {
        return playerName == null ? "" : playerName.toLowerCase(Locale.ROOT);
    }

    public static boolean containsChinese(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(index));
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    public static boolean validMinecraftName(String name) {
        return name != null && name.length() >= 3 && name.length() <= 32 && name.matches("[A-Za-z0-9_\\u4e00-\\u9fff]+");
    }
}

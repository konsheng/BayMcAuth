package com.baymc.auth.common.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/*
 * 离线 UUID 工具
 *
 * <p>按 Minecraft 离线模式规则从玩家名稳定生成 UUID
 */
public final class OfflineUuidUtil {
    private OfflineUuidUtil() {
    }

    public static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }
}

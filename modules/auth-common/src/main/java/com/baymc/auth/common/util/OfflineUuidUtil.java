package com.baymc.auth.common.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OfflineUuidUtil {
    private OfflineUuidUtil() {
    }

    public static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }
}

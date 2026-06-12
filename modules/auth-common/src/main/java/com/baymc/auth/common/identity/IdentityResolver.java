package com.baymc.auth.common.identity;

import com.baymc.auth.common.blacklist.BlacklistService;
import com.baymc.auth.common.config.AuthConfig;
import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.IdentityDecision;
import com.baymc.auth.common.util.NameUtil;
import com.baymc.auth.common.util.OfflineUuidUtil;

import java.util.Locale;

/*
 * Velocity 身份分流决策
 *
 * <p>顺序固定为 name-affix, name-plain, name-chinese, PREMIUM
 * 离线账号使用完整玩家名生成离线 UUID, PREMIUM 不强制 UUID, 交由代理正版链路处理
 */
public final class IdentityResolver {
    private final AuthConfig config;
    private final BlacklistService blacklistService;

    public IdentityResolver(AuthConfig config, BlacklistService blacklistService) {
        this.config = config;
        this.blacklistService = blacklistService;
    }

    public IdentityDecision resolve(String playerName, NameLockLookup nameLockLookup) {
        if (!NameUtil.validMinecraftName(playerName)) {
            return IdentityDecision.deny("玩家名格式无效");
        }
        if (blacklistService.usernameBlocked(playerName)) {
            return IdentityDecision.deny("玩家名命中黑名单");
        }
        if (config.accountTypes().nameAffix()) {
            AffixResult affix = matchAffix(playerName);
            if (affix.wrongCase()) {
                return IdentityDecision.deny("离线名前后缀大小写错误");
            }
            if (affix.matched()) {
                return IdentityDecision.allow(AccountType.OFFLINE_AFFIX, OfflineUuidUtil.offlineUuid(playerName), "玩家 " + playerName + " 命中 OFFLINE_AFFIX");
            }
        }
        if (config.accountTypes().namePlain() && nameLockLookup.findActiveByName(playerName).isPresent()) {
            return IdentityDecision.allow(AccountType.OFFLINE_PLAIN, OfflineUuidUtil.offlineUuid(playerName), "玩家 " + playerName + " 命中 OFFLINE_PLAIN");
        }
        if (config.accountTypes().nameChinese() && config.chineseName().allow() && NameUtil.containsChinese(playerName)) {
            return IdentityDecision.allow(AccountType.OFFLINE_CHINESE, OfflineUuidUtil.offlineUuid(playerName), "玩家 " + playerName + " 命中 OFFLINE_CHINESE");
        }
        return IdentityDecision.allow(AccountType.PREMIUM, null, "玩家 " + playerName + " 使用 PREMIUM");
    }

    private AffixResult matchAffix(String playerName) {
        String mode = config.offlineAffix().mode();
        boolean checkPrefix = "both".equalsIgnoreCase(mode) || "prefix-only".equalsIgnoreCase(mode);
        boolean checkSuffix = "both".equalsIgnoreCase(mode) || "suffix-only".equalsIgnoreCase(mode);
        boolean matched = false;
        boolean wrongCase = false;
        if (checkPrefix) {
            for (String prefix : config.offlineAffix().prefixes()) {
                Match match = matchPrefix(playerName, prefix);
                matched |= match.matched();
                wrongCase |= match.wrongCase();
            }
        }
        if (checkSuffix) {
            for (String suffix : config.offlineAffix().suffixes()) {
                Match match = matchSuffix(playerName, suffix);
                matched |= match.matched();
                wrongCase |= match.wrongCase();
            }
        }
        return new AffixResult(matched, wrongCase && !matched && config.offlineAffix().rejectWrongCase());
    }

    private Match matchPrefix(String playerName, String prefix) {
        if (prefix.isEmpty()) {
            return new Match(false, false);
        }
        if (config.offlineAffix().caseSensitive()) {
            return new Match(playerName.startsWith(prefix), playerName.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)) && !playerName.startsWith(prefix));
        }
        return new Match(playerName.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)), false);
    }

    private Match matchSuffix(String playerName, String suffix) {
        if (suffix.isEmpty()) {
            return new Match(false, false);
        }
        if (config.offlineAffix().caseSensitive()) {
            return new Match(playerName.endsWith(suffix), playerName.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT)) && !playerName.endsWith(suffix));
        }
        return new Match(playerName.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT)), false);
    }

    private record Match(boolean matched, boolean wrongCase) {
    }

    private record AffixResult(boolean matched, boolean wrongCase) {
    }
}

package com.baymc.auth.paper.service;

import com.baymc.auth.common.config.AuthConfig;
import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.InviteCode;
import com.baymc.auth.common.security.InviteCodeGenerator;
import com.baymc.auth.paper.RuntimeConfigRef;
import com.baymc.auth.storage.repository.InviteCodeRepository;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
 * 邀请码管理服务
 *
 * <p>负责创建, 查询, 撤销邀请码并写入操作者信息
 */
public final class InviteService {
    private final InviteCodeRepository invites;
    private final RuntimeConfigRef configRef;

    public InviteService(InviteCodeRepository invites, RuntimeConfigRef configRef) {
        this.invites = invites;
        this.configRef = configRef;
    }

    public List<InviteCode> create(CommandSender sender, int count, int days) {
        AuthConfig config = configRef.get();
        List<InviteCode> result = new ArrayList<>();
        String batchId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        for (int index = 0; index < count; index++) {
            String code;
            do {
                code = InviteCodeGenerator.generate(config.invite().format());
            } while (invites.findByCodeKey(InviteCodeGenerator.key(code)).isPresent());
            InviteCode invite = new InviteCode(0L, code, InviteCodeGenerator.key(code), batchId, false, null, null, null,
                null, null, null, sender.getName(), sender instanceof Player player ? player.getUniqueId() : null,
                now, now.plus(days, ChronoUnit.DAYS), false, null, null, null, null);
            result.add(invites.save(invite));
        }
        return result;
    }

    public List<InviteCode> list() {
        return invites.list(50);
    }

    public InviteCode revoke(String code, CommandSender sender) {
        InviteCode invite = invites.findByCodeKey(InviteCodeGenerator.key(code)).orElseThrow();
        InviteCode revoked = new InviteCode(invite.id(), invite.code(), invite.codeKey(), invite.batchId(), invite.used(), invite.usedByUuid(),
            invite.usedByName(), invite.usedByNameLower(), invite.usedAccountType() == null ? AccountType.OFFLINE_AFFIX : invite.usedAccountType(),
            invite.usedIp(), invite.usedAt(), invite.createdBy(), invite.createdByUuid(), invite.createdAt(), invite.expiresAt(), true,
            sender.getName(), sender instanceof Player player ? player.getUniqueId() : null, Instant.now(), invite.note());
        return invites.save(revoked);
    }

    public InviteCode info(String code) {
        return invites.findByCodeKey(InviteCodeGenerator.key(code)).orElseThrow();
    }
}

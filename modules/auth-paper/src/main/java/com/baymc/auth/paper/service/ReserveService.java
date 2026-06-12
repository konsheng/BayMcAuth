package com.baymc.auth.paper.service;

import com.baymc.auth.common.blacklist.BlacklistService;
import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.NameLock;
import com.baymc.auth.common.util.NameUtil;
import com.baymc.auth.storage.repository.NameLockRepository;
import com.baymc.auth.storage.repository.UserRepository;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReserveService {
    private final NameLockRepository nameLocks;
    private final UserRepository users;
    private final BlacklistService blacklistService;

    public ReserveService(NameLockRepository nameLocks, UserRepository users, BlacklistService blacklistService) {
        this.nameLocks = nameLocks;
        this.users = users;
        this.blacklistService = blacklistService;
    }

    public NameLock reserve(CommandSender sender, String playerName) {
        if (!NameUtil.validMinecraftName(playerName) || blacklistService.usernameBlocked(playerName) || !users.findByName(playerName).isEmpty()) {
            throw new IllegalArgumentException("玩家名不可预留");
        }
        if (nameLocks.findAnyByName(playerName).filter(NameLock::usable).isPresent()) {
            throw new IllegalArgumentException("名字已经被预留");
        }
        NameLock lock = new NameLock(0L, NameUtil.lower(playerName), playerName, null, AccountType.OFFLINE_PLAIN,
            "ADMIN_RESERVED", true, sender.getName(), sender instanceof Player player ? player.getUniqueId() : null,
            Instant.now(), false, null, null, null, null);
        return nameLocks.save(lock);
    }

    public NameLock revoke(CommandSender sender, String playerName) {
        NameLock lock = nameLocks.findActiveByName(playerName).orElseThrow();
        NameLock revoked = new NameLock(lock.id(), lock.nameLower(), lock.playerName(), lock.ownerUuid(), lock.accountType(),
            lock.lockType(), false, lock.createdBy(), lock.createdByUuid(), lock.createdAt(), true, sender.getName(),
            sender instanceof Player player ? player.getUniqueId() : null, Instant.now(), lock.note());
        return nameLocks.save(revoked);
    }

    public NameLock info(String playerName) {
        return nameLocks.findAnyByName(playerName).orElseThrow();
    }

    public List<NameLock> list() {
        return nameLocks.listActive(50);
    }
}

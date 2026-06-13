INSERT INTO {table} (name_lower, player_name, owner_uuid, account_type, lock_type, active, created_by, created_by_uuid,
created_at, revoked, revoked_by, revoked_by_uuid, revoked_at, note)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), owner_uuid = VALUES(owner_uuid), account_type = VALUES(account_type),
lock_type = VALUES(lock_type), active = VALUES(active), revoked = VALUES(revoked), revoked_by = VALUES(revoked_by),
revoked_by_uuid = VALUES(revoked_by_uuid), revoked_at = VALUES(revoked_at), note = VALUES(note)

INSERT INTO {table} (code, code_key, batch_id, used, used_by_uuid, used_by_name, used_by_name_lower, used_account_type,
used_ip, used_at, created_by, created_by_uuid, created_at, expires_at, revoked, revoked_by, revoked_by_uuid, revoked_at, note)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE used = VALUES(used), used_by_uuid = VALUES(used_by_uuid), used_by_name = VALUES(used_by_name),
used_by_name_lower = VALUES(used_by_name_lower), used_account_type = VALUES(used_account_type), used_ip = VALUES(used_ip),
used_at = VALUES(used_at), revoked = VALUES(revoked), revoked_by = VALUES(revoked_by), revoked_by_uuid = VALUES(revoked_by_uuid),
revoked_at = VALUES(revoked_at), note = VALUES(note)

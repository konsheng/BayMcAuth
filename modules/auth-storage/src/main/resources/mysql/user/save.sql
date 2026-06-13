INSERT INTO {table} (uuid, player_name, player_name_lower, account_type, password_enabled, password_plain, password_cipher,
totp_enabled, totp_confirmed, totp_secret, totp_pending_secret, register_invite_code, register_invite_id, locked,
locked_reason, locked_by, locked_by_uuid, locked_at, register_ip, last_login_ip, last_login_at, last_server_name,
created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), player_name_lower = VALUES(player_name_lower),
account_type = VALUES(account_type), password_enabled = VALUES(password_enabled), password_plain = VALUES(password_plain),
password_cipher = VALUES(password_cipher), totp_enabled = VALUES(totp_enabled), totp_confirmed = VALUES(totp_confirmed),
totp_secret = VALUES(totp_secret), totp_pending_secret = VALUES(totp_pending_secret), register_invite_code = VALUES(register_invite_code),
register_invite_id = VALUES(register_invite_id), locked = VALUES(locked), locked_reason = VALUES(locked_reason),
locked_by = VALUES(locked_by), locked_by_uuid = VALUES(locked_by_uuid), locked_at = VALUES(locked_at),
register_ip = VALUES(register_ip), last_login_ip = VALUES(last_login_ip), last_login_at = VALUES(last_login_at),
last_server_name = VALUES(last_server_name), updated_at = VALUES(updated_at)

SELECT * FROM {table} WHERE player_name_lower = ? AND (ip = ? OR ? IS NULL) AND consumed_at IS NULL AND expires_at > ? ORDER BY issued_at DESC LIMIT 1

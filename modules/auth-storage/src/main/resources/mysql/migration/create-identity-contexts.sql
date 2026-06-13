CREATE TABLE IF NOT EXISTS {identity_contexts} (
  id BIGINT NOT NULL AUTO_INCREMENT,
  context_id VARCHAR(64) NOT NULL,
  player_name_lower VARCHAR(32) NOT NULL,
  uuid CHAR(36) NULL,
  account_type VARCHAR(32) NOT NULL,
  ip VARCHAR(45) NULL,
  server_name VARCHAR(64) NULL,
  issued_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_context_id (context_id),
  KEY idx_player_ip (player_name_lower, ip),
  KEY idx_expires_at (expires_at),
  KEY idx_consumed_at (consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4

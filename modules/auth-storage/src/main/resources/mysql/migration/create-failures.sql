CREATE TABLE IF NOT EXISTS {failures} (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_uuid CHAR(36) NULL,
  player_name VARCHAR(32) NULL,
  player_name_lower VARCHAR(32) NULL,
  ip VARCHAR(45) NULL,
  account_type VARCHAR(32) NULL,
  action_type VARCHAR(32) NOT NULL,
  reason VARCHAR(255) NULL,
  server_name VARCHAR(64) NULL,
  failed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_uuid (user_uuid),
  KEY idx_player_name_lower (player_name_lower),
  KEY idx_ip (ip),
  KEY idx_action_type (action_type),
  KEY idx_failed_at (failed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4

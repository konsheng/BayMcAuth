CREATE TABLE IF NOT EXISTS {password_history} (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_uuid CHAR(36) NOT NULL,
  player_name VARCHAR(32) NOT NULL,
  player_name_lower VARCHAR(32) NOT NULL,
  account_type VARCHAR(32) NOT NULL,
  password_plain VARCHAR(255) NOT NULL,
  change_type VARCHAR(32) NOT NULL,
  changed_by VARCHAR(32) NULL,
  changed_by_uuid CHAR(36) NULL,
  ip VARCHAR(45) NULL,
  server_name VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user_uuid (user_uuid),
  KEY idx_player_name_lower (player_name_lower),
  KEY idx_change_type (change_type),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4

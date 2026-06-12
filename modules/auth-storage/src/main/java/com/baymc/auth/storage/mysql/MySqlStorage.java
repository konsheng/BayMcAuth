package com.baymc.auth.storage.mysql;

import com.baymc.auth.common.config.AuthConfig;
import com.baymc.auth.common.model.*;
import com.baymc.auth.common.security.InviteCodeGenerator;
import com.baymc.auth.common.util.NameUtil;
import com.baymc.auth.storage.repository.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * MySQL 存储入口
 *
 * <p>初始化只创建缺失表, 字段和索引
 * 不删除表, 不删除字段, 不执行破坏性迁移
 */
public final class MySqlStorage implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final String prefix;
    private final UserRepository users;
    private final InviteCodeRepository inviteCodes;
    private final NameLockRepository nameLocks;
    private final PasswordHistoryRepository passwordHistory;
    private final FailureRepository failures;
    private final AuditRepository audits;
    private final IdentityContextRepository identityContexts;

    public MySqlStorage(AuthConfig.Database database) {
        this.prefix = sanitizePrefix(database.tablePrefix());
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(database.jdbcUrl());
        config.setUsername(database.username());
        config.setPassword(database.password());
        config.setMaximumPoolSize(database.pool().maximumPoolSize());
        config.setMinimumIdle(database.pool().minimumIdle());
        config.setConnectionTimeout(database.pool().connectionTimeout().toMillis());
        config.setIdleTimeout(database.pool().idleTimeout().toMillis());
        config.setMaxLifetime(database.pool().maxLifetime().toMillis());
        config.setPoolName("BayMcAuth-Hikari");
        this.dataSource = new HikariDataSource(config);
        migrate();
        this.users = new JdbcUserRepository(dataSource, table("users"));
        this.inviteCodes = new JdbcInviteCodeRepository(dataSource, table("invite_codes"));
        this.nameLocks = new JdbcNameLockRepository(dataSource, table("name_locks"));
        this.passwordHistory = new JdbcPasswordHistoryRepository(dataSource, table("password_history"));
        this.failures = new JdbcFailureRepository(dataSource, table("failures"));
        this.audits = new JdbcAuditRepository(dataSource, table("audit_logs"));
        this.identityContexts = new JdbcIdentityContextRepository(dataSource, table("identity_contexts"));
    }

    public UserRepository users() {
        return users;
    }

    public InviteCodeRepository inviteCodes() {
        return inviteCodes;
    }

    public NameLockRepository nameLocks() {
        return nameLocks;
    }

    public PasswordHistoryRepository passwordHistory() {
        return passwordHistory;
    }

    public FailureRepository failures() {
        return failures;
    }

    public AuditRepository audits() {
        return audits;
    }

    public IdentityContextRepository identityContexts() {
        return identityContexts;
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private String table(String suffix) {
        return prefix + suffix;
    }

    private static String sanitizePrefix(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_]+")) {
            return "baymc_auth_";
        }
        return value;
    }

    private void migrate() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  uuid CHAR(36) NOT NULL,
                  player_name VARCHAR(32) NOT NULL,
                  player_name_lower VARCHAR(32) NOT NULL,
                  account_type VARCHAR(32) NOT NULL,
                  password_enabled TINYINT(1) NOT NULL DEFAULT 1,
                  password_plain VARCHAR(255) NULL,
                  password_cipher VARCHAR(255) NULL,
                  totp_enabled TINYINT(1) NOT NULL DEFAULT 0,
                  totp_confirmed TINYINT(1) NOT NULL DEFAULT 0,
                  totp_secret VARCHAR(255) NULL,
                  totp_pending_secret VARCHAR(255) NULL,
                  register_invite_code VARCHAR(128) NULL,
                  register_invite_id BIGINT NULL,
                  locked TINYINT(1) NOT NULL DEFAULT 0,
                  locked_reason VARCHAR(255) NULL,
                  locked_by VARCHAR(32) NULL,
                  locked_by_uuid CHAR(36) NULL,
                  locked_at DATETIME(3) NULL,
                  register_ip VARCHAR(45) NULL,
                  last_login_ip VARCHAR(45) NULL,
                  last_login_at DATETIME(3) NULL,
                  last_server_name VARCHAR(64) NULL,
                  created_at DATETIME(3) NOT NULL,
                  updated_at DATETIME(3) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_uuid (uuid),
                  KEY idx_player_name_lower (player_name_lower),
                  KEY idx_account_type (account_type),
                  KEY idx_register_invite_code (register_invite_code),
                  KEY idx_locked (locked),
                  KEY idx_last_login_at (last_login_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(table("users")));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS %s (
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
                """.formatted(table("password_history")));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  code VARCHAR(128) NOT NULL,
                  code_key VARCHAR(128) NOT NULL,
                  batch_id VARCHAR(64) NULL,
                  used TINYINT(1) NOT NULL DEFAULT 0,
                  used_by_uuid CHAR(36) NULL,
                  used_by_name VARCHAR(32) NULL,
                  used_by_name_lower VARCHAR(32) NULL,
                  used_account_type VARCHAR(32) NULL,
                  used_ip VARCHAR(45) NULL,
                  used_at DATETIME(3) NULL,
                  created_by VARCHAR(32) NULL,
                  created_by_uuid CHAR(36) NULL,
                  created_at DATETIME(3) NOT NULL,
                  expires_at DATETIME(3) NOT NULL,
                  revoked TINYINT(1) NOT NULL DEFAULT 0,
                  revoked_by VARCHAR(32) NULL,
                  revoked_by_uuid CHAR(36) NULL,
                  revoked_at DATETIME(3) NULL,
                  note VARCHAR(255) NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_code_key (code_key),
                  KEY idx_used (used),
                  KEY idx_revoked (revoked),
                  KEY idx_expires_at (expires_at),
                  KEY idx_used_by_uuid (used_by_uuid),
                  KEY idx_batch_id (batch_id),
                  KEY idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(table("invite_codes")));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  name_lower VARCHAR(32) NOT NULL,
                  player_name VARCHAR(32) NOT NULL,
                  owner_uuid CHAR(36) NULL,
                  account_type VARCHAR(32) NOT NULL DEFAULT 'OFFLINE_PLAIN',
                  lock_type VARCHAR(32) NOT NULL DEFAULT 'ADMIN_RESERVED',
                  active TINYINT(1) NOT NULL DEFAULT 1,
                  created_by VARCHAR(32) NULL,
                  created_by_uuid CHAR(36) NULL,
                  created_at DATETIME(3) NOT NULL,
                  revoked TINYINT(1) NOT NULL DEFAULT 0,
                  revoked_by VARCHAR(32) NULL,
                  revoked_by_uuid CHAR(36) NULL,
                  revoked_at DATETIME(3) NULL,
                  note VARCHAR(255) NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_name_lower (name_lower),
                  KEY idx_owner_uuid (owner_uuid),
                  KEY idx_active (active),
                  KEY idx_revoked (revoked),
                  KEY idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(table("name_locks")));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS %s (
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
                """.formatted(table("failures")));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  event_type VARCHAR(64) NOT NULL,
                  player_name VARCHAR(32) NULL,
                  player_name_lower VARCHAR(32) NULL,
                  uuid CHAR(36) NULL,
                  account_type VARCHAR(32) NULL,
                  ip VARCHAR(45) NULL,
                  server_name VARCHAR(64) NULL,
                  result VARCHAR(32) NULL,
                  reason VARCHAR(255) NULL,
                  message TEXT NOT NULL,
                  created_at DATETIME(3) NOT NULL,
                  PRIMARY KEY (id),
                  KEY idx_event_type (event_type),
                  KEY idx_player_name_lower (player_name_lower),
                  KEY idx_uuid (uuid),
                  KEY idx_ip (ip),
                  KEY idx_account_type (account_type),
                  KEY idx_result (result),
                  KEY idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(table("audit_logs")));
            statement.execute("""
                CREATE TABLE IF NOT EXISTS %s (
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
                """.formatted(table("identity_contexts")));
            ensureUserColumns(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("MySQL 初始化失败", exception);
        }
    }

    private void ensureUserColumns(Connection connection) throws SQLException {
        ensureColumn(connection, table("users"), "totp_pending_secret", "VARCHAR(255) NULL");
        ensureColumn(connection, table("users"), "last_server_name", "VARCHAR(64) NULL");
        ensureColumn(connection, table("identity_contexts"), "consumed_at", "DATETIME(3) NULL");
    }

    private void ensureColumn(Connection connection, String table, String column, String definition) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
            """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) == 0) {
                    try (Statement alter = connection.createStatement()) {
                        alter.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                    }
                }
            }
        }
    }

    static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    static String uuid(UUID value) {
        return value == null ? null : value.toString();
    }

    static boolean bool(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getInt(column) != 0;
    }

    static void bindUuid(PreparedStatement statement, int index, UUID uuid) throws SQLException {
        statement.setString(index, uuid(uuid));
    }

    private static RuntimeException sql(SQLException exception) {
        return new IllegalStateException("MySQL 操作失败", exception);
    }

    private static final class JdbcUserRepository implements UserRepository {
        private final DataSource dataSource;
        private final String table;

        private JdbcUserRepository(DataSource dataSource, String table) {
            this.dataSource = dataSource;
            this.table = table;
        }

        @Override
        public Optional<UserRecord> findByUuid(UUID uuid) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapUser(resultSet)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public List<UserRecord> findByName(String playerName) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE player_name_lower = ?")) {
                statement.setString(1, NameUtil.lower(playerName));
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<UserRecord> result = new ArrayList<>();
                    while (resultSet.next()) {
                        result.add(mapUser(resultSet));
                    }
                    return result;
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public Optional<UserRecord> findByNameAndType(String playerName, AccountType accountType) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE player_name_lower = ? AND account_type = ?")) {
                statement.setString(1, NameUtil.lower(playerName));
                statement.setString(2, accountType.name());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapUser(resultSet)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public UserRecord save(UserRecord user) {
            String sql = """
                INSERT INTO %s (uuid, player_name, player_name_lower, account_type, password_enabled, password_plain, password_cipher,
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
                """.formatted(table);
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                bindUser(statement, user);
                statement.executeUpdate();
                return findByUuid(user.uuid()).orElseThrow();
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        private void bindUser(PreparedStatement statement, UserRecord user) throws SQLException {
            int index = 1;
            statement.setString(index++, user.uuid().toString());
            statement.setString(index++, user.playerName());
            statement.setString(index++, user.playerNameLower());
            statement.setString(index++, user.accountType().name());
            statement.setBoolean(index++, user.passwordEnabled());
            statement.setString(index++, user.passwordPlain());
            statement.setString(index++, user.passwordCipher());
            statement.setBoolean(index++, user.totpEnabled());
            statement.setBoolean(index++, user.totpConfirmed());
            statement.setString(index++, user.totpSecret());
            statement.setString(index++, user.totpPendingSecret());
            statement.setString(index++, user.registerInviteCode());
            if (user.registerInviteId() == null) statement.setObject(index++, null); else statement.setLong(index++, user.registerInviteId());
            statement.setBoolean(index++, user.locked());
            statement.setString(index++, user.lockedReason());
            statement.setString(index++, user.lockedBy());
            bindUuid(statement, index++, user.lockedByUuid());
            statement.setTimestamp(index++, SqlTime.timestamp(user.lockedAt()));
            statement.setString(index++, user.registerIp());
            statement.setString(index++, user.lastLoginIp());
            statement.setTimestamp(index++, SqlTime.timestamp(user.lastLoginAt()));
            statement.setString(index++, user.lastServerName());
            statement.setTimestamp(index++, SqlTime.timestamp(user.createdAt()));
            statement.setTimestamp(index, SqlTime.timestamp(user.updatedAt()));
        }

        private UserRecord mapUser(ResultSet resultSet) throws SQLException {
            return new UserRecord(
                resultSet.getLong("id"),
                uuid(resultSet.getString("uuid")),
                resultSet.getString("player_name"),
                resultSet.getString("player_name_lower"),
                AccountType.valueOf(resultSet.getString("account_type")),
                bool(resultSet, "password_enabled"),
                resultSet.getString("password_plain"),
                resultSet.getString("password_cipher"),
                bool(resultSet, "totp_enabled"),
                bool(resultSet, "totp_confirmed"),
                resultSet.getString("totp_secret"),
                resultSet.getString("totp_pending_secret"),
                resultSet.getString("register_invite_code"),
                resultSet.getObject("register_invite_id") == null ? null : resultSet.getLong("register_invite_id"),
                bool(resultSet, "locked"),
                resultSet.getString("locked_reason"),
                resultSet.getString("locked_by"),
                uuid(resultSet.getString("locked_by_uuid")),
                SqlTime.instant(resultSet, "locked_at"),
                resultSet.getString("register_ip"),
                resultSet.getString("last_login_ip"),
                SqlTime.instant(resultSet, "last_login_at"),
                resultSet.getString("last_server_name"),
                SqlTime.instant(resultSet, "created_at"),
                SqlTime.instant(resultSet, "updated_at")
            );
        }
    }

    private static final class JdbcInviteCodeRepository implements InviteCodeRepository {
        private final DataSource dataSource;
        private final String table;

        private JdbcInviteCodeRepository(DataSource dataSource, String table) {
            this.dataSource = dataSource;
            this.table = table;
        }

        @Override
        public Optional<InviteCode> findByCodeKey(String codeKey) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE code_key = ?")) {
                statement.setString(1, InviteCodeGenerator.key(codeKey));
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapInvite(resultSet)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public InviteCode save(InviteCode inviteCode) {
            String sql = """
                INSERT INTO %s (code, code_key, batch_id, used, used_by_uuid, used_by_name, used_by_name_lower, used_account_type,
                used_ip, used_at, created_by, created_by_uuid, created_at, expires_at, revoked, revoked_by, revoked_by_uuid, revoked_at, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE used = VALUES(used), used_by_uuid = VALUES(used_by_uuid), used_by_name = VALUES(used_by_name),
                used_by_name_lower = VALUES(used_by_name_lower), used_account_type = VALUES(used_account_type), used_ip = VALUES(used_ip),
                used_at = VALUES(used_at), revoked = VALUES(revoked), revoked_by = VALUES(revoked_by), revoked_by_uuid = VALUES(revoked_by_uuid),
                revoked_at = VALUES(revoked_at), note = VALUES(note)
                """.formatted(table);
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                statement.setString(index++, inviteCode.code());
                statement.setString(index++, inviteCode.codeKey());
                statement.setString(index++, inviteCode.batchId());
                statement.setBoolean(index++, inviteCode.used());
                bindUuid(statement, index++, inviteCode.usedByUuid());
                statement.setString(index++, inviteCode.usedByName());
                statement.setString(index++, inviteCode.usedByNameLower());
                statement.setString(index++, inviteCode.usedAccountType() == null ? null : inviteCode.usedAccountType().name());
                statement.setString(index++, inviteCode.usedIp());
                statement.setTimestamp(index++, SqlTime.timestamp(inviteCode.usedAt()));
                statement.setString(index++, inviteCode.createdBy());
                bindUuid(statement, index++, inviteCode.createdByUuid());
                statement.setTimestamp(index++, SqlTime.timestamp(inviteCode.createdAt()));
                statement.setTimestamp(index++, SqlTime.timestamp(inviteCode.expiresAt()));
                statement.setBoolean(index++, inviteCode.revoked());
                statement.setString(index++, inviteCode.revokedBy());
                bindUuid(statement, index++, inviteCode.revokedByUuid());
                statement.setTimestamp(index++, SqlTime.timestamp(inviteCode.revokedAt()));
                statement.setString(index, inviteCode.note());
                statement.executeUpdate();
                return findByCodeKey(inviteCode.codeKey()).orElseThrow();
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public List<InviteCode> list(int limit) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " ORDER BY created_at DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<InviteCode> result = new ArrayList<>();
                    while (resultSet.next()) {
                        result.add(mapInvite(resultSet));
                    }
                    return result;
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        private InviteCode mapInvite(ResultSet rs) throws SQLException {
            String usedType = rs.getString("used_account_type");
            return new InviteCode(rs.getLong("id"), rs.getString("code"), rs.getString("code_key"), rs.getString("batch_id"),
                bool(rs, "used"), uuid(rs.getString("used_by_uuid")), rs.getString("used_by_name"), rs.getString("used_by_name_lower"),
                usedType == null ? null : AccountType.valueOf(usedType), rs.getString("used_ip"), SqlTime.instant(rs, "used_at"),
                rs.getString("created_by"), uuid(rs.getString("created_by_uuid")), SqlTime.instant(rs, "created_at"),
                SqlTime.instant(rs, "expires_at"), bool(rs, "revoked"), rs.getString("revoked_by"), uuid(rs.getString("revoked_by_uuid")),
                SqlTime.instant(rs, "revoked_at"), rs.getString("note"));
        }
    }

    private static final class JdbcNameLockRepository implements NameLockRepository {
        private final DataSource dataSource;
        private final String table;

        private JdbcNameLockRepository(DataSource dataSource, String table) {
            this.dataSource = dataSource;
            this.table = table;
        }

        @Override
        public Optional<NameLock> findActiveByName(String playerName) {
            return findAnyByName(playerName).filter(NameLock::usable);
        }

        @Override
        public Optional<NameLock> findAnyByName(String playerName) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE name_lower = ?")) {
                statement.setString(1, NameUtil.lower(playerName));
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapLock(resultSet)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public NameLock save(NameLock lock) {
            String sql = """
                INSERT INTO %s (name_lower, player_name, owner_uuid, account_type, lock_type, active, created_by, created_by_uuid,
                created_at, revoked, revoked_by, revoked_by_uuid, revoked_at, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), owner_uuid = VALUES(owner_uuid), account_type = VALUES(account_type),
                lock_type = VALUES(lock_type), active = VALUES(active), revoked = VALUES(revoked), revoked_by = VALUES(revoked_by),
                revoked_by_uuid = VALUES(revoked_by_uuid), revoked_at = VALUES(revoked_at), note = VALUES(note)
                """.formatted(table);
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                statement.setString(index++, lock.nameLower());
                statement.setString(index++, lock.playerName());
                bindUuid(statement, index++, lock.ownerUuid());
                statement.setString(index++, lock.accountType().name());
                statement.setString(index++, lock.lockType());
                statement.setBoolean(index++, lock.active());
                statement.setString(index++, lock.createdBy());
                bindUuid(statement, index++, lock.createdByUuid());
                statement.setTimestamp(index++, SqlTime.timestamp(lock.createdAt()));
                statement.setBoolean(index++, lock.revoked());
                statement.setString(index++, lock.revokedBy());
                bindUuid(statement, index++, lock.revokedByUuid());
                statement.setTimestamp(index++, SqlTime.timestamp(lock.revokedAt()));
                statement.setString(index, lock.note());
                statement.executeUpdate();
                return findAnyByName(lock.playerName()).orElseThrow();
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public List<NameLock> listActive(int limit) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE active = 1 AND revoked = 0 ORDER BY created_at DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<NameLock> result = new ArrayList<>();
                    while (resultSet.next()) {
                        result.add(mapLock(resultSet));
                    }
                    return result;
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        private NameLock mapLock(ResultSet rs) throws SQLException {
            return new NameLock(rs.getLong("id"), rs.getString("name_lower"), rs.getString("player_name"), uuid(rs.getString("owner_uuid")),
                AccountType.valueOf(rs.getString("account_type")), rs.getString("lock_type"), bool(rs, "active"), rs.getString("created_by"),
                uuid(rs.getString("created_by_uuid")), SqlTime.instant(rs, "created_at"), bool(rs, "revoked"), rs.getString("revoked_by"),
                uuid(rs.getString("revoked_by_uuid")), SqlTime.instant(rs, "revoked_at"), rs.getString("note"));
        }
    }

    private static final class JdbcPasswordHistoryRepository implements PasswordHistoryRepository {
        private final DataSource dataSource;
        private final String table;

        private JdbcPasswordHistoryRepository(DataSource dataSource, String table) {
            this.dataSource = dataSource;
            this.table = table;
        }

        @Override
        public PasswordHistoryEntry add(PasswordHistoryEntry entry) {
            String sql = "INSERT INTO " + table + " (user_uuid, player_name, player_name_lower, account_type, password_plain, change_type, changed_by, changed_by_uuid, ip, server_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int index = 1;
                bindUuid(statement, index++, entry.userUuid());
                statement.setString(index++, entry.playerName());
                statement.setString(index++, entry.playerNameLower());
                statement.setString(index++, entry.accountType().name());
                statement.setString(index++, entry.passwordPlain());
                statement.setString(index++, entry.changeType().name());
                statement.setString(index++, entry.changedBy());
                bindUuid(statement, index++, entry.changedByUuid());
                statement.setString(index++, entry.ip());
                statement.setString(index++, entry.serverName());
                statement.setTimestamp(index, SqlTime.timestamp(entry.createdAt()));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    long id = keys.next() ? keys.getLong(1) : 0L;
                    return new PasswordHistoryEntry(id, entry.userUuid(), entry.playerName(), entry.playerNameLower(), entry.accountType(),
                        entry.passwordPlain(), entry.changeType(), entry.changedBy(), entry.changedByUuid(), entry.ip(), entry.serverName(), entry.createdAt());
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public List<PasswordHistoryEntry> findByUserUuid(UUID userUuid, int limit) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE user_uuid = ? ORDER BY created_at DESC LIMIT ?")) {
                statement.setString(1, userUuid.toString());
                statement.setInt(2, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    List<PasswordHistoryEntry> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(new PasswordHistoryEntry(rs.getLong("id"), uuid(rs.getString("user_uuid")), rs.getString("player_name"),
                            rs.getString("player_name_lower"), AccountType.valueOf(rs.getString("account_type")), rs.getString("password_plain"),
                            PasswordChangeType.valueOf(rs.getString("change_type")), rs.getString("changed_by"), uuid(rs.getString("changed_by_uuid")),
                            rs.getString("ip"), rs.getString("server_name"), SqlTime.instant(rs, "created_at")));
                    }
                    return result;
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }
    }

    private static final class JdbcFailureRepository implements FailureRepository {
        private final DataSource dataSource;
        private final String table;

        private JdbcFailureRepository(DataSource dataSource, String table) {
            this.dataSource = dataSource;
            this.table = table;
        }

        @Override
        public FailureRecord add(FailureRecord failure) {
            String sql = "INSERT INTO " + table + " (user_uuid, player_name, player_name_lower, ip, account_type, action_type, reason, server_name, failed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int index = 1;
                bindUuid(statement, index++, failure.userUuid());
                statement.setString(index++, failure.playerName());
                statement.setString(index++, failure.playerNameLower());
                statement.setString(index++, failure.ip());
                statement.setString(index++, failure.accountType() == null ? null : failure.accountType().name());
                statement.setString(index++, failure.actionType().name());
                statement.setString(index++, failure.reason());
                statement.setString(index++, failure.serverName());
                statement.setTimestamp(index, SqlTime.timestamp(failure.failedAt()));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    long id = keys.next() ? keys.getLong(1) : 0L;
                    return new FailureRecord(id, failure.userUuid(), failure.playerName(), failure.playerNameLower(), failure.ip(),
                        failure.accountType(), failure.actionType(), failure.reason(), failure.serverName(), failure.failedAt());
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public List<FailureRecord> findSince(UUID userUuid, String ip, FailureActionType actionType, Instant since) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE action_type = ? AND failed_at > ? AND (user_uuid = ? OR ip = ?)")) {
                statement.setString(1, actionType.name());
                statement.setTimestamp(2, SqlTime.timestamp(since));
                statement.setString(3, uuid(userUuid));
                statement.setString(4, ip);
                try (ResultSet rs = statement.executeQuery()) {
                    List<FailureRecord> result = new ArrayList<>();
                    while (rs.next()) {
                        String accountType = rs.getString("account_type");
                        result.add(new FailureRecord(rs.getLong("id"), uuid(rs.getString("user_uuid")), rs.getString("player_name"),
                            rs.getString("player_name_lower"), rs.getString("ip"), accountType == null ? null : AccountType.valueOf(accountType),
                            FailureActionType.valueOf(rs.getString("action_type")), rs.getString("reason"), rs.getString("server_name"),
                            SqlTime.instant(rs, "failed_at")));
                    }
                    return result;
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }
    }

    private static final class JdbcAuditRepository implements AuditRepository {
        private final DataSource dataSource;
        private final String table;

        private JdbcAuditRepository(DataSource dataSource, String table) {
            this.dataSource = dataSource;
            this.table = table;
        }

        @Override
        public AuditEntry add(AuditEntry entry) {
            String sql = "INSERT INTO " + table + " (event_type, player_name, player_name_lower, uuid, account_type, ip, server_name, result, reason, message, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int index = 1;
                statement.setString(index++, entry.eventType().name());
                statement.setString(index++, entry.playerName());
                statement.setString(index++, entry.playerNameLower());
                bindUuid(statement, index++, entry.uuid());
                statement.setString(index++, entry.accountType() == null ? null : entry.accountType().name());
                statement.setString(index++, entry.ip());
                statement.setString(index++, entry.serverName());
                statement.setString(index++, entry.result() == null ? null : entry.result().name());
                statement.setString(index++, entry.reason());
                statement.setString(index++, entry.message());
                statement.setTimestamp(index, SqlTime.timestamp(entry.createdAt()));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    long id = keys.next() ? keys.getLong(1) : 0L;
                    return new AuditEntry(id, entry.eventType(), entry.playerName(), entry.playerNameLower(), entry.uuid(), entry.accountType(),
                        entry.ip(), entry.serverName(), entry.result(), entry.reason(), entry.message(), entry.createdAt());
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }
    }

    private static final class JdbcIdentityContextRepository implements IdentityContextRepository {
        private final DataSource dataSource;
        private final String table;

        private JdbcIdentityContextRepository(DataSource dataSource, String table) {
            this.dataSource = dataSource;
            this.table = table;
        }

        @Override
        public IdentityContext save(IdentityContext context) {
            String sql = "INSERT INTO " + table + " (context_id, player_name_lower, uuid, account_type, ip, server_name, issued_at, expires_at, consumed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE consumed_at = VALUES(consumed_at)";
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, context.contextId());
                statement.setString(2, context.playerNameLower());
                bindUuid(statement, 3, context.uuid());
                statement.setString(4, context.accountType().name());
                statement.setString(5, context.ip());
                statement.setString(6, context.serverName());
                statement.setTimestamp(7, SqlTime.timestamp(context.issuedAt()));
                statement.setTimestamp(8, SqlTime.timestamp(context.expiresAt()));
                statement.setTimestamp(9, SqlTime.timestamp(context.consumedAt()));
                statement.executeUpdate();
                return context;
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public Optional<IdentityContext> findLatest(String playerName, String ip) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE player_name_lower = ? AND (ip = ? OR ? IS NULL) AND consumed_at IS NULL AND expires_at > ? ORDER BY issued_at DESC LIMIT 1")) {
                statement.setString(1, NameUtil.lower(playerName));
                statement.setString(2, ip);
                statement.setString(3, ip);
                statement.setTimestamp(4, SqlTime.timestamp(Instant.now()));
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(mapContext(rs)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public void consume(String contextId) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE " + table + " SET consumed_at = ? WHERE context_id = ?")) {
                statement.setTimestamp(1, SqlTime.timestamp(Instant.now()));
                statement.setString(2, contextId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        @Override
        public void purgeExpired() {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE expires_at < ?")) {
                statement.setTimestamp(1, SqlTime.timestamp(Instant.now()));
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw sql(exception);
            }
        }

        private IdentityContext mapContext(ResultSet rs) throws SQLException {
            return new IdentityContext(rs.getString("context_id"), rs.getString("player_name_lower"), uuid(rs.getString("uuid")),
                AccountType.valueOf(rs.getString("account_type")), rs.getString("ip"), rs.getString("server_name"),
                SqlTime.instant(rs, "issued_at"), SqlTime.instant(rs, "expires_at"), SqlTime.instant(rs, "consumed_at"));
        }
    }
}

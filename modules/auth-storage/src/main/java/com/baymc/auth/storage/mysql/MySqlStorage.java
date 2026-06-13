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
import java.util.Map;
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
            throw new IllegalArgumentException("Invalid database.table-prefix: " + value);
        }
        return value;
    }

    private void migrate() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sqlTemplate("migration/create-users", Map.of("users", table("users"))));
            statement.execute(sqlTemplate("migration/create-password-history", Map.of("password_history", table("password_history"))));
            statement.execute(sqlTemplate("migration/create-invite-codes", Map.of("invite_codes", table("invite_codes"))));
            statement.execute(sqlTemplate("migration/create-name-locks", Map.of("name_locks", table("name_locks"))));
            statement.execute(sqlTemplate("migration/create-failures", Map.of("failures", table("failures"))));
            statement.execute(sqlTemplate("migration/create-audit-logs", Map.of("audit_logs", table("audit_logs"))));
            statement.execute(sqlTemplate("migration/create-identity-contexts", Map.of("identity_contexts", table("identity_contexts"))));
            ensureUserColumns(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("MySQL 初始化失败", exception);
        }
    }

    private void ensureUserColumns(Connection connection) throws SQLException {
        ensureColumn(connection, table("users"), "totp_pending_secret", "migration/add-users-totp-pending-secret");
        ensureColumn(connection, table("users"), "last_server_name", "migration/add-users-last-server-name");
        ensureColumn(connection, table("identity_contexts"), "consumed_at", "migration/add-identity-contexts-consumed-at");
    }

    private void ensureColumn(Connection connection, String table, String column, String alterTemplate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sqlTemplate("migration/column-exists"))) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) == 0) {
                    try (Statement alter = connection.createStatement()) {
                        alter.execute(sqlTemplate(alterTemplate, table));
                    }
                }
            }
        }
    }

    private static String sqlTemplate(String name) {
        return SqlTemplates.render(name, Map.of());
    }

    private static String sqlTemplate(String name, String table) {
        return SqlTemplates.render(name, Map.of("table", table));
    }

    private static String sqlTemplate(String name, Map<String, String> placeholders) {
        return SqlTemplates.render(name, placeholders);
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("user/find-by-uuid", table))) {
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("user/find-by-name", table))) {
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("user/find-by-name-and-type", table))) {
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
            String sql = sqlTemplate("user/save", table);
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("invite-code/find-by-code-key", table))) {
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
            String sql = sqlTemplate("invite-code/save", table);
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("invite-code/list", table))) {
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("name-lock/find-any-by-name", table))) {
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
            String sql = sqlTemplate("name-lock/save", table);
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("name-lock/list-active", table))) {
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
            String sql = sqlTemplate("password-history/add", table);
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("password-history/find-by-user-uuid", table))) {
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
            String sql = sqlTemplate("failure/add", table);
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("failure/find-since", table))) {
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
            String sql = sqlTemplate("audit/add", table);
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
            String sql = sqlTemplate("identity-context/save", table);
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("identity-context/find-latest", table))) {
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("identity-context/consume", table))) {
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
                 PreparedStatement statement = connection.prepareStatement(sqlTemplate("identity-context/purge-expired", table))) {
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

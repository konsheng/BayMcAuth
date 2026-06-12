package com.baymc.auth.common.config;

import com.baymc.auth.common.util.DurationParser;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/*
 * BayMcAuth 运行配置
 *
 * <p>配置键名保持 YAML 层级短横线风格
 * 读取层把时间字符串转换为 Duration, 后续业务只使用强类型配置
 */
public record AuthConfig(
    Settings settings,
    Database database,
    Redis redis,
    Session session,
    AccountTypes accountTypes,
    OfflineAffix offlineAffix,
    PlainName plainName,
    ChineseName chineseName,
    Invite invite,
    Password password,
    Totp totp,
    Login login,
    FailureLock failureLock,
    LoginPrompt loginPrompt,
    FirstJoin firstJoin,
    Blacklist blacklist,
    Audit audit,
    Security security,
    Confirm confirm
) {
    public static AuthConfig from(YamlDocument document, Consumer<String> warnings) {
        ConfigReader reader = new ConfigReader(document.values(), warnings);
        return new AuthConfig(
            new Settings(reader.string("settings.language", "zh_CN"), ZoneId.of(reader.string("settings.timezone", "Asia/Shanghai"))),
            new Database(
                reader.string("database.type", "mysql"),
                reader.string("database.host", "127.0.0.1"),
                reader.integer("database.port", 3306),
                reader.string("database.database", "baymc"),
                reader.string("database.username", "root"),
                reader.string("database.password", ""),
                reader.string("database.table-prefix", "baymc_auth_"),
                new Pool(
                    reader.integer("database.pool.maximum-pool-size", 10),
                    reader.integer("database.pool.minimum-idle", 2),
                    duration(reader.string("database.pool.connection-timeout", "10s")),
                    duration(reader.string("database.pool.idle-timeout", "10m")),
                    duration(reader.string("database.pool.max-lifetime", "30m"))
                )
            ),
            new Redis(
                reader.bool("redis.enabled", true),
                reader.string("redis.host", "127.0.0.1"),
                reader.integer("redis.port", 6379),
                reader.string("redis.password", ""),
                reader.integer("redis.database", 0),
                reader.string("redis.key-prefix", "baymcauth"),
                duration(reader.string("redis.connect-timeout", "5s")),
                duration(reader.string("redis.command-timeout", "5s"))
            ),
            new Session(
                reader.string("session.mode", "redis"),
                duration(reader.string("session.expire", "7d")),
                reader.bool("session.keep-online-when-redis-down", true)
            ),
            new AccountTypes(
                reader.bool("account-types.name-affix", true),
                reader.bool("account-types.name-plain", true),
                reader.bool("account-types.name-chinese", true)
            ),
            new OfflineAffix(
                reader.string("offline-affix.mode", "both"),
                reader.stringList("offline-affix.prefixes"),
                reader.stringList("offline-affix.suffixes"),
                reader.bool("offline-affix.case-sensitive", true),
                reader.bool("offline-affix.reject-wrong-case", true)
            ),
            new PlainName(reader.bool("plain-name.revoke-requires-confirm", true)),
            new ChineseName(reader.bool("chinese-name.allow", true)),
            new Invite(
                reader.bool("invite.require-for-offline-register", true),
                reader.integer("invite.default-expire-days", 7),
                new InviteFormat(
                    reader.string("invite.format.prefix", "BAYMC"),
                    reader.intList("invite.format.groups"),
                    reader.string("invite.format.separator", "-"),
                    reader.string("invite.format.charset", "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"),
                    reader.bool("invite.format.uppercase", true)
                )
            ),
            new Password(
                reader.integer("password.min-length", 6),
                reader.integer("password.max-length", 64),
                reader.integer("password.bcrypt-cost", 12),
                reader.bool("password.store-current-plain", true),
                reader.bool("password.store-history-plain", true)
            ),
            new Totp(
                reader.string("totp.issuer", "BayMc"),
                reader.integer("totp.digits", 6),
                duration(reader.string("totp.period", "30s")),
                reader.integer("totp.window", 1)
            ),
            new Login(
                duration(reader.string("login.timeout", "60s")),
                reader.bool("login.allow-premium-auto-login", true)
            ),
            new FailureLock(
                reader.bool("failure-lock.enabled", true),
                thresholds(reader.mapList("failure-lock.password.account-thresholds")),
                thresholds(reader.mapList("failure-lock.totp.account-thresholds")),
                thresholds(reader.mapList("failure-lock.ip.thresholds"))
            ),
            new LoginPrompt(
                channel(reader, "login-prompt.bossbar", "1s"),
                channel(reader, "login-prompt.title", "10s"),
                channel(reader, "login-prompt.subtitle", "10s"),
                channel(reader, "login-prompt.actionbar", "1s"),
                channel(reader, "login-prompt.chat", "15s")
            ),
            new FirstJoin(reader.bool("first-join.enabled", true), duration(reader.string("first-join.delay", "1s")), reader.integer("first-join.times", 1)),
            new Blacklist(
                blacklistGroup(reader, "blacklist.username"),
                blacklistGroup(reader, "blacklist.password")
            ),
            new Audit(
                reader.bool("audit.enabled", true),
                reader.bool("audit.console", true),
                reader.bool("audit.file", true),
                reader.bool("audit.database", true),
                reader.string("audit.directory", "logs")
            ),
            new Security(
                reader.bool("security.hide-totp-secret-in-logs", true),
                reader.bool("security.hide-totp-code-in-logs", true),
                reader.bool("security.hide-password-in-logs", true)
            ),
            new Confirm(duration(reader.string("confirm.expire", "30s")))
        );
    }

    public boolean redisSession() {
        return redis.enabled() && "redis".equalsIgnoreCase(session.mode());
    }

    private static Duration duration(String value) {
        return DurationParser.parse(value);
    }

    private static PromptChannel channel(ConfigReader reader, String base, String defaultInterval) {
        return new PromptChannel(reader.bool(base + ".enabled", true), duration(reader.string(base + ".update-interval", reader.string(base + ".send-interval", defaultInterval))));
    }

    private static BlacklistGroup blacklistGroup(ConfigReader reader, String base) {
        return new BlacklistGroup(
            reader.bool(base + ".enabled", true),
            reader.stringList(base + ".remote-urls"),
            reader.stringList(base + ".local-extra"),
            duration(reader.string(base + ".connect-timeout", "5s")),
            duration(reader.string(base + ".read-timeout", "10s")),
            reader.string(base + ".max-file-size", "1mb")
        );
    }

    private static List<Threshold> thresholds(List<Map<String, Object>> maps) {
        List<Threshold> result = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            Object attempts = map.get("attempts");
            Object lock = map.get("lock");
            if (attempts instanceof Number number && lock != null) {
                result.add(new Threshold(number.intValue(), duration(String.valueOf(lock))));
            }
        }
        return result;
    }

    public record Settings(String language, ZoneId timezone) {
    }

    public record Database(String type, String host, int port, String database, String username, String password, String tablePrefix, Pool pool) {
        public String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&characterEncoding=utf8&useUnicode=true&serverTimezone=UTC";
        }
    }

    public record Pool(int maximumPoolSize, int minimumIdle, Duration connectionTimeout, Duration idleTimeout, Duration maxLifetime) {
    }

    public record Redis(boolean enabled, String host, int port, String password, int database, String keyPrefix, Duration connectTimeout, Duration commandTimeout) {
        public String uri() {
            String auth = password == null || password.isBlank() ? "" : ":" + password + "@";
            return "redis://" + auth + host + ":" + port + "/" + database;
        }
    }

    public record Session(String mode, Duration expire, boolean keepOnlineWhenRedisDown) {
    }

    public record AccountTypes(boolean nameAffix, boolean namePlain, boolean nameChinese) {
    }

    public record OfflineAffix(String mode, List<String> prefixes, List<String> suffixes, boolean caseSensitive, boolean rejectWrongCase) {
    }

    public record PlainName(boolean revokeRequiresConfirm) {
    }

    public record ChineseName(boolean allow) {
    }

    public record Invite(boolean requireForOfflineRegister, int defaultExpireDays, InviteFormat format) {
    }

    public record InviteFormat(String prefix, List<Integer> groups, String separator, String charset, boolean uppercase) {
    }

    public record Password(int minLength, int maxLength, int bcryptCost, boolean storeCurrentPlain, boolean storeHistoryPlain) {
    }

    public record Totp(String issuer, int digits, Duration period, int window) {
    }

    public record Login(Duration timeout, boolean allowPremiumAutoLogin) {
    }

    public record FailureLock(boolean enabled, List<Threshold> passwordAccountThresholds, List<Threshold> totpAccountThresholds, List<Threshold> ipThresholds) {
    }

    public record Threshold(int attempts, Duration lock) {
    }

    public record LoginPrompt(PromptChannel bossbar, PromptChannel title, PromptChannel subtitle, PromptChannel actionbar, PromptChannel chat) {
    }

    public record PromptChannel(boolean enabled, Duration interval) {
    }

    public record FirstJoin(boolean enabled, Duration delay, int times) {
    }

    public record Blacklist(BlacklistGroup username, BlacklistGroup password) {
    }

    public record BlacklistGroup(boolean enabled, List<String> remoteUrls, List<String> localExtra, Duration connectTimeout, Duration readTimeout, String maxFileSize) {
    }

    public record Audit(boolean enabled, boolean console, boolean file, boolean database, String directory) {
    }

    public record Security(boolean hideTotpSecretInLogs, boolean hideTotpCodeInLogs, boolean hidePasswordInLogs) {
    }

    public record Confirm(Duration expire) {
    }
}

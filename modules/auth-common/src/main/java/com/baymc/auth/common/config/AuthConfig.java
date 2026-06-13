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
        ConfigReader reader = new ConfigReader(document, warnings);
        return new AuthConfig(
            new Settings(
                reader.string("settings.language"),
                ZoneId.of(reader.string("settings.timezone")),
                reader.bool("settings.debug")
            ),
            new Database(
                reader.string("database.type"),
                reader.string("database.host"),
                reader.integer("database.port"),
                reader.string("database.database"),
                reader.string("database.username"),
                reader.string("database.password"),
                reader.string("database.table-prefix"),
                new Pool(
                    reader.integer("database.pool.maximum-pool-size"),
                    reader.integer("database.pool.minimum-idle"),
                    duration(reader.string("database.pool.connection-timeout")),
                    duration(reader.string("database.pool.idle-timeout")),
                    duration(reader.string("database.pool.max-lifetime"))
                )
            ),
            new Redis(
                reader.bool("redis.enabled"),
                reader.string("redis.host"),
                reader.integer("redis.port"),
                reader.string("redis.password"),
                reader.integer("redis.database"),
                reader.string("redis.key-prefix"),
                duration(reader.string("redis.connect-timeout")),
                duration(reader.string("redis.command-timeout"))
            ),
            new Session(
                reader.string("session.mode"),
                duration(reader.string("session.expire")),
                reader.bool("session.keep-online-when-redis-down")
            ),
            new AccountTypes(
                reader.bool("account-types.name-affix"),
                reader.bool("account-types.name-plain"),
                reader.bool("account-types.name-chinese")
            ),
            new OfflineAffix(
                reader.string("offline-affix.mode"),
                reader.stringList("offline-affix.prefixes"),
                reader.stringList("offline-affix.suffixes"),
                reader.bool("offline-affix.case-sensitive"),
                reader.bool("offline-affix.reject-wrong-case")
            ),
            new PlainName(reader.bool("plain-name.revoke-requires-confirm")),
            new ChineseName(reader.bool("chinese-name.allow")),
            new Invite(
                reader.bool("invite.require-for-offline-register"),
                reader.integer("invite.default-expire-days"),
                new InviteFormat(
                    reader.string("invite.format.prefix"),
                    reader.intList("invite.format.groups"),
                    reader.string("invite.format.separator"),
                    reader.string("invite.format.charset"),
                    reader.bool("invite.format.uppercase")
                )
            ),
            new Password(
                reader.integer("password.min-length"),
                reader.integer("password.max-length"),
                reader.integer("password.bcrypt-cost")
            ),
            new Totp(
                reader.string("totp.issuer"),
                reader.integer("totp.digits"),
                duration(reader.string("totp.period")),
                reader.integer("totp.window")
            ),
            new Login(
                duration(reader.string("login.timeout")),
                reader.bool("login.allow-premium-auto-login")
            ),
            new FailureLock(
                reader.bool("failure-lock.enabled"),
                thresholds(reader.mapList("failure-lock.password.account-thresholds")),
                thresholds(reader.mapList("failure-lock.totp.account-thresholds")),
                thresholds(reader.mapList("failure-lock.ip.thresholds"))
            ),
            new LoginPrompt(
                channel(reader, "login-prompt.bossbar", "update-interval"),
                channel(reader, "login-prompt.title", "send-interval"),
                channel(reader, "login-prompt.subtitle", "send-interval"),
                channel(reader, "login-prompt.actionbar", "update-interval"),
                channel(reader, "login-prompt.chat", "send-interval")
            ),
            new FirstJoin(reader.bool("first-join.enabled"), duration(reader.string("first-join.delay")), reader.integer("first-join.times")),
            new Blacklist(
                blacklistGroup(reader, "blacklist.username"),
                blacklistGroup(reader, "blacklist.password")
            ),
            new Audit(
                reader.bool("audit.enabled"),
                reader.bool("audit.console"),
                reader.bool("audit.file"),
                reader.bool("audit.database"),
                reader.string("audit.directory")
            ),
            new Security(
                reader.bool("security.hide-totp-secret-in-logs"),
                reader.bool("security.hide-totp-code-in-logs"),
                reader.bool("security.hide-password-in-logs")
            ),
            new Confirm(duration(reader.string("confirm.expire")))
        );
    }

    public boolean redisSession() {
        return redis.enabled() && "redis".equalsIgnoreCase(session.mode());
    }

    private static Duration duration(String value) {
        return DurationParser.parse(value);
    }

    private static PromptChannel channel(ConfigReader reader, String base, String intervalKey) {
        return new PromptChannel(reader.bool(base + ".enabled"), duration(reader.string(base + "." + intervalKey)));
    }

    private static BlacklistGroup blacklistGroup(ConfigReader reader, String base) {
        return new BlacklistGroup(
            reader.bool(base + ".enabled"),
            reader.stringList(base + ".remote-urls"),
            reader.stringList(base + ".local-extra"),
            duration(reader.string(base + ".connect-timeout")),
            duration(reader.string(base + ".read-timeout")),
            reader.string(base + ".max-file-size")
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

    public record Settings(String language, ZoneId timezone, boolean debug) {
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

    public record Password(int minLength, int maxLength, int bcryptCost) {
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

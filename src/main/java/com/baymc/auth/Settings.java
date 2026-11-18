package com.baymc.auth;

import com.baymc.auth.command.CommandPermissionState;
import com.baymc.auth.dependencies.DatabaseLibrary;
import java.util.List;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.file.BuiltInWorldFileType;
import net.elytrium.limboapi.api.player.GameMode;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;

public class Settings {

  public static volatile Settings IMP;

  public String PREFIX;

  public MAIN MAIN;

  public static class MAIN {

    public int AUTH_TIME;
    public boolean ENABLE_BOSSBAR;
    public BossBar.Color BOSSBAR_COLOR;
    public BossBar.Overlay BOSSBAR_OVERLAY;
    public int MIN_PASSWORD_LENGTH;
    public int MAX_PASSWORD_LENGTH;
    public boolean CHECK_PASSWORD_STRENGTH;
    public String UNSAFE_PASSWORDS_FILE;
    public boolean ONLINE_MODE_NEED_AUTH;
    public boolean ONLINE_MODE_NEED_AUTH_STRICT;
    public boolean FLOODGATE_NEED_AUTH;
    public boolean FORCE_OFFLINE_MODE;
    public boolean FORCE_OFFLINE_UUID;
    public boolean CHECK_PREMIUM_PRIORITY_INTERNAL;
    public int PREMIUM_AND_FLOODGATE_MESSAGES_DELAY;
    public boolean SAVE_UUID;
    public boolean SAVE_PREMIUM_ACCOUNTS;
    public boolean ENABLE_TOTP;
    public boolean TOTP_NEED_PASSWORD;
    public boolean REGISTER_NEED_REPEAT_PASSWORD;
    public boolean CHANGE_PASSWORD_NEED_OLD_PASSWORD;
    public String CONFIRM_KEYWORD;
    public String OFFLINE_MODE_PREFIX;
    public String ONLINE_MODE_PREFIX;
    public Dimension DIMENSION;
    public long PURGE_CACHE_MILLIS;
    public long PURGE_PREMIUM_CACHE_MILLIS;
    public long PURGE_BRUTEFORCE_CACHE_MILLIS;
    public int BRUTEFORCE_MAX_ATTEMPTS;
    public String QR_GENERATOR_URL;
    public String TOTP_ISSUER;
    public int BCRYPT_COST;
    public int LOGIN_ATTEMPTS;
    public int TOTP_RECOVERY_CODES_AMOUNT;

    public int IP_LIMIT_REGISTRATIONS;
    public long IP_LIMIT_VALID_TIME;

    public String ALLOWED_NICKNAME_REGEX;

    public boolean LOAD_WORLD;
    public BuiltInWorldFileType WORLD_FILE_TYPE;
    public String WORLD_FILE_PATH;
    public boolean DISABLE_FALLING;

    public long WORLD_TICKS;

    public int WORLD_LIGHT_LEVEL;

    public GameMode GAME_MODE;

    public String ISPREMIUM_AUTH_URL;

    public List<Integer> STATUS_CODE_USER_EXISTS;
    public List<Integer> STATUS_CODE_USER_NOT_EXISTS;
    public List<Integer> STATUS_CODE_RATE_LIMIT;

    public List<String> USER_EXISTS_JSON_VALIDATOR_FIELDS;
    public String JSON_UUID_FIELD;
    public List<String> USER_NOT_EXISTS_JSON_VALIDATOR_FIELDS;

    public boolean ON_RATE_LIMIT_PREMIUM;

    public boolean ON_SERVER_ERROR_PREMIUM;

    public List<String> REGISTER_COMMAND;
    public List<String> LOGIN_COMMAND;
    public List<String> TOTP_COMMAND;

    public boolean DISABLE_REGISTRATIONS;

    public Settings.MAIN.WORLD_COORDS WORLD_COORDS;

    public static class WORLD_COORDS {

      public int X;
      public int Y;
      public int Z;
    }

    public MAIN.AUTH_COORDS AUTH_COORDS;

    public static class AUTH_COORDS {

      public double X;
      public double Y;
      public double Z;
      public double YAW;
      public double PITCH;
    }

    public Settings.MAIN.CRACKED_TITLE_SETTINGS CRACKED_TITLE_SETTINGS;

    public static class CRACKED_TITLE_SETTINGS {

      public int FADE_IN;
      public int STAY;
      public int FADE_OUT;
      public boolean CLEAR_AFTER_LOGIN;

      public Title.Times toTimes() {
        return Title.Times.times(Ticks.duration(this.FADE_IN), Ticks.duration(this.STAY), Ticks.duration(this.FADE_OUT));
      }
    }

    public Settings.MAIN.PREMIUM_TITLE_SETTINGS PREMIUM_TITLE_SETTINGS;

    public static class PREMIUM_TITLE_SETTINGS {

      public int FADE_IN;
      public int STAY;
      public int FADE_OUT;

      public Title.Times toTimes() {
        return Title.Times.times(Ticks.duration(this.FADE_IN), Ticks.duration(this.STAY), Ticks.duration(this.FADE_OUT));
      }
    }

    public Settings.MAIN.BACKEND_API BACKEND_API;

    public static class BACKEND_API {

      public boolean ENABLED;

      public String TOKEN;

      public List<String> ENABLED_ENDPOINTS;
    }

    public MAIN.COMMAND_PERMISSION_STATE COMMAND_PERMISSION_STATE;

    public static class COMMAND_PERMISSION_STATE {
      public CommandPermissionState CHANGE_PASSWORD;
      public CommandPermissionState DESTROY_SESSION;
      public CommandPermissionState PREMIUM;
      public CommandPermissionState TOTP;
      public CommandPermissionState UNREGISTER;

      public CommandPermissionState FORCE_CHANGE_PASSWORD;
      public CommandPermissionState FORCE_REGISTER;
      public CommandPermissionState FORCE_LOGIN;
      public CommandPermissionState FORCE_UNREGISTER;
      public CommandPermissionState RELOAD;
      public CommandPermissionState HELP;
    }

    public MAIN.STRINGS STRINGS;

    public static class STRINGS {

      public String RELOAD;
      public String ERROR_OCCURRED;
      public String RATELIMITED;
      public String DATABASE_ERROR_KICK;

      public String NOT_PLAYER;
      public String NOT_REGISTERED;
      public String CRACKED_COMMAND;
      public String WRONG_PASSWORD;
      public String PASSWORD_CHANGED_KICK;

      public String NICKNAME_INVALID_KICK;
      public String RECONNECT_KICK;

      public String IP_LIMIT_KICK;
      public String WRONG_NICKNAME_CASE_KICK;

      public String BOSSBAR;
      public String TIMES_UP;

      public String LOGIN_PREMIUM;
      public String LOGIN_PREMIUM_TITLE;
      public String LOGIN_PREMIUM_SUBTITLE;
      public String LOGIN_FLOODGATE;
      public String LOGIN_FLOODGATE_TITLE;
      public String LOGIN_FLOODGATE_SUBTITLE;

      public String LOGIN;
      public String LOGIN_WRONG_PASSWORD;
      public String LOGIN_WRONG_PASSWORD_KICK;
      public String LOGIN_SUCCESSFUL;
      public String LOGIN_TITLE;
      public String LOGIN_SUBTITLE;
      public String LOGIN_SUCCESSFUL_TITLE;
      public String LOGIN_SUCCESSFUL_SUBTITLE;

      public String REGISTER;
      public String REGISTER_DIFFERENT_PASSWORDS;
      public String REGISTER_PASSWORD_TOO_SHORT;
      public String REGISTER_PASSWORD_TOO_LONG;
      public String REGISTER_PASSWORD_UNSAFE;
      public String REGISTER_SUCCESSFUL;
      public String REGISTER_TITLE;
      public String REGISTER_SUBTITLE;
      public String REGISTER_SUCCESSFUL_TITLE;
      public String REGISTER_SUCCESSFUL_SUBTITLE;

      public String UNREGISTER_SUCCESSFUL;
      public String UNREGISTER_USAGE;

      public String PREMIUM_SUCCESSFUL;
      public String ALREADY_PREMIUM;
      public String NOT_PREMIUM;
      public String PREMIUM_USAGE;

      public String EVENT_CANCELLED;

      public String FORCE_UNREGISTER_SUCCESSFUL;
      public String FORCE_UNREGISTER_KICK;
      public String FORCE_UNREGISTER_NOT_SUCCESSFUL;
      public String FORCE_UNREGISTER_USAGE;

      public String REGISTRATIONS_DISABLED_KICK;

      public String CHANGE_PASSWORD_SUCCESSFUL;
      public String CHANGE_PASSWORD_USAGE;

      public String FORCE_CHANGE_PASSWORD_SUCCESSFUL;
      public String FORCE_CHANGE_PASSWORD_MESSAGE;
      public String FORCE_CHANGE_PASSWORD_NOT_SUCCESSFUL;
      public String FORCE_CHANGE_PASSWORD_NOT_REGISTERED;
      public String FORCE_CHANGE_PASSWORD_USAGE;

      public String FORCE_REGISTER_USAGE;
      public String FORCE_REGISTER_INCORRECT_NICKNAME;
      public String FORCE_REGISTER_TAKEN_NICKNAME;
      public String FORCE_REGISTER_SUCCESSFUL;
      public String FORCE_REGISTER_NOT_SUCCESSFUL;

      public String FORCE_LOGIN_USAGE;
      public String FORCE_LOGIN_SUCCESSFUL;
      public String FORCE_LOGIN_UNKNOWN_PLAYER;

      public String TOTP;
      public String TOTP_TITLE;
      public String TOTP_SUBTITLE;
      public String TOTP_SUCCESSFUL;
      public String TOTP_DISABLED;
      public String TOTP_USAGE;
      public String TOTP_WRONG;
      public String TOTP_ALREADY_ENABLED;
      public String TOTP_QR;
      public String TOTP_TOKEN;
      public String TOTP_RECOVERY;

      public String DESTROY_SESSION_SUCCESSFUL;
      public String HELP_HEADER;
      public String HELP_COPYRIGHT;
      public String HELP_WEBSITE;
      public String HELP_AVAILABLE_SUBCOMMANDS;
      public String HELP_NO_AVAILABLE_SUBCOMMANDS;
      public String HELP_RELOAD_DESCRIPTION;
      public String RELOAD_FAILED;
    }
  }

  public DATABASE DATABASE;

  public static class DATABASE {

    public DatabaseLibrary STORAGE_TYPE;

    public String HOSTNAME;
    public String USER;
    public String PASSWORD;
    // 网络数据库名称
    public String DATABASE;
    public String CONNECTION_PARAMETERS;
  }
}

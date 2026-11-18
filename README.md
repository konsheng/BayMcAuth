# BayMcAuth

BayMcAuth  使用 [LimboAPI](https://github.com/Elytrium/LimboAPI) 提供虚拟登录服务器, 是一款现代化的高性能登录插件

## 插件功能

- 支持 SQLite, H2, MySQL, MariaDB 和 PostgreSQL
- 支持 [Geyser](https://wiki.geysermc.org) 和 [Floodgate](https://wiki.geysermc.org/floodgate/)
- 支持 [Floodgate](https://wiki.geysermc.org/floodgate/), 正版和离线账号混合认证
- 使用 [BCrypt](https://en.wikipedia.org/wiki/Bcrypt) [哈希算法](https://en.wikipedia.org/wiki/Cryptographic_hash_function) 保存密码
- 注册和玩家改密支持弱密码黑名单检查
- 支持基于 [TOTP](https://en.wikipedia.org/wiki/Time-based_one-time_password) 的[双重验证](https://en.wikipedia.org/wiki/Help:Two-factor_authentication)
- 支持使用[数据库](https://en.wikipedia.org/wiki/Database)中保存的 [UUID](https://minecraft.wiki/w/Universally_unique_identifier)
- 支持自定义插件消息, 您可以修改消息内容或按配置关闭相关提示
- 支持加载 [MCEdit](https://www.mcedit.net/about.html) 格式的世界文件

## 构建环境

- 构建 JDK: 25, Gradle 包装器版本: 9.9.1
- Java 工具链和编译目标均为 25, 编译使用 `--release 25`, 运行插件需要 Java 25 或更高版本
- 使用 `./gradlew clean build` 构建, Windows 使用 `.\gradlew.bat clean build`,
- LimboAPI 继续作为独立外部依赖, 不包含在插件 Jar 中

## 消息格式

- 消息格式为 MiniMessage
- Java 扩展接口 `BayMcAuth.getSerializer()` 现在返回 `MiniMessage`, 使用此接口的扩展需要重新编译

| 占位符 | 含义与适用消息 |
| --- | --- |
| `<prefix>` | 消息前缀, 所有消息均可使用 |
| `<newline>` / `<br>` | 换行, MiniMessage 内置标签 |
| `<player>` | 强制注册, 登录, 注销和改密结果中的目标玩家名 |
| `<expected_name>` / `<current_name>` | 玩家名称大小写错误提示中的正确名称和当前输入名称 |
| `<attempts>` | 登录提示和密码错误提示中的剩余次数, 登录标题与副标题中的初始次数 |
| `<seconds>` | 首领栏中的剩余登录秒数 |
| `<password>` | 管理员改密后发送给目标玩家的新密码 |
| `<secret>` | 双重验证密钥提示中的密钥 |
| `<recovery_codes>` | 双重验证恢复码提示中的恢复码列表 |
| `<reason>` | `messages.reload-failed` 中的配置重载失败原因 |

## 命令与权限

主命令为 `/baymcauth`, 别名为 `/auth`, `/ba`, `/bauth`, 使用 `/baymcauth reload` 可重新加载配置

### 玩家命令

- ***baymcauth.commands.destroysession* | /destroysession** - 注销当前账号的认证会话
- ***baymcauth.commands.premium* | /license 或 /premium** - 将账号切换为正版账号
- ***baymcauth.commands.unregister* | /unregister** - 注销账号
- ***baymcauth.commands.changepassword* | /changepassword** - 修改账号密码
- ***baymcauth.commands.totp* | /totp** - 管理双重验证
- ***baymcauth.commands.***\* - 授予全部玩家命令权限

### 管理员命令

- ***baymcauth.admin.forceunregister* | /forceunregister** - 强制注销指定账号
- ***baymcauth.admin.forcechangepassword* | /forcechangepassword** - 强制修改指定账号的密码
- ***baymcauth.admin.forceregister* | /forceregister** - 强制注册指定账号
- ***baymcauth.admin.forcelogin* | /forcelogin** - 强制通过指定玩家的认证
- ***baymcauth.admin.reload* | /baymcauth reload** - 重新加载插件配置
- ***baymcauth.admin.***\* - 授予全部管理员命令权限

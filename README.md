# BayMcAuth

BayMcAuth 是一个面向 Velocity + Paper/Folia 网络的统一认证插件, 用于在代理端完成身份分流, 在后端服务器完成注册、登录、TOTP、邀请码、预留名、锁定和审计等认证流程。

同一个 Jar 同时放入 Velocity 和 Paper/Folia 的 `plugins` 目录即可使用。

## 🔂 功能特性

- 支持 Velocity 代理端和 Paper/Folia 后端同时加载
- 支持正版玩家自动登录, 也可配置正版玩家启用密码登录
- 支持离线前后缀名、普通离线名、中文名的注册和登录
- 支持邀请码注册、邀请码撤销、离线名预留、用户锁定和 TOTP 重置
- 支持 TOTP 二次验证, 只显示手动 secret, 不生成二维码
- 支持登录保护、登录提示、首次加入提示和高风险操作确认
- 支持 MySQL 持久化、Redis 会话缓存和本地会话降级
- 支持审计日志输出到控制台、文件和数据库
- 支持配置和语言文件缺键自动补全, 保留用户已有配置值

## 🌋 运行环境

- Java 25
- Gradle Wrapper 9.5.1
- Paper API 26.1.2 build 69 stable
- Velocity API 3.5.0-SNAPSHOT
- MySQL 8.0 或兼容版本
- Redis 可选, 用于跨端会话缓存

## 🖥️ 服务端支持

- Velocity 3.5.0-SNAPSHOT
- Paper 1.21 系列
- Folia 1.21 系列

不支持 Bukkit、Spigot、BungeeCord 单独加载。

## 📦 安装

1. 构建或下载 `BayMcAuth-1.0.0-SNAPSHOT.jar`
2. 将同一个 Jar 放入 Velocity 的 `plugins` 目录
3. 将同一个 Jar 放入所有 Paper/Folia 后端服务器的 `plugins` 目录
4. 启动 Velocity 和 Paper/Folia, 生成 `plugins/BayMcAuth/config.yml` 和 `plugins/BayMcAuth/lang/zh_CN.yml`
5. 停止服务器, 配置 MySQL、Redis、会话和账号类型策略
6. 再次启动服务器

本地产物路径:
```text
modules/auth-plugin/build/libs/BayMcAuth-1.0.0-SNAPSHOT.jar
```

## ⚙️ 服务器配置

Velocity 需要开启正版入口和 modern 转发:
```toml
online-mode = true
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"

[servers]
lobby = "127.0.0.1:25566"
survival = "127.0.0.1:25567"

try = [
  "lobby"
]
```

Paper/Folia 的 `server.properties`:
```properties
online-mode=false
```

Paper/Folia 的 `spigot.yml`:
```yaml
settings:
  bungeecord: false
```

Paper/Folia 的 `config/paper-global.yml`:
```yaml
proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: "这里填写 Velocity forwarding.secret 的内容"
```

Velocity 和所有 Paper/Folia 后端都需要配置 `plugins/BayMcAuth/config.yml`。MySQL 配置必须一致, Redis 可按需要启用:
```yaml
database:
  type: mysql
  mysql:
    host: 127.0.0.1
    port: 3306
    database: baymc
    username: root
    password: password

redis:
  enabled: true
  host: 127.0.0.1
  port: 6379

session:
  mode: redis

login:
  allow-premium-auto-login: true

invite:
  require-for-offline-register: true
```

核心配置项:
- `database.*`: MySQL 连接和连接池配置
- `redis.*`: Redis 会话缓存配置
- `session.mode`: 会话模式
- `login.allow-premium-auto-login`: 正版玩家是否自动登录
- `invite.require-for-offline-register`: 离线账号注册是否需要邀请码
- `account-types.*`: 不同账号类型的注册和登录策略
- `settings.debug`: 是否在可降级错误中打印完整异常堆栈

## ⌨️ 命令

主命令:
- `/baymcauth`
- `/auth`

参数约定:
- `<>` 必填
- `[]` 选填

命令列表:
- **`/baymcauth`**<br>
  权限：`baymcauth.help`<br>
  显示插件帮助
- **`/baymcauth help`**<br>
  权限：`baymcauth.help`<br>
  显示插件帮助
- **`/baymcauth status`**<br>
  权限：`baymcauth.status`<br>
  查看 Paper/Folia 端数据库状态
- **`/baymcauth register <密码> <确认密码> <邀请码>`**<br>
  权限：`baymcauth.register`<br>
  注册当前账号
- **`/register <密码> <确认密码> <邀请码>`**<br>
  权限：`baymcauth.register`<br>
  注册当前账号
- **`/baymcauth login <密码> [TOTP 验证码]`**<br>
  权限：`baymcauth.login`<br>
  登录当前账号
- **`/login <密码> [TOTP 验证码]`**<br>
  权限：`baymcauth.login`<br>
  登录当前账号
- **`/baymcauth logout`**<br>
  权限：`baymcauth.logout`<br>
  登出当前账号
- **`/logout`**<br>
  权限：`baymcauth.logout`<br>
  登出当前账号
- **`/baymcauth 2fa setup`**<br>
  权限：`baymcauth.2fa.setup`<br>
  生成 TOTP 手动 secret
- **`/baymcauth 2fa confirm <验证码>`**<br>
  权限：`baymcauth.2fa.confirm`<br>
  确认并启用 TOTP
- **`/baymcauth 2fa code <验证码>`**<br>
  权限：`baymcauth.2fa.code`<br>
  提交 TOTP 验证码
- **`/baymcauth 2fa disable <密码> <验证码>`**<br>
  权限：`baymcauth.2fa.disable`<br>
  关闭 TOTP
- **`/baymcauth 2fa status`**<br>
  权限：`baymcauth.2fa.status`<br>
  查看当前账号 TOTP 状态
- **`/2fa setup`**<br>
  权限：`baymcauth.2fa.setup`<br>
  生成 TOTP 手动 secret
- **`/2fa confirm <验证码>`**<br>
  权限：`baymcauth.2fa.confirm`<br>
  确认并启用 TOTP
- **`/2fa code <验证码>`**<br>
  权限：`baymcauth.2fa.code`<br>
  提交 TOTP 验证码
- **`/2fa disable <密码> <验证码>`**<br>
  权限：`baymcauth.2fa.disable`<br>
  关闭 TOTP
- **`/2fa status`**<br>
  权限：`baymcauth.2fa.status`<br>
  查看当前账号 TOTP 状态
- **`/baymcauth resetpassword <新密码> <确认密码> <TOTP 验证码>`**<br>
  权限：`baymcauth.resetpassword`<br>
  通过 TOTP 重置当前账号密码
- **`/resetpassword <新密码> <确认密码> <TOTP 验证码>`**<br>
  权限：`baymcauth.resetpassword`<br>
  通过 TOTP 重置当前账号密码
- **`/baymcauth password enable <密码> <确认密码>`**<br>
  权限：`baymcauth.password.enable`<br>
  为 PREMIUM 账号启用密码登录
- **`/baymcauth password disable <密码>`**<br>
  权限：`baymcauth.password.disable`<br>
  关闭当前账号密码登录
- **`/baymcauth password change <旧密码> <新密码> <确认密码>`**<br>
  权限：`baymcauth.password.change`<br>
  修改当前账号密码
- **`/baymcauth invite create [数量] [有效天数]`**<br>
  权限：`baymcauth.invite.create`<br>
  创建邀请码
- **`/baymcauth invite list`**<br>
  权限：`baymcauth.invite.list`<br>
  列出邀请码
- **`/baymcauth invite export`**<br>
  权限：`baymcauth.invite.export`<br>
  导出邀请码列表到聊天输出
- **`/baymcauth invite info <邀请码>`**<br>
  权限：`baymcauth.invite.info`<br>
  查看邀请码详情
- **`/baymcauth invite revoke <邀请码>`**<br>
  权限：`baymcauth.invite.revoke`<br>
  撤销邀请码
- **`/baymcauth reserve offline <玩家名>`**<br>
  权限：`baymcauth.reserve.offline`<br>
  预留无前后缀离线名
- **`/baymcauth reserve info <玩家名>`**<br>
  权限：`baymcauth.reserve.info`<br>
  查看预留名详情
- **`/baymcauth reserve list`**<br>
  权限：`baymcauth.reserve.list`<br>
  列出预留名
- **`/baymcauth reserve revoke <玩家名>`**<br>
  权限：`baymcauth.reserve.revoke`<br>
  撤销预留名
- **`/baymcauth user info <玩家名>`**<br>
  权限：`baymcauth.user.info`<br>
  查看用户基础信息
- **`/baymcauth user history <玩家名>`**<br>
  权限：`baymcauth.user.history`<br>
  查看用户历史信息
- **`/baymcauth lock <玩家名> <原因>`**<br>
  权限：`baymcauth.lock`<br>
  锁定用户
- **`/baymcauth unlock <玩家名>`**<br>
  权限：`baymcauth.unlock`<br>
  解锁用户
- **`/baymcauth reset2fa <玩家名>`**<br>
  权限：`baymcauth.reset2fa`<br>
  重置玩家 TOTP
- **`/baymcauth confirm`**<br>
  权限：`baymcauth.confirm`<br>
  确认当前执行者名下有效期内的高风险操作
- **`/baymcauth reload`**<br>
  权限：`baymcauth.reload`<br>
  重载 Paper/Folia 端配置和语言文件
- **`/baymcauth velocity help`**<br>
  权限：`baymcauth.velocity.help`<br>
  显示 Velocity 端命令帮助
- **`/baymcauth velocity status`**<br>
  权限：`baymcauth.velocity.status`<br>
  查看 Velocity 端数据库状态
- **`/baymcauth velocity reload`**<br>
  权限：`baymcauth.velocity.reload`<br>
  重载 Velocity 端配置和语言文件
- **`/baymcauth velocity affix status`**<br>
  权限：`baymcauth.velocity.affix.status`<br>
  查看 Velocity 端离线名前后缀模式
- **`/baymcauth velocity affix reload`**<br>
  权限：`baymcauth.velocity.affix.reload`<br>
  显示 Velocity 端离线名前后缀模式

## 🔐 权限

- **`baymcauth.help`**<br>
  默认：`true`<br>
  显示插件帮助
- **`baymcauth.status`**<br>
  默认：`true`<br>
  查看 Paper/Folia 端状态
- **`baymcauth.register`**<br>
  默认：`true`<br>
  注册当前账号
- **`baymcauth.login`**<br>
  默认：`true`<br>
  登录当前账号
- **`baymcauth.logout`**<br>
  默认：`true`<br>
  登出当前账号
- **`baymcauth.resetpassword`**<br>
  默认：`true`<br>
  通过 TOTP 重置当前账号密码
- **`baymcauth.confirm`**<br>
  默认：`true`<br>
  确认当前执行者名下的高风险操作
- **`baymcauth.password.enable`**<br>
  默认：`true`<br>
  启用密码登录
- **`baymcauth.password.disable`**<br>
  默认：`true`<br>
  关闭密码登录
- **`baymcauth.password.change`**<br>
  默认：`true`<br>
  修改密码
- **`baymcauth.2fa.setup`**<br>
  默认：`true`<br>
  创建 TOTP pending secret
- **`baymcauth.2fa.confirm`**<br>
  默认：`true`<br>
  确认并启用 TOTP
- **`baymcauth.2fa.code`**<br>
  默认：`true`<br>
  提交 TOTP 验证码
- **`baymcauth.2fa.disable`**<br>
  默认：`true`<br>
  关闭 TOTP
- **`baymcauth.2fa.status`**<br>
  默认：`true`<br>
  查看当前账号 TOTP 状态
- **`baymcauth.reload`**<br>
  默认：`op`<br>
  重载 Paper/Folia 端配置和语言文件
- **`baymcauth.invite.create`**<br>
  默认：`op`<br>
  创建邀请码
- **`baymcauth.invite.list`**<br>
  默认：`op`<br>
  列出邀请码
- **`baymcauth.invite.export`**<br>
  默认：`op`<br>
  导出邀请码
- **`baymcauth.invite.info`**<br>
  默认：`op`<br>
  查看邀请码详情
- **`baymcauth.invite.revoke`**<br>
  默认：`op`<br>
  撤销邀请码
- **`baymcauth.reserve.offline`**<br>
  默认：`op`<br>
  预留无前后缀离线名
- **`baymcauth.reserve.info`**<br>
  默认：`op`<br>
  查看预留名详情
- **`baymcauth.reserve.list`**<br>
  默认：`op`<br>
  列出预留名
- **`baymcauth.reserve.revoke`**<br>
  默认：`op`<br>
  撤销预留名
- **`baymcauth.user.info`**<br>
  默认：`op`<br>
  查看用户基础信息
- **`baymcauth.user.history`**<br>
  默认：`op`<br>
  查看用户历史信息
- **`baymcauth.lock`**<br>
  默认：`op`<br>
  锁定用户
- **`baymcauth.unlock`**<br>
  默认：`op`<br>
  解锁用户
- **`baymcauth.reset2fa`**<br>
  默认：`op`<br>
  重置玩家 TOTP
- **`baymcauth.velocity.help`**<br>
  默认：`op`<br>
  显示 Velocity 端命令帮助
- **`baymcauth.velocity.status`**<br>
  默认：`op`<br>
  查看 Velocity 端数据库状态
- **`baymcauth.velocity.reload`**<br>
  默认：`op`<br>
  重载 Velocity 端配置和语言文件
- **`baymcauth.velocity.affix.status`**<br>
  默认：`op`<br>
  查看 Velocity 端离线名前后缀模式
- **`baymcauth.velocity.affix.reload`**<br>
  默认：`op`<br>
  显示 Velocity 端离线名前后缀模式

## 🛡️ 数据安全

- TOTP secret、TOTP 验证码和密码不会写入审计日志或控制台
- TOTP 只显示手动 secret, 不生成二维码
- 高风险操作会先登记待确认操作, 再由 `/baymcauth confirm` 执行
- `/baymcauth confirm` 只对当前执行者和有效期内操作生效
- 数据库初始化失败时, 插件会进入降级状态, 避免继续写入不完整数据
- Redis 不可用时不会踢出已在线玩家, 新登录成功后会写入本地会话
- 密码使用 BCrypt 存储
- 配置和语言文件缺键会从 Jar 内默认文件自动补全

## 🧾 审计

审计用于记录认证和管理员敏感操作, 方便后续追溯和排查问题。

- 记录注册、登录、登出、身份分流、邀请码、预留名、用户锁定和 TOTP 管理等操作
- 可同步输出到控制台
- 可写入 `plugins/BayMcAuth/logs/`
- 可写入数据库
- 默认隐藏密码、TOTP secret 和 TOTP 验证码

## 🛠️ 构建

Windows:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
.\gradlew.bat clean test shadowJar
```

Linux:
```bash
./gradlew clean test shadowJar
```

本地产物:
```text
modules/auth-plugin/build/libs/BayMcAuth-1.0.0-SNAPSHOT.jar
```

## 📄 许可证

本项目当前未声明许可证。

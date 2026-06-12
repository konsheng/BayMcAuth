# BayMcAuth

开发约束:

- Java 使用 JDK 25
- Gradle Wrapper 使用 9.5.1
- Paper API 和 Velocity API 只使用 compileOnly
- 最终 Jar 由 `modules/auth-plugin:shadowJar` 生成
- 配置键使用短横线, 数据库字段使用下划线
- 玩家可见文本必须来自 `lang/zh_CN.yml`
- 中文文本使用英文标点, 逗号后加空格, 不使用句号结尾

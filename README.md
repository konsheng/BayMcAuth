# BayMcAuth

BayMcAuth 是面向 Velocity + Paper/Folia 网络的统一认证插件

## 构建

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
.\gradlew.bat clean test shadowJar
```

最终文件:

```text
modules/auth-plugin/build/libs/BayMcAuth-1.0.0.jar
```

同一个 Jar 放入 Velocity 和 Paper/Folia 的 `plugins` 目录

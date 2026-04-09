# 构建与环境说明

更新时间：2026-04-09

## 1. 基础环境

- Android Studio 或可用的 Android Gradle 环境
- JDK 17 及以上
- Android SDK
- Windows 环境优先使用 `gradlew.bat`
- Windows 用户目录如果包含中文或其他非 ASCII 字符，建议显式设置纯英文路径的 `GRADLE_USER_HOME`

## 2. 当前项目参数

- `compileSdk = 34`
- `targetSdk = 34`
- `minSdk = 24`
- Kotlin Android 项目
- 单模块应用：`app`

## 3. 本地配置

- Android SDK 通过 `local.properties` 中的 `sdk.dir` 指定
- 运行 Gradle 前需要确保 `JAVA_HOME` 可用
- 如需把 Gradle 缓存放到非系统盘，可额外设置 `GRADLE_USER_HOME`
- 当前工作区在 `JAVA_HOME=D:\android\jbr`、`GRADLE_USER_HOME=D:\gradle-home` 下已验证可运行

## 4. 当前已验证命令

```powershell
$env:JAVA_HOME="D:\android\jbr"
$env:GRADLE_USER_HOME="D:\gradle-home"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon testDebugUnitTest
.\gradlew.bat --no-daemon assembleDebug
.\gradlew.bat --no-daemon lintDebug
```

- 如果不设置纯英文路径的 `GRADLE_USER_HOME`，当前 Windows 环境下的 `testDebugUnitTest` 可能出现 `GradleWorkerMain` 启动失败
- `lintDebug` 也建议在同样的环境变量下执行

## 5. 常用命令

### 5.1 构建 Debug 包

```powershell
.\gradlew.bat :app:assembleDebug
```

### 5.2 运行 Lint

```powershell
.\gradlew.bat :app:lintDebug
```

### 5.3 输出位置

```text
app/build/outputs/apk/debug/
```

## 6. 当前真实状态

- 当前 `assembleDebug` 已验证可通过
- 当前 `testDebugUnitTest` 已验证可通过，但依赖正确的 `JAVA_HOME` 和纯英文路径的 `GRADLE_USER_HOME`
- 当前 `lintDebug` 已验证可通过
- 当前 `lintDebug` 仍保留 46 个非阻断警告，主要是无用资源、旧 API 守卫和实验性自动化代码问题
- 当前命令行构建会提示 SDK XML 版本告警，说明本机 Android Studio 与命令行工具版本仍需后续统一

## 7. 推荐协作方式

- 目录调整先更新 `docs/development/project-structure.md`
- 功能范围变化先更新 `docs/product/`
- 规划类内容统一更新到 `docs/roadmap/`

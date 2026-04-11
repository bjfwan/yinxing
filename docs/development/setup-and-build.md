# 构建与环境说明

更新时间：2026-04-12

## 1. 基础环境

- Android Studio 或可用的 Android Gradle 环境
- JDK 17 及以上
- Android SDK
- Windows 环境优先使用 `gradlew.bat`
- 建议显式设置 `JAVA_HOME`

## 2. 当前项目参数

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 24`
- 主应用模块：`app`
- Baseline Profile / Macrobenchmark 模块：`benchmark`
- 仪器测试运行器：`androidx.test.runner.AndroidJUnitRunner`

## 3. 本地配置

- Android SDK 通过 `local.properties` 中的 `sdk.dir` 指定
- 当前工作区 `sdk.dir = D:\androidsdk`
- 运行 Gradle 前需要确保 `JAVA_HOME` 可用，或 `java` 已在 `PATH`
- 当前工作区已在 `JAVA_HOME=D:\android\jbr` 条件下直接执行 `.\gradlew.bat` 验证可运行
- 如需把 Gradle 缓存放到非系统盘，可额外设置 `GRADLE_USER_HOME`
- 当前 `gradlew.bat` 在未显式设置 `GRADLE_USER_HOME` 时，会默认回退到工作区下的 `.gradle-user-home`
- 当前 `gradle.properties` 已移除 `Windows-ROOT` trust store 强制配置，避免 JBR 下 SSL 依赖下载失败

## 4. 当前已验证命令

```powershell
$env:JAVA_HOME="D:\android\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

## 5. 常用命令

### 5.1 构建 Debug 包

```powershell
.\gradlew.bat :app:assembleDebug
```

### 5.2 运行单元测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

### 5.3 编译仪器测试 APK

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
```

### 5.4 执行设备级仪器测试

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

### 5.5 运行 Lint

```powershell
.\gradlew.bat :app:lintDebug
```

### 5.6 输出位置

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/androidTest/debug/
```

## 6. 当前真实状态

- 当前已于 2026-04-12 验证 `:app:assembleDebugAndroidTest` 可通过
- 当前已于 2026-04-12 验证 `:app:testDebugUnitTest` 可通过
- 当前已于 2026-04-12 验证 `:app:assembleDebug` 可通过
- 当前已于 2026-04-12 验证 `:app:lintDebug` 可通过
- 当前 `lintDebug` 结果为 `No issues found.`
- 当前未检测到已连接设备，因此未执行 `:app:connectedDebugAndroidTest`

## 7. 推荐协作方式

- 目录调整先更新 `docs/development/project-structure.md`
- 架构边界变化先更新 `docs/architecture/current-architecture.md`
- 测试覆盖变化同步更新到 `docs/testing/`
- 发布门禁变化同步更新到 `docs/release/`

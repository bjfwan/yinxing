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
- 主应用仪器测试运行器：`androidx.test.runner.AndroidJUnitRunner`
- `benchmark` 模块运行器：`androidx.benchmark.junit4.AndroidBenchmarkRunner`


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

### 5.4 检查设备连接

如果 `adb` 已在 `PATH` 中，可直接执行：

```powershell
adb devices
```

如果当前终端找不到 `adb`，可直接使用 SDK 内的完整路径：

```powershell
& 'D:\androidsdk\platform-tools\adb.exe' devices
```

期望输出示例：

```text
List of devices attached
10AD5H082S000H5    device
```

常见状态说明：

- `device`：已连接，可执行 `connectedDebugAndroidTest`
- `unauthorized`：设备未授权当前电脑，需要在手机上确认 USB 调试授权
- `offline`：设备已被识别但连接异常，通常需要重插数据线或重启 `adb`
- 无设备：说明当前没有真机或模拟器在线

### 5.5 执行设备级仪器测试

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

如果设备在线但执行失败，优先检查：

- 设备屏幕是否点亮并解锁
- 设备是否弹出了安装确认或安全授权，并被手动拒绝
- 当前设备是否允许通过 USB / `adb` 安装调试应用

### 5.5.1 运行 Benchmark 分层探针

当 `:benchmark:collectNonMinifiedReleaseBaselineProfile` 看起来卡住时，先不要直接重跑整套基线采集，优先按下面顺序执行分层探针：

```powershell
.\gradlew.bat :benchmark:connectedNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bajianfeng.launcher.benchmark.UiAutomationProbe#launcherUiSmoke
```

```powershell
.\gradlew.bat :benchmark:connectedNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bajianfeng.launcher.benchmark.MacrobenchmarkProbe#coldStartupProbe
```

```powershell
.\gradlew.bat :benchmark:connectedNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bajianfeng.launcher.benchmark.BaselineProfileFrameworkProbe#collectProbe
```

结果解读建议：

- `UiAutomationProbe` 失败：优先看设备是否解锁、默认桌面状态、入口文案/选择器是否变化
- `MacrobenchmarkProbe` 失败：说明问题已在 Macrobenchmark 基础设施层，不必继续跑 `Baseline Profile`
- `BaselineProfileFrameworkProbe` 失败或长时间只打印 watchdog 心跳：大概率卡在 `BaselineProfileRule.collect(...)` 框架层或 ROM 兼容性
- 三个探针都通过后，再执行完整的 `:benchmark:collectNonMinifiedReleaseBaselineProfile`

### 5.6 运行 Lint

```powershell
.\gradlew.bat :app:lintDebug
```

### 5.7 输出位置



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
- 当前已通过 `D:\androidsdk\platform-tools\adb.exe devices` 检测到在线设备 `10AD5H082S000H5`
- 当前已于 2026-04-12 在真机 `10AD5H082S000H5` 上执行 `:app:connectedDebugAndroidTest` 并通过


## 7. 推荐协作方式

- 目录调整先更新 `docs/development/project-structure.md`
- 架构边界变化先更新 `docs/architecture/current-architecture.md`
- 测试覆盖变化同步更新到 `docs/testing/`
- 发布门禁变化同步更新到 `docs/release/`

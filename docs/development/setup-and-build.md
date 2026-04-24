# 构建与环境说明

更新时间：2026-04-25

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

```powershell
adb devices
# 或使用完整路径
& 'D:\androidsdk\platform-tools\adb.exe' devices
```

常见状态说明：

- `device`：已连接，可执行 `connectedDebugAndroidTest`
- `unauthorized`：设备未授权当前电脑，需要在手机上确认 USB 调试授权
- `offline`：设备已被识别但连接异常，通常需要重插数据线或重启 `adb`

### 5.5 执行设备级仪器测试

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

### 5.6 运行 Benchmark

```powershell
# 完整冷启动基准（需真机或已启用 suppressErrors 的模拟器）
.\gradlew.bat :benchmark:connectedAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.yinxing.launcher.benchmark.HomeStartupBenchmark" `
  "-x" "uploadCrashlyticsMappingFileBenchmarkRelease"
```

#### Benchmark 分层探针（当采集卡住时按此顺序执行）

```powershell
# 第一步：确认设备 UI 可操作
.\gradlew.bat :benchmark:connectedNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yinxing.launcher.benchmark.UiAutomationProbe#launcherUiSmoke

# 第二步：确认 MacrobenchmarkRule 可进入
.\gradlew.bat :benchmark:connectedNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yinxing.launcher.benchmark.MacrobenchmarkProbe#coldStartupProbe

# 第三步：确认 BaselineProfileRule 可进入 lambda
.\gradlew.bat :benchmark:connectedNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yinxing.launcher.benchmark.BaselineProfileFrameworkProbe#collectProbe

# 最后：完整基线采集
.\gradlew.bat :benchmark:collectNonMinifiedReleaseBaselineProfile
```

### 5.7 运行 Lint

```powershell
.\gradlew.bat :app:lintDebug
```

### 5.8 构建 Release 包

```powershell
.\gradlew.bat :app:assembleRelease -x uploadCrashlyticsMappingFileReleaseRelease
```

### 5.9 输出位置

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
app/build/outputs/apk/androidTest/debug/
benchmark/build/outputs/connected_android_test_additional_output/  （benchmark JSON 结果）
```

## 6. Firebase Crashlytics 配置

项目已接入 Firebase Crashlytics，用于自动捕获崩溃日志。

- Firebase 项目：`launcher-d4690a2f`
- 应用包名：`com.yinxing.launcher`
- `google-services.json` 需放在 `app/` 目录下（已加入 `.gitignore`，不提交到仓库）
- 无需任何额外代码，App 崩溃后下次启动自动上传日志
- 查看崩溃报告：[console.firebase.google.com](https://console.firebase.google.com) → 项目 → Crashlytics

> 注意：从 GitHub clone 项目后需自行在 Firebase Console 下载 `google-services.json` 并放到 `app/` 目录，否则构建会失败。

## 7. 推荐协作方式

- 目录调整先更新 `docs/development/project-structure.md`
- 架构边界变化先更新 `docs/architecture/current-architecture.md`
- 测试覆盖变化同步更新到 `docs/testing/`
- 发布门禁变化同步更新到 `docs/release/`

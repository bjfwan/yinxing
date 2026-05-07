# 构建与环境说明

更新时间：2026-05-07

## 1. 基础环境

- Android Studio 或可用的 Android Gradle 环境
- JDK 17 及以上
- Android SDK
- Windows 环境优先使用 `build.bat`
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
- 当前仓库提供 `build.bat`，会在 Windows 下设置 `JAVA_HOME=D:\Android\jbr` 后调用 `gradlew.bat`
- 如需把 Gradle 缓存放到非系统盘，可额外设置 `GRADLE_USER_HOME`
- 当前 `gradlew.bat` 在未显式设置 `GRADLE_USER_HOME` 时，会默认回退到工作区下的 `.gradle-user-home`
- 当前 `gradle.properties` 已移除 `Windows-ROOT` trust store 强制配置，避免 JBR 下 SSL 依赖下载失败

## 4. 当前已验证命令

```powershell
.\build.bat :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

## 5. 常用命令

### 5.1 构建 Debug 包

```powershell
.\build.bat :app:assembleDebug
```

### 5.2 运行单元测试

```powershell
.\build.bat :app:testDebugUnitTest
```

### 5.3 编译仪器测试 APK

```powershell
.\build.bat :app:assembleDebugAndroidTest
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
.\build.bat :app:connectedDebugAndroidTest
```

### 5.6 运行 Benchmark

```powershell
# 完整冷启动基准（需真机或已启用 suppressErrors 的模拟器）
.\build.bat :benchmark:connectedAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.yinxing.launcher.benchmark.HomeStartupBenchmark"
```

#### Benchmark 分层探针（当采集卡住时按此顺序执行）

```powershell
# 第一步：确认设备 UI 可操作
.\build.bat :benchmark:connectedNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yinxing.launcher.benchmark.UiAutomationProbe#launcherUiSmoke

# 第二步：确认 MacrobenchmarkRule 可进入
.\build.bat :benchmark:connectedNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yinxing.launcher.benchmark.MacrobenchmarkProbe#coldStartupProbe

# 第三步：确认 BaselineProfileRule 可进入 lambda
.\build.bat :benchmark:connectedNonMinifiedReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yinxing.launcher.benchmark.BaselineProfileFrameworkProbe#collectProbe

# 最后：完整基线采集
.\build.bat :benchmark:collectNonMinifiedReleaseBaselineProfile
```

### 5.7 运行 Lint

```powershell
.\build.bat :app:lintDebug
```

### 5.8 构建 Release 包

```powershell
.\build.bat :app:assembleRelease
```

### 5.9 输出位置

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
app/build/outputs/apk/androidTest/debug/
benchmark/build/outputs/connected_android_test_additional_output/  （benchmark JSON 结果）
```

### 5.10 发布页 APK 同步

- Release 输出文件为 `app/build/outputs/apk/release/app-release.apk`
- 下载页主文件为 `docs/app-release.apk`
- 每次正式发布时，用本次 Release APK 覆盖 `docs/app-release.apk`
- GitHub Release 上传同一个 `app-release.apk`
- Cloudflare 域名托管的下载页使用 `docs/index.html` 和 `docs/app-release.apk`
- 版本号、包体大小变化时，同步更新 `README.md`、`docs/index.html`、`docs/update.json` 和 `docs/release/release-checklist.md`

## 6. Lobster 日志与性能上报配置

项目使用自有 Lobster 接口上报日志、诊断与性能指标，不依赖 Firebase。

- `LOBSTER_UPLOAD_URL`：日志上报地址
- `LOBSTER_UPLOAD_TOKEN`：日志上报 token
- 上述字段从 `local.properties` 注入到 `BuildConfig`
- 未配置时应用仍可构建和运行，只是不进行线上日志上报
- Release 包会关闭普通 logcat 输出，诊断数据走 Lobster 上报链路

## 7. 推荐协作方式

- 目录调整先更新 `docs/development/project-structure.md`
- 架构边界变化先更新 `docs/architecture/current-architecture.md`
- 测试覆盖变化同步更新到 `docs/testing/`
- 发布门禁变化同步更新到 `docs/release/`

# 构建与环境说明

更新时间：2026-04-09

## 1. 基础环境

- Android Studio 或可用的 Android Gradle 环境
- JDK 17 及以上
- Android SDK
- Windows 环境优先使用 `gradlew.bat`

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

## 4. 常用命令

### 4.1 构建 Debug 包

```powershell
.\gradlew.bat :app:assembleDebug
```

### 4.2 运行 Lint

```powershell
.\gradlew.bat :app:lintDebug
```

### 4.3 输出位置

```text
app/build/outputs/apk/debug/
```

## 5. 当前真实状态

- 当前可以把 `assembleDebug` 作为基础构建检查
- 当前还没有稳定的自动化测试门禁
- 当前 `lintDebug` 仍有待清理项，适合作为问题扫描，不适合作为发布前唯一门禁

## 6. 推荐协作方式

- 目录调整先更新 `docs/development/project-structure.md`
- 功能范围变化先更新 `docs/product/`
- 规划类内容统一更新到 `docs/roadmap/`

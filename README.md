# OldLauncher

OldLauncher 是一个面向老年用户的 Android 本地桌面应用，当前主线目标是把桌面主页、常用应用入口、电话联系人与基础设置收拢到一个更简单、更稳定的启动器里。

## 当前项目范围

- 桌面主页与默认 Launcher 入口
- 应用管理与桌面应用选择
- 电话联系人查看、添加、编辑、删除、拨号
- 微信视频联系人页、本地联系人管理与实验性发起入口
- 低性能模式与基础系统设置入口

## 当前工程结构

- `app`：主应用模块
- `benchmark`：Baseline Profile 与 Macrobenchmark 模块
- `docs`：产品、架构、开发、测试与发布文档

## 当前测试基线

- 单元测试：`.\gradlew.bat :app:testDebugUnitTest`
- Debug 构建：`.\gradlew.bat :app:assembleDebug`
- 仪器测试编译：`.\gradlew.bat :app:assembleDebugAndroidTest`
- Lint：`.\gradlew.bat :app:lintDebug`
- 设备级执行：连接设备或模拟器后运行 `.\gradlew.bat :app:connectedDebugAndroidTest`

## 当前不作为已交付能力

- 微信自动化拨号稳定能力
- 微信或系统电话自动接听
- 通知监听、远程协助、云同步

## 文档入口

- [文档总览](docs/README.md)
- [产品需求](docs/product/product-requirements.md)
- [功能状态](docs/product/current-feature-status.md)
- [当前架构](docs/architecture/current-architecture.md)
- [项目结构](docs/development/project-structure.md)
- [构建说明](docs/development/setup-and-build.md)
- [Git 协作规范](docs/development/git-workflow.md)
- [测试策略](docs/testing/test-strategy.md)
- [项目路线图](docs/roadmap/project-roadmap.md)
- [微信视频规划](docs/roadmap/wechat-video-call-plan.md)
- [发布检查清单](docs/release/release-checklist.md)

# 发布检查清单

更新时间：2026-04-25

## 1. 版本信息

- 版本号已更新（当前 `versionCode = 7`，`versionName = "1.4.0"`）
- 变更范围已确认
- 发布说明已准备

## 2. 构建检查

- `JAVA_HOME` 已确认可用
- `GRADLE_USER_HOME` 已确认使用稳定路径
- `:app:assembleDebug` 通过
- `:app:testDebugUnitTest` 通过（108 tests, 0 failed）
- `:app:assembleDebugAndroidTest` 通过
- `:app:lintDebug` 没有阻断错误
- 目标设备可安装
- 关键权限声明已确认

## 3. 功能检查

- 默认桌面入口正常
- 应用管理页正常
- 电话联系人页正常
- 视频联系人页正常
- 设置入口正常
- 主页天气卡片入口已在目标设备验证

## 4. 权限检查

- 联系人权限申请正常
- 拨号权限申请正常
- 视频联系人页涉及的权限提示正常
- 实验性自动化能力若保留入口，需确认权限文案不会误导用户

## 5. 文档检查

- `README.md` 已更新
- `docs/product/` 已更新
- `docs/architecture/` 已更新
- `docs/development/` 已更新
- `docs/testing/` 已更新
- 规划类内容没有误写成已交付能力

## 6. 发布前回归

- 手工冒烟测试已完成
- 如环境有设备或模拟器，已执行 `:app:connectedDebugAndroidTest`
- 当前无设备时，至少确认仪器测试 APK 可以编译
- 关键已知问题已记录
- 非当前版本能力没有被写成"已实现"

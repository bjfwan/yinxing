# 项目路线图

更新时间：2026-04-09

## 1. 当前判断

### 2026-04 现在最该做的事

- 先建立稳定开发基线，不继续扩新功能
- 让构建、测试、Lint、架构文档和真实代码重新一致
- 把桌面主页、应用管理和电话联系人作为当前主干，继续降低微信自动化优先级

## 2. 已验证现状

- 设置 `JAVA_HOME=D:\android\jbr` 和纯英文路径的 `GRADLE_USER_HOME=D:\gradle-home` 后，`testDebugUnitTest`、`assembleDebug`、`lintDebug` 已验证可通过
- `CALL_PHONE` 对应的 `uses-feature` 已补齐，当前没有阻断 `lint` 的错误
- 应用管理页已改为基于 `ACTION_MAIN + CATEGORY_LAUNCHER` 查询应用，并补了 `queries` 声明，Android 11+ 可见性风险已收敛
- 主页天气入口已支持“厂商天气应用优先，浏览器天气页兜底”
- 电话联系人页和视频联系人页已补空状态、权限拒绝和失败提示
- 当前 `lintDebug` 仍有 46 个非阻断警告，主要集中在无用资源、旧 API 兼容写法和实验性自动化代码治理

## 3. 近期优先级

### 已完成的 P0

- 固化可运行的本地环境配置，统一 `JAVA_HOME` 与 `GRADLE_USER_HOME` 约束
- 修复 `AndroidManifest.xml` 中 `CALL_PHONE` 对应的 `uses-feature` 缺失，先清掉 Lint 阻断错误
- 把 `testDebugUnitTest`、`assembleDebug`、`lintDebug` 变成固定验收动作
- 继续修正文档漂移，保持结构文档和代码分层一致

### 已完成的 P1

- 处理 Android 11+ 应用可见性问题，收敛应用管理页的兼容风险
- 清理权限、国际化和可访问性警告，优先处理硬编码文案、图片描述和点击可达性
- 给天气入口补明确兜底策略
- 为电话联系人和视频联系人页补空状态、权限拒绝和失败提示

### 下一阶段 P2

- 继续拆分 `PhoneActivity` 和 `VideoCallActivity` 的职责
- 补齐 `data.home`、联系人编辑和权限包装相关测试
- 清理无用资源、旧 API 兼容告警和低价值实验性依赖

### P3

- 在核心桌面和电话体验稳定后，再决定是否恢复微信自动化投入

## 4. 暂缓事项

- 微信视频自动化稳定化
- 微信视频和系统电话自动接听
- 通知监听相关功能
- 云同步和远程协助

# 项目路线图

更新时间：2026-05-04

## 1. 当前状态（v1.8.0）

- 构建基线稳定：`assembleDebug` / `testDebugUnitTest`（171 tests, 0 failed）/ `lintDebug` 全部通过
- 联系人、天气、桌面应用列表数据操作全面迁移至后台线程，消除主线程 IO 阻塞
- 微信自动化主动拨号（视频）Phase 1 已完成并在真实设备验证通过
- 无障碍服务已合并为单一服务 `SelectToSpeakService`，用户只需授权一次
- 微信来电自动接听代码已清除（技术天花板明确，无障碍事件节流导致连续来电成功率低）
- 包名已从 `com.bajianfeng.launcher` 迁移至 `com.yinxing.launcher`
- 已接入 Firebase Crashlytics，崩溃日志自动上报
- Baseline Profile 已嵌入包体，有 Profile 时 P90 帧时间相比无 Profile 降低约 46%

## 2. 下一阶段

### 系统电话自动接听收尾

- 保留现有 `PHONE_STATE` 广播、来电页与 `TelecomManager.acceptRingingCall()` 主流程
- 继续补不同 ROM / Android 版本的真机兼容性验证
- 评估是否改用 `TelephonyCallback` 或前台服务来增强后台存活与稳定性
- 继续打磨接通后扬声器、倒计时与联系人匹配体验

### 设备级测试补齐

- 在真机上补跑 `connectedDebugAndroidTest`
- 补应用管理页、权限拒绝场景的仪器测试断言

## 3. 暂缓事项

- 骚扰拦截（自动拒接非联系人来电）：功能明确但优先级低，待系统电话接听稳定后评估
- 远程协助、云同步

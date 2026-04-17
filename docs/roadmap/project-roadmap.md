# 项目路线图

更新时间：2026-04-18

## 1. 当前状态

- 构建基线稳定：`assembleDebug` / `testDebugUnitTest` / `lintDebug` 全部通过
- 微信自动化主动拨号（视频）Phase 1 已完成并在真实设备验证通过
- 无障碍服务已合并为单一服务 `SelectToSpeakService`，用户只需授权一次
- 微信来电自动接听代码已清除（技术天花板明确，无障碍事件节流导致连续来电成功率低）

## 2. 下一阶段

### 系统电话自动接听

- 使用 `TelecomManager.acceptRingingCall()` 官方 API，声明 `ANSWER_PHONE_CALLS` 权限
- 来电时监听 `TelephonyCallback`，匹配来电号码与联系人
- 匹配成功时延迟 3–5 秒后自动接听，期间展示来电方姓名和倒计时提示
- 接通后自动切换扬声器（`AudioManager`）
- 监听逻辑放在前台服务中保活

## 3. 暂缓事项

- 骚扰拦截（自动拒接非联系人来电）：功能明确但优先级低，待系统电话接听稳定后评估
- 远程协助、云同步、日志后台上报

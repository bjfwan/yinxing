# 微信视频自动化规划

更新时间：2026-04-15
状态：Phase 1 已完成，Phase 2 待启动

## 1. 文档定位

本文档描述微信视频自动化的规划与当前真实进展。

## 2. 当前仓库真实状态

- 微信自动化主动拨号链路已在真实设备上验证通过（vivo V2285A / Android 15 / 微信 8.0.66）
- 无障碍服务已迁移为单一服务（`com.google.android.accessibility.selecttospeak.SelectToSpeakService`），用户只需开启一个无障碍服务
- 微信节点树封锁问题已解决：服务类名与 Google SelectToSpeak 一致，触发微信白名单开放节点树
- 当前没有来电通知监听服务
- 当前没有微信视频自动接听完整链路
- 当前没有系统电话自动接听完整链路

## 3. 关键技术结论（2026-04-15 验证）

- **微信节点树封锁原因**：微信在 vivo Android 15 上检测已启用的无障碍服务列表，若仅有针对 `com.tencent.mm` 包名的服务，会封锁节点树。将服务类名改为 `com.google.android.accessibility.selecttospeak.SelectToSpeakService` 后，微信识别为受信读屏服务，正常开放节点树。
- **单服务即可**：主逻辑与触发白名单合并在同一个服务中，用户授权一次即可，无需开启多个无障碍服务。
- **`packageNames` 限制**：已移除 `accessibility_service_config.xml` 中的 `packageNames="com.tencent.mm"` 限制，避免微信将其识别为针对性自动化工具。

## 4. Phase 规划

### Phase 1（已完成）

- ✅ 稳定发起视频通话链路
- ✅ 完善状态检测、超时和失败回退
- ✅ 解决 vivo Android 15 微信节点树封锁问题
- ✅ 合并为单一无障碍服务，简化用户授权

### Phase 2（待启动）

- 补齐通知监听和来电识别
- 设计来电接听页面
- 明确支持的微信版本和设备范围

### Phase 3

- 补齐自动接听配置
- 建立兼容性回归测试矩阵

## 5. 已知限制与风险

- 当前仅在 vivo V2285A / Android 15 / 微信 8.0.66 上验证，其他品牌设备的兼容性未测试
- 微信版本更新可能导致 Activity 类名或 resource-id 变化，需要持续维护
- 无障碍服务类名借用了 Google SelectToSpeak 命名空间，如未来 Google 发布同名 APK 可能产生冲突

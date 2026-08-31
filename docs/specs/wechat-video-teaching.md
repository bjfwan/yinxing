# Spec: 微信视频示教

## Objective

在设置中提供“微信视频示教”。用户人工完成一次微信视频流程，银杏把脱敏控件特征绑定到搜索、联系人、更多、音视频入口和视频确认等固定语义动作，形成当前设备、当前微信版本的本地校准资料。能力树负责流程编排和页面校验，不执行录制下来的任意动作序列。

## Tech Stack

- Android/Kotlin，最低 Android 7.0（API 24）
- 现有 `AccessibilityService` 采集微信无障碍事件
- `SharedPreferences` 保存单设备规则
- 现有 Lobster 上报链路发送用户明确同意的数据

## Commands

- 单元测试：`./gradlew.bat :app:testDebugUnitTest`
- 定向测试：`./gradlew.bat :app:testDebugUnitTest --tests "*WeChatTeaching*"`
- 构建：`./gradlew.bat :app:assembleDebug`
- 差异检查：`git diff --check`

## Project Structure

- `app/src/main/java/com/yinxing/launcher/automation/wechat/teaching/`：示教模型、分析、存储与上传数据
- `app/src/main/java/com/google/android/accessibility/selecttospeak/`：事件采集、悬浮操作与语义校准接入
- `app/src/main/java/com/yinxing/launcher/feature/settings/`：设置入口和状态展示
- `app/src/test/`：纯逻辑、存储和设置入口测试

## Code Style

```kotlin
val result = WeChatTeachingAnalyzer.analyze(observations)
if (result is WeChatTeachingResult.Complete) {
    store.save(result.profile)
}
```

- Kotlin 类型用 PascalCase，函数和字段用 camelCase。
- 页面、动作和语义标签使用枚举；需要输入联系人时只保存动态占位符，不保存实际联系人或任意界面文字。
- 内置规则优先；匹配设备和微信版本的语义校准选择器仅作为对应动作的受控兜底。
- 未完成的视频示教只保存为待验证候选，不参与自动拨号；真实进入视频呼叫状态后，本次候选才能进入可执行校准资料。
- 上传数据只供项目维护者汇总、复现和人工适配，不自动下发给其他设备。

## Testing Strategy

- 纯单元测试：动作顺序、完整度评分、隐私过滤、版本兼容、JSON 往返。
- Robolectric：设置页出现入口且状态正确。
- 构建检查：确保资源和 Android 集成可编译。
- 真机验收由用户操作；自动拨号使用语义能力树，通话由用户自行接听或挂断。

## Boundaries

- Always：仅采集 `com.tencent.mm`；只记录脱敏后的控件类名、资源 ID、固定语义标签、相对位置、页面类名和状态指纹；主动采样补足厂商漏事件；进入微信视频呼叫状态才判定成功；最终拨号只点击一次并继续执行现有呼叫状态确认。
- Ask first：新增服务端接口、改变上传服务地址、采集截图或图像特征。
- Never：记录或上传联系人姓名、搜索文字、聊天内容、头像、图片、录音、视频；把采集路线自动分发给其他用户；因学习规则而绕过失败保护。

## Success Criteria

1. 设置中可以准备示教，并显示“未示教 / 已适配 / 需重新示教”。
2. 悬浮按钮支持“开始演示”和“结束演示”，录制期间只接受微信事件。
3. 示教只校准固定语义动作，不录制或执行任意动作序列。
4. 同一设备和微信版本按语义动作保存最新安全控件特征；联系人内容始终使用运行时参数，不落盘。
5. 未打通视频的示教可保留待验证候选，但不能覆盖或进入现有可执行规则。
6. vivo 等设备漏发窗口事件时，使用当前窗口、可见控件和通话状态主动采样补齐，但主动采样结果必须经过状态验证。
7. 只有真实进入微信视频呼叫状态，本次语义候选才升级为可执行校准资料。
8. 执行时先识别当前页面，由能力树选择下一个语义动作；内置定位失败后才使用兼容的校准选择器。
9. 旧版通用路线数据不参与自动拨号；失败仍进入现有诊断和安全停止流程。
10. 上传本次匿名演示数据默认勾选，用户可在开始前取消；成功、失败、未识别的新路线均可上传，且本地保存不依赖上传结果。
11. 上传载荷不包含联系人、聊天文字、图片、截图、音视频等内容；服务条款和隐私政策明确说明上传目的、数据范围、保存与撤回方式。
12. 上传数据进入维护者的集中分析后台，支持按厂商、机型、Android、微信版本、路线指纹、结果和回放状态汇总，并标记“待分析、已复现、已适配、忽略”；不自动跨设备共享。

## Open Questions

- 上传继续复用现有日志入口，后台增加独立“微信示教”聚合视图和明确的 `report_type`，不另建自动规则分发服务。

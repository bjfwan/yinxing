# 结构化诊断 v5

更新时间：2026-09-12

## 目标

线上错误必须能回答四个问题：哪一份代码、哪台安装实例、哪一段操作链、在哪一步失败。诊断结论必须区分“证据充分”“数据不足”和“疑似误报”，不能只凭设备型号或一行错误摘要猜测。

## 客户端契约

普通日志使用 `schema_version = 5`，日志与性能指标共同携带：

- `build_sha`：构建对应的 Git 提交；无法取得时为 `unknown`。
- `build_type`：`debug`、`devicetest`、`release` 或其他实际变体。
- `application_id`：区分正式包和测试包。
- `build_source_state`：`clean`、`dirty` 或 `unknown`。
- `session_id`：一次进程会话的随机标识。
- `session_event_sequence`：会话内严格递增的事件序号。
- `created_at`：UTC 事件时间；性能指标也必须携带。

错误事件继续携带 `error_code`、`failed_step`、脱敏设备状态。微信失败样本必须至少包含一个合法步骤；原步骤不可用时生成失败终止步骤。主线程卡顿携带 `stall_duration_ms` 和有界堆栈；采样时主线程已经回到 `MessageQueue` 空闲态，或错误采到 watchdog 自身时，只记恢复指标，不生成错误。

## 服务端诊断规则

1. 先按 `build_sha + application_id + build_type` 对应源码和安装包。
2. 再按 `device_id + session_id` 聚合同一次运行，优先按 `session_event_sequence` 还原故障前事件链，旧数据回退到 `created_at`。
3. 按 `error_code + failed_step + fingerprint` 聚类，不用自由文本作为唯一分组依据。
4. 构建身份缺失、步骤为空、事件序号缺口必须作为数据质量问题返回，禁止输出确定性根因。
5. 诊断摘要只返回聚合、脱敏字段和必要的有界堆栈，不返回 token、联系人、电话号码或完整原始日志。

## 发布顺序与验收

1. 服务端先兼容 v4/v5 并上线存储和查询。
2. 生成干净提交上的内部测试包，确认 `build_source_state = clean` 且 SHA 与安装包一致。
   正式构建必须执行 `:app:bundleReleaseDiagnostics`，把签名 APK 与同次 R8 `mapping.txt` 作为一个诊断包归档。
3. 用真实设备制造一次微信失败、一次真实主线程阻塞和一次恢复采样。
4. 确认服务端能还原事件链、展示步骤与阻塞时长，并把恢复采样排除出错误数。
5. 通过后再发布正式更新；旧版数据仍可查，但缺失的构建身份和步骤不能由服务端事后补造。

## 边界

该契约显著提高可定位性，不承诺仅靠日志自动修复所有缺陷。没有被代码捕获的业务状态、系统外部行为和无法复现的第三方 UI 变化，仍可能需要用户描述、设备复现或更有针对性的探针。

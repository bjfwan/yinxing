# Implementation Plan: 微信视频示教

## Architecture Decisions

- 示教观察值只允许固定枚举语义和动态占位符，不保存任意界面文字。
- 本地规则与上传相互独立；“上传本次匿名演示数据”默认勾选，用户可在开始前取消。
- 同一设备允许多条路线，按起始状态、适用条件、可靠度和最近验证时间选择并失败回退。
- 上传只供维护者集中分析和人工适配，不自动分发给其他用户。
- 继续复用现有呼叫启动确认，学习规则和主动采样都不能直接宣告成功。

## Task List

### Phase 1: Foundation

- [x] Task 1: 示教模型、隐私提取和完整度分析
  - Acceptance: 五个关键动作和 `VideoActivity` 可生成规则；乱序或缺失返回数据不足；模型无任意用户文字字段。
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests "*WeChatTeachingAnalyzerTest"`
  - Files: teaching model/analyzer + analyzer test（3 files）

- [x] Task 2: 设备规则持久化与兼容判断
  - Acceptance: JSON 往返一致；设备或微信版本变化时规则不可用；失败示教不覆盖旧规则。
  - Verify: `./gradlew.bat :app:testDebugUnitTest --tests "*WeChatTeachingStoreTest"`
  - Dependencies: Task 1
  - Files: store/fingerprint + store test（3 files）

### Checkpoint: Foundation

- [x] 定向测试通过，隐私边界有自动化证明。

### Phase 2: Recording Flow

- [x] Task 3: 无障碍事件采集和示教悬浮按钮
  - Acceptance: 准备、开始、结束、重试/退出状态可用；只接收微信点击和页面事件；完整结果自动本地保存。
  - Verify: Kotlin 编译及 controller 测试。
  - Dependencies: Tasks 1-2
  - Files: extractor/controller/overlay/service integration（5 files）

- [x] Task 4: 设置入口与状态
  - Acceptance: 设置页显示示教入口及未示教、已适配、需重教状态；无障碍未启用时不给出伪成功。
  - Verify: Settings smoke test + resource build。
  - Dependencies: Task 3
  - Files: settings controller/strings/settings test（3 files）

### Checkpoint: Recording Flow

- [x] 定向测试通过；`assembleDebug` 可编译。

### Phase 3: Use and Upload

- [x] Task 5: 学习规则兜底回放
  - Acceptance: 内置选择器失败后才读取匹配规则；最终视频选项仍只点击一次并进入现有确认阶段。
  - Verify: selector policy tests + WeChat focused tests。
  - Dependencies: Tasks 1-3
  - Files: selector/element locator/service（3 files）

- [x] Task 6: 明确同意后的匿名上传
  - Acceptance: 本地保存不依赖上传；只有点击“保存并上传”才发送；载荷不含联系人、聊天、图片、音视频内容。
  - Verify: upload payload test。
  - Dependencies: Tasks 1-3
  - Files: upload factory/test/controller（3 files）

### Checkpoint: Complete

- [x] 所有新增定向测试通过。
- [x] `git diff --check` 通过。
- [x] 构建结果与当前仓库既有并行故障分开报告。
- [x] 真机操作留给用户验收。

### Phase 4: 通用多路线学习（规划中）

- [ ] Task 7: 修复旧规则被新演示覆盖
  - Acceptance: 失败、数据不足或低可靠度演示不覆盖旧路线和旧验证结果；新结果按路线合并或追加。
  - Verify: 新增旧规则保护、重复路线合并和弱演示回归测试。

- [ ] Task 8: 通用多路线数据结构
  - Acceptance: 每条路线保存起始状态、适用条件、通用动作序列、结束证据、设备/微信指纹、来源、优先级、可靠度和生命周期；支持点击、相对位置、返回、滚动、等待、启动页面和动态输入占位符。
  - Verify: 模型、JSON 往返、旧数据迁移和隐私过滤测试。

- [ ] Task 9: 主动采样与全新路线采集
  - Acceptance: vivo 漏发 `VideoActivity` 或窗口事件时可通过当前窗口、可见控件和通话状态补样；从消息列表、聊天记录等新入口打通的视频路线也能完整记录，不再限定固定五步。
  - Verify: 漏事件、无点击事件、替代入口和误判语音通话测试。

- [ ] Task 10: 演示后自动回放验证
  - Acceptance: 演示先本地保存，再提示回放一次且允许取消；真实进入视频呼叫状态才标记已验证，失败保留候选路线并说明失败步骤。
  - Verify: 回放成功、用户取消、快速挂断、超时和语音误入测试。

- [ ] Task 11: 路线条件、排序、回退和停用
  - Acceptance: 根据起始页面、设备、微信版本和可见状态筛选；按可靠度和最近验证排序；失败回退下一条；连续失败后降级或停用，重新验证可恢复。
  - Verify: 多路线排序、条件不匹配、逐条回退、可靠度升降和停用恢复测试。

- [ ] Task 12: 用户可取消的默认上传
  - Acceptance: 开始演示前显示默认勾选的“上传本次匿名演示数据”；用户可取消；成功、失败、数据不足和未知路线都遵循本次选择；本地保存不依赖上传。
  - Verify: 默认勾选、取消后零请求、各种结束状态和离线队列测试。

- [ ] Task 13: 上传载荷与服务条款
  - Acceptance: 使用 `wechat_teaching_route_v1`，上传路线/会话标识、起始状态、脱敏步骤、前后状态指纹、结束证据、漏事件、设备/微信版本、可靠度、回放结果和失败原因；不含联系人、聊天文字、截图、图片和音视频；隐私政策、服务条款和演示页说明一致。
  - Verify: 载荷白名单测试、敏感字段反向测试和文案检查。

- [ ] Task 14: 维护者集中分析后台
  - Acceptance: LogAnalyse 正确保存 `report_type` 和路线数据；新增“微信示教”视图，可按厂商、机型、Android、微信版本、路线指纹、结果和回放状态汇总，查看脱敏时间线并标记“待分析、已复现、已适配、忽略”。
  - Boundary: 数据用于维护者人工改进银杏，不自动下发规则、不让所有用户直接共享采集结果。
  - Verify: API 入库、筛选聚合、详情展示、状态流转和导出测试。

### Checkpoint: 通用学习闭环

- [ ] Android 定向测试、构建通过，旧版单路线数据可安全迁移。
- [ ] 用户完成一次新路线演示、取消上传和允许上传三种真机验收。
- [ ] 后台能够看到脱敏数据并完成一次“待分析 → 已适配”流转。

## Risks and Mitigations

- 微信控件信息不完整：语义、资源 ID、页面上下文、相对位置分层兜底，数据不足不保存。
- 旧微信规则误用：设备指纹和微信版本必须同时匹配。
- 示教误触：只采集微信包；完整规则必须进入呼叫页；最终拨号沿用现有单击保护。
- 多路线误执行：执行前校验起始状态和适用条件，逐步验证状态，失败立即停止当前路线并回退。
- 主动采样误判：采样只能补充证据，不能替代视频呼叫状态确认。
- 默认上传理解不足：演示开始前保持勾选项可见、可取消，并在服务条款和隐私政策说明用途与范围。
- 数据集中后误用：后台只支持维护者分析和人工适配，不建设自动跨设备分发链路。
- 并行修改冲突：优先新增文件，只对设置和无障碍服务做最小补丁。

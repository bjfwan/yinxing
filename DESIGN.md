# 银杏 Launcher 设计规范

本文件是项目 UI 的唯一设计契约。所有新页面、改页面、设计稿必须按这里执行；
审计时发现代码与本文件不一致，以本文件为准修代码。

## 色彩

### 中性
| Token | 值 | 用途 |
|---|---|---|
| `launcher_primary` | `#0F172A` | 主按钮填充、关键文字 |
| `launcher_primary_dark` | `#1E293B` | 页面标题、次级按钮文字 |
| `launcher_surface` | `#FFFFFF` | 卡片、弹窗 |
| `launcher_surface_muted` | `#F8FAFC` | 输入框、次级按钮底 |
| `launcher_surface_soft` | `#F1F5F9` | 头像占位、图标底块 |
| `launcher_outline` | `#D2E4F2` | 卡片/输入框描边 |
| `launcher_text_secondary` | `#475569` | 副标题、说明文字 |
| `launcher_text_muted` | `#94A3B8` | 占位提示、弱化文字 |

### 语义色
| Token | 值 | 用途 |
|---|---|---|
| `launcher_phone_action` | `#065F46` | 拨打/通话主操作 |
| `launcher_action` | `#10B981` | 正向操作（新增、成功）— `DialogButtonSuccess` |
| `launcher_call` | `#059669` | 通话相关强调、徽章文字 |
| `launcher_call_soft` | `#D1FAE5` | 徽章/高亮底色 |
| `launcher_danger` | `#EF4444` | 删除等破坏性操作 |
| `launcher_danger_soft` | `#FEE2E2` | 危险操作底色 |

### 渐变
- 页面底：`settings_page` `#FCFDFE → #F3F6FA`（135°）；首页底 `launcher_bg` `#F8FAFC → #E2E8F0`
- 深色按钮：`settings_primary` `#12264A → #071A35`

**色彩角色约定**：深蓝 = 页面级操作（完成、更换、管理）；绿 = 正向/通话动作（新增、拨打）；红 = 破坏。同一语义只能落在同一颜色的件上。

## 圆角标准（2026-10 定稿）

| Token | 值 | 用途 |
|---|---|---|
| `radius_xs` | **6dp** | 徽章、标签（原 chip 胶囊一律改小方标签） |
| `radius_sm` | **10dp** | 图标底块、分段开关、小工具件 |
| `radius_md` | **14dp** | **主力档：按钮、输入框、卡片、内容容器** |
| `radius_lg` | **20dp** | 弹窗 surface、Hero/首页大卡 |
| 真圆形 | ∞ | 仅开关轨道、圆形头像/旋钮 |

**页面主体只允许 radius_md 一种圆角。** 按钮不再使用胶囊造型。

迁移映射：现有 14/16/18/20/23 → `radius_md`；24/26/28/32/34 → `radius_lg`；9/10/12 → `radius_sm` 或 `radius_xs`。

## 字号

| 层级 | sp | 用途 |
|---|---|---|
| PageTitle | 28 bold | 页面标题（#1E293B） |
| PageSubtitle | 17 | 页面副标题（#475569） |
| CardTitle | 26 bold | 卡片主名（联系人名等） |
| ListTitle | 22 bold | 列表/区块标题 |
| Button | 18 bold | 按钮文字（dialog 内 16） |
| Meta | 16 | 次要信息 |
| DialogTitle | 22 bold | 弹窗标题（居中） |
| DialogSectionLabel | 15 bold | 字段标签 |
| DialogSupporting | 14 | 辅助说明（#475569） |
| Caption | 14 | 弱化注释（#94A3B8） |

原则：层级只用 `TextAppearance.OldLauncher.*` 里的现成值，不新造 sp。

**设置页（家属端）专用档**：设置页面向家属/维护者，不复用老人端大字档；只用 `Settings*` 前缀样式，改动不外溢到首页与老人端弹窗。

| 层级 | sp | 用途 |
|---|---|---|
| SettingsPageTitle | 22 bold | 设置页/详情页标题 |
| SettingsRowTitle | 17 medium | 设置行标题 |
| SettingsRowMeta | 14 | 设置行副标题、右侧值 |
| SettingsSectionLabel | 13 medium | 概览/详情页分组标题 |
| SettingsHint | 13 | D1 行内展开说明 |

**分组标签约定**：概览页每个卡片组必须有 `SettingsSectionLabel` 节标题；详情页多卡片时用 `useRowsContainer(containerId, cardId, labelId, labelRes)` 给次级卡片挂标签（label 出现在卡片上方，卡片顶部间距由标签接管）。「使用前检查」卡覆盖：来电链路（通知/默认电话/电话权限）+ 视频链路（无障碍/悬浮窗）+ 保活（电池/自启/后台）；默认桌面状态由「桌面与设备」hub 行承担（回退重定向是合法替代，不算待办），联系人读取权限在导入流程按需请求、不入清单。

## 间距

| Token | dp | 用途 |
|---|---|---|
| `spacing_xs` | 4 | 图标与文字、元素内部微距 |
| `spacing_sm` | 8 | 同组元素间、行内间隔 |
| `spacing_md` | 12 | 卡片间距、区块内元素 |
| `spacing_lg` | 16 | 页面水平 padding、卡片 padding |
| `spacing_xl` | 20 | 大区块间隔、弹窗内容 padding（紧凑） |
| `spacing_xxl` | 24 | 弹窗 padding、页面级大间隔 |

只用这六档，布局里出现 7/9/10/13 这类游离值视为违规。

## 图标

- 只用 `app/src/main/res/drawable/` 里现有 vector 图标或 Material Symbols 风格；不混用第二套图标库。
- 图标尺寸档：行内 20、按钮内 22、独立入口 24、头像/占位 ≥36。
- **关键操作图标必须配文字**或语义直白到老人能懂（`person_add` 可以，裸铅笔/裸齿轮不行）。
- 纯装饰图标设 `importantForAccessibility="no"`；承载含义的必须配 `contentDescription`。

## 组件状态

每个交互件至少要想到这些态，缺了在审计里报：

- 按钮：默认 / 按下（ripple）/ 禁用（灰化、不响应）
- 输入框：默认 / 聚焦（`edit_text_background_focused`，描边变深蓝）
- 列表：正常 / **空态**（`PageStateView`：图标+标题+一句引导+主操作）/ 加载
- 弹窗：正常 / 危险操作（danger 按钮+确认文案）
- 徽章：开启态（call_soft 底 + call 字）

## 组件（Widget.OldLauncher）

| 组件 | 规格 | 用途 |
|---|---|---|
| `DialogSurfaceCard` | 白卡 + 描边 + 6dp 阴影、radius_lg | **所有弹窗：居中卡片，不做底部弹层** |
| `DialogButtonPrimary` | 深蓝实心、h56、radius_md | 主操作 |
| `DialogButtonSecondary` | surface_muted + 描边、h56、radius_md | 次级操作 |
| `DialogButtonDanger` | 红、h56 | 删除/危险 |
| `DialogButtonSuccess` | `#10B981`、h56 | 新增/确认 |
| `DialogContentCard` | muted + 描边、radius_md | 弹窗内分组容器 |
| `PageToolbarButton` | 次级样式衍生、radius_md | 标题栏右侧小按钮 |
| `ListItemCard` | 大卡 radius_lg | 首页大卡 |
| `PageHeroCard` | radius_lg | Hero 区域 |

常用 drawable：`bg_settings_primary_capsule`（深蓝渐变，改 radius_md）、`bg_settings_mode_capsule`（白94%+描边容器）、`edit_text_background(_focused)`（2dp 描边输入框）。

## 首页卡片档位

六格网格（`GRID_SPAN_COUNT = 6`）下每个首页入口占位宽分三档，只改卡片宽度，不改图标与名称字号档位语义：

| 档位 | 跨度 | 图标底 | 名称 | 用途 |
|---|---|---|---|---|
| SMALL | 2 格（一行三个） | 88dp | 18sp，单行 | 应用多、一屏放更多入口 |
| MEDIUM | 3 格（一行两个） | 116dp | 28sp，两行 | 默认档 |
| LARGE | 6 格（独占一行） | 128dp | 30sp，单行 | 电话、微信视频等最高频入口 |

- 每行档位列宽不足 6 格时，剩余格由该行卡片均摊吸收（`HomeCardSize.effectiveRowSpans`），行行铺满、不留行尾空隙；顺序与档位语义不变。
- 家属设置内按入口单独设置或统一大小；设置行说明用 D1 行内展开，选择器用 D3 说明弹窗。
- 档位变化只做宽度与图标底插值，不做跨档位缩放动画；入场动画按行错峰。

## 页面结构

- **标题栏**：52dp 返回（透明底深蓝箭头）+ PageTitle + PageSubtitle + 右侧一个动作按钮。右侧按钮**只能有一个含义**，不许"变身"。
- **触达目标**：≥52dp（构建标准）；审计下限 48dp。相邻可点目标间距 ≥8dp。
- **一屏一个主操作**：全页最多一个实心深色/绿色主按钮；其余用描边次级或文字。
- **行为切换要有进出**：页面行为变化（拨号↔编辑）必须换标题/副标题给出信号，并提供明确的退出按钮（如「完成」），返回键同样生效。
- **破坏性操作**：不出现在一级页面；必须二次确认弹窗（`DialogButtonDanger`）。
- **文案可见即所得**：副标题直接说明页面用法，不让用户猜。
- **同形同色同语义**：长得一样的按钮必须干一样的事；颜色/形状差异代表语义差异。

## 弹窗规范

- 一律 `LauncherDialogFactory` 居中弹窗，`DialogSurfaceCard` + padding 20/24。
- 字段用 mode 容器分组 + `DialogSectionLabel` 标签 + `DialogSupporting` 说明。
- 按钮行 h56：左次级右主键；编辑态左键可换 `DialogButtonDanger`。
- 标题 `DialogTitle` 居中，22sp bold。

## 提示/说明呈现系统

所有给用户看的解释、状态、引导、反馈只能落在以下八式之一；写新文案先定档再选载体，不许串用。

### 常驻族（无需交互）

| 式 | 载体 | 职责 | 规则 |
|---|---|---|---|
| S1 状态副标题 | 行副标题 | 当前值/状态 | ≤14字，写"状态+后果"（如「已锁定，禁止拖动」）；开关已能表达的裸「已开启/已关闭」不占副标题 |
| S2 页面状态块 | `PageStateView`、页面内 banner、错误块 | 空态/加载/失败/页面级警示 | 必须有图标+标题+一句引导+主操作 |
| S3 待办徽标 | 计数 badge、红点、hub 状态卡、「新」角标 | "需要处理/发现"的信号 | 只表达待办，不写解释；解释交给点进去的页面。「新」角标（`bg_badge_new`，标题右侧贴附）由 `NewFeatureRegistry` 按版本登记：每个新功能一个稳定 key + `introducedIn` 版本号，用户进入该功能一次即持久消隐，下一版本基线自动翻页 |

### 按需族（点了才出现）

| 式 | 载体 | 职责 | 规则 |
|---|---|---|---|
| D1 行内展开 | `detail_row_hint`（(i) 触发） | 功能解释 | ≤60字；不自动消失；设置行的解释一律用此式 |
| D2 锚点气泡 | `AnchoredHintPopup` | 指向性引导（"点这里看 XX"） | 必须指向具体控件；同屏至多一个；**设置行禁用 D2**；一次性场景用，自动消失 |
| D3 说明弹窗 | `LauncherDialogFactory` | 长文/分步指引/要操作的引导 | >60字或有步骤顺序的场景；弹窗规范同上 |

### 反馈族（操作后出现）

| 式 | 载体 | 职责 | 规则 |
|---|---|---|---|
| F1 即时反馈 | Toast + 行内校验错误 | 操作结果、表单校验 | >20字的解释或多步指引禁用 Toast（升 D3）；不复用 summary 文案充当 Toast |
| F2 过程状态 | `FloatingStatusView`、示教浮层、三态进度弹窗 | 长任务进行中 + 可取消 | 浮层已承载进度时不再叠加 Toast 通道 |

历史归位：设置行 (i) ×9、清缓存 (i)、导入联系人 (i) 已全部从 D2 气泡迁入 D1 行内展开；D2 仅保留首页天气卡、家属入口、天气城市名三处真·指向引导。

## 老人场景硬规则

1. 无破坏的入口可以做明显做大；误触成本为零才配大按钮。
2. 任何让"卡片点按行为改变"的开关都必须远离日常拇指热区。
3. 图标必须配文字或语义直白（`person_add` 好于裸 `+`）。
4. 空态、超长名字、单条数据、最大字体档（FontScaleActivity）都要能正常显示。
5. 弹窗不许遮罩即关（`dismissOnTouchOutside=false`），出口必须是指挥明确的按钮。

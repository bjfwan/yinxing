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

## 老人场景硬规则

1. 无破坏的入口可以做明显做大；误触成本为零才配大按钮。
2. 任何让"卡片点按行为改变"的开关都必须远离日常拇指热区。
3. 图标必须配文字或语义直白（`person_add` 好于裸 `+`）。
4. 空态、超长名字、单条数据、最大字体档（FontScaleActivity）都要能正常显示。
5. 弹窗不许遮罩即关（`dismissOnTouchOutside=false`），出口必须是指挥明确的按钮。

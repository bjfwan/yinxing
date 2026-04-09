# 当前架构说明

更新时间：2026-04-09

## 1. 架构概览

当前项目是一个单模块 Android 应用，核心结构已经按职责整理为 `feature`、`data`、`common`、`automation` 四层。

## 2. 代码分层

### 2.1 feature

- `feature.home`
  - 桌面主页
  - 桌面卡片与拖拽相关类
- `feature.appmanage`
  - 应用管理页
  - 应用列表展示与勾选逻辑
- `feature.phone`
  - 电话联系人页
  - 电话联系人展示与编辑逻辑
- `feature.videocall`
  - 视频联系人页
  - 本地视频联系人展示与管理逻辑

### 2.2 data

- `data.home`
  - 桌面已选应用配置
  - 桌面应用顺序策略
- `data.contact`
  - 视频联系人数据模型
  - 视频联系人本地存储管理

### 2.3 common

- `common.service`
  - 通用语音播报服务
- `common.ui`
  - 公共浮窗视图
- `common.util`
  - 权限与网络工具类
  - 无障碍服务匹配与权限判断

### 2.4 automation

- `automation.wechat`
  - 实验性微信自动化能力
  - 包含状态模型、超时管理、无障碍服务和无障碍工具
  - 当前与稳定产品目标隔离，不作为当前版本主干能力

## 3. 数据与存储

- `launcher_prefs`
  - 桌面已选择应用
  - 桌面应用顺序
- `wechat_contacts`
  - 视频联系人本地列表
- `ContactsContract`
  - 电话联系人真实数据源

## 4. 外部系统依赖

- Android Launcher 能力
- Contacts Provider
- 电话拨号权限
- 系统设置页跳转
- 无障碍服务
- 悬浮窗权限

## 5. 当前架构特点

- 没有后端
- 没有数据库层
- 没有依赖注入框架
- 没有多模块拆分
- 自动化能力和主业务已经做包级隔离，但仍处在同一 app 模块中

## 6. 当前需要继续治理的点

- `feature.phone.PhoneActivity` 仍直接承担权限申请、联系人增删改查和图片处理，职责偏重
- `feature.videocall.VideoCallActivity` 同时承担联系人管理、权限检查和自动化触发，主流程与实验性能力仍有耦合
- `feature.appmanage.AppManageActivity` 仍依赖全量应用查询，Android 11+ 的应用可见性兼容性需要单独治理

## 7. 当前架构边界

- 主页、电话、应用管理属于当前主业务
- 微信自动化属于实验性能力
- 来电自动接听、通知监听、远程协助不在当前架构交付范围内

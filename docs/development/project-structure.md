# 项目结构规范

更新时间：2026-04-09

## 1. 根目录结构

```text
OldLauncher/
├── app/
├── docs/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```

## 2. Android 源码结构

```text
app/src/main/java/com/bajianfeng/launcher/
├── automation/
│   └── wechat/
├── common/
│   ├── media/
│   ├── service/
│   ├── ui/
│   └── util/
├── data/
│   ├── contact/
│   └── home/
└── feature/
    ├── appmanage/
    ├── home/
    ├── phone/
    ├── settings/
    └── videocall/
```

## 3. 分层规则

- 页面、页面专属适配器、页面专属模型优先放在对应 `feature` 包内
- 可跨功能复用的服务、公共视图、工具类放在 `common`
- 本地数据模型、偏好设置和数据管理器放在 `data`
- 实验性自动化能力统一放在 `automation`

## 4. 命名规则

- Activity 放在所属功能目录中
- Adapter 与其直接服务的页面放在同一功能目录中
- Markdown 文档统一放在 `docs/`
- 根目录默认只保留项目入口文档 `README.md`

## 5. 资源命名规则

- 页面布局：`activity_*`
- 对话框布局：`dialog_*`
- 列表项布局：`item_*`
- 通用背景：`bg_*`
- 规则配置：`*_rules.xml`

## 6. 后续新增文件放置建议

- 新桌面功能：`feature.home`
- 新桌面配置或排序策略：`data.home`
- 新电话功能：`feature.phone`
- 新视频联系人功能：`feature.videocall`
- 新设置页功能：`feature.settings`
- 新图片或缩略图能力：`common.media`
- 新共享工具：`common`
- 新本地数据源：`data`
- 新实验性自动化：`automation`

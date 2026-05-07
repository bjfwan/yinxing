# 项目结构规范

更新时间：2026-05-07

## 1. 根目录结构

```text
OldLauncher/
├── app/
├── benchmark/
├── docs/
├── gradle/
├── build.gradle.kts
├── build.bat
├── gradle.properties
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```

> 私有密钥、日志 token 和签名配置通过 `local.properties` 注入，不提交到仓库。

## 2. 主应用源码结构

```text
app/src/main/
├── java/com/yinxing/launcher/
│   ├── automation/wechat/
│   │   ├── manager/
│   │   ├── model/
│   │   ├── service/
│   │   └── util/
│   ├── common/
│   │   ├── ai/
│   │   ├── media/
│   │   ├── service/
│   │   ├── ui/
│   │   └── util/
│   ├── data/
│   │   ├── contact/
│   │   ├── home/
│   │   └── weather/
│   └── feature/
│       ├── appmanage/
│       ├── home/
│       ├── incoming/
│       ├── phone/
│       ├── settings/
│       └── videocall/
└── res/
    ├── drawable/
    ├── layout/
    ├── mipmap-*/
    ├── values/
    └── xml/
```

## 3. 测试与性能模块结构

```text
app/src/test/                 Robolectric 与单元测试
app/src/androidTest/          第一批设备级仪器测试
benchmark/src/main/kotlin/    Baseline Profile 与 Macrobenchmark
```

## 4. 分层规则

- 页面、页面专属适配器、页面专属控制器优先放在对应 `feature` 包内
- 可跨功能复用的服务、公共视图、工具类放在 `common`
- 本地数据模型、偏好设置、仓储与管理器放在 `data`
- 实验性微信自动化能力统一放在 `automation.wechat`
- 设备级 UI 回归放在 `app/src/androidTest`

## 5. 命名规则

- Activity 放在所属功能目录中
- Adapter 与其直接服务的页面放在同一功能目录中
- 页面专属 Dialog Controller 放在对应功能目录中
- Markdown 文档统一放在 `docs/`
- 根目录默认只保留项目入口文档 `README.md`

## 6. 资源命名规则

- 页面布局：`activity_*`
- 对话框布局：`dialog_*`
- 列表项布局：`item_*`
- 通用背景：`bg_*`
- 状态视图：`view_*`
- 规则配置：`*_rules.xml`

## 7. 新增文件放置建议

- 新桌面功能：`feature.home`
- 新桌面配置或排序策略：`data.home`
- 新电话功能：`feature.phone`
- 新视频联系人功能：`feature.videocall`
- 新设置页功能：`feature.settings`
- 新图片或缩略图能力：`common.media`
- 新共享工具：`common.util` 或 `common.ui`
- 新本地数据源：`data`
- 新实验性自动化：`automation.wechat`
- 新 Robolectric 或单元测试：`app/src/test`
- 新设备级仪器测试：`app/src/androidTest`

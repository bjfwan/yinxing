# 文档总览

更新时间：2026-10-28

> 公开仓只保留对外读者需要的文档。内部工程、自动化规格、测试与发布流程资料不对外发布。

## 公开文档

### 产品

- [产品需求](product/product-requirements.md)：产品定位、已发布范围与非功能要求
- [当前功能状态](product/current-feature-status.md)：已发布能力、实验性边界与未交付范围

### 设计与兼容

- [设计规范（根目录）](../DESIGN.md)：UI 唯一设计契约
- [产品原则（根目录）](../PRODUCT.md)：产品目标、原则与无障碍要求
- [首页返回与厂商兼容策略](architecture/home-navigation-compatibility.md)：默认桌面与回退原则

### 许可与发布

- [许可说明（根目录）](../LICENSING.md)：分版本许可与免费承诺
- 版本发布说明：[v2.4.1](release/v2.4.1.md)、[v2.4.0](release/v2.4.0.md)、[v2.3.0](release/v2.3.0.md)、[v2.2.0](release/v2.2.0.md)、[v2.1.1](release/v2.1.1.md)、[v2.1.0](release/v2.1.0.md)、[v2.0.0](release/v2.0.0.md)

### 官网与下载页物料

- `index.html`、`versions.html`、`privacy.html`、`terms.html`、`update.json`、`releases.json`、`app-release.apk`：官网与下载页资产，随每次发布同步

## 维护原则

- 公开文档只写已发布能力与对外可公开的边界，不写内部待办与未发布实验。
- 公开文档单独维护，版本发布时与 README、产品需求、当前功能状态和发布说明一起同步。
- 版本发布时同步更新 README、产品需求、当前功能状态、发布说明与下载页物料。
- 文档中的"已实现/已验证/实验性"口径与 `current-feature-status.md` 保持一致，不夸大真机验证范围。

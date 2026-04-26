# 文档总览

更新时间：2026-04-26

## 文档使用规则

- `product` 目录描述当前产品范围、交付边界与功能状态
- `architecture` 目录描述当前模块、分层和关键职责
- `development` 目录描述工程结构、构建方式和 Git 协作约束
- `testing` 目录描述当前自动化覆盖、设备级现状和发布门禁
- `roadmap` 目录只描述规划，不等同于已交付能力
- `release` 目录用于发布前回归和提测检查

## 当前文档列表

- [产品需求](product/product-requirements.md)
- [功能状态](product/current-feature-status.md)
- [当前架构](architecture/current-architecture.md)
- [项目结构](development/project-structure.md)
- [构建说明](development/setup-and-build.md)
- [Git 协作规范](development/git-workflow.md)
- [测试策略](testing/test-strategy.md)
- [项目路线图](roadmap/project-roadmap.md)
- [发布检查清单](release/release-checklist.md)

## 文档维护原则

- 文档必须以当前仓库真实代码、Gradle 配置和测试结果为准
- 规划类文档必须显式标注"规划中"或"暂停推进"
- 功能、结构或测试覆盖有变化时，同步更新对应文档
- 新增设备级测试时，至少同步更新 `testing` 与 `development` 文档
- 根目录默认只保留 `README.md`，其他说明文档统一放在 `docs/`

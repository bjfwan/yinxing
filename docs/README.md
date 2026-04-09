# 文档总览

本文档目录从 2026-04-09 开始作为项目文档的统一入口。

## 文档使用规则

- `product` 目录描述当前产品范围和真实功能状态
- `architecture` 目录描述当前代码结构和技术方案
- `development` 目录描述目录规范、构建方式和协作约束
- `testing` 目录描述当前测试现状和测试计划
- `roadmap` 目录只描述规划，不等同于已交付能力
- `release` 目录用于发布前检查

## 当前文档列表

- [产品需求](product/product-requirements.md)
- [功能状态](product/current-feature-status.md)
- [当前架构](architecture/current-architecture.md)
- [项目结构](development/project-structure.md)
- [构建说明](development/setup-and-build.md)
- [Git 协作规范](development/git-workflow.md)
- [测试策略](testing/test-strategy.md)
- [项目路线图](roadmap/project-roadmap.md)
- [微信视频规划](roadmap/wechat-video-call-plan.md)
- [发布检查清单](release/release-checklist.md)

## 文档维护原则

- 文档必须以当前仓库真实代码为准
- 规划类文档必须显式标注“规划中”或“暂停推进”
- 新增功能时，至少同步更新功能状态、架构或项目结构中的一个文档
- 根目录默认只保留 `README.md`，其他说明文档统一放在 `docs/`

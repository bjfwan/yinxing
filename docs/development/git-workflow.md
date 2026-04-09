# Git 协作规范

更新时间：2026-04-09

## 1. 目标

本项目默认采用 `main + feature` 分支模型。

- `main` 只保留相对稳定、可运行、可演示的代码
- 日常开发必须先创建分支，再提交代码
- 不允许直接在 `main` 上开发并提交

## 2. 分支规则

- `main`：主分支
- `feature/*`：新功能开发
- `fix/*`：缺陷修复
- `refactor/*`：重构调整
- `docs/*`：文档修改

分支命名示例：

- `feature/video-call-ui`
- `fix/contact-list-crash`
- `refactor/package-structure`
- `docs/project-roadmap-update`

## 3. 开发流程

每次开始新任务前，先同步主分支，再创建自己的工作分支。

```bash
git switch main
git pull origin main
git switch -c feature/your-task-name
```

开发完成后，在当前分支提交：

```bash
git add .
git commit -m "feat: add xxx"
git push -u origin feature/your-task-name
```

然后发起 PR，目标分支为 `main`。

## 4. 提交规则

提交信息统一使用前缀：

- `feat:` 新功能
- `fix:` 修复问题
- `refactor:` 重构
- `docs:` 文档修改
- `chore:` 杂项调整
- `test:` 测试相关

提交信息示例：

- `feat: add video call contact screen`
- `fix: handle empty contact list`
- `refactor: reorganize launcher package structure`
- `docs: add git workflow guide`

## 5. 合并规则

- 日常开发分支必须通过 PR 合并到 `main`
- 合并前至少自己完整检查一次改动范围
- 两人协作时，非紧急情况建议让对方看一遍再合并
- 未确认可运行前，不合并到 `main`

## 6. 禁止事项

- 不直接向 `main` 提交代码
- 不把 `.idea`、`build`、`apk`、签名文件等本机或构建产物提交到仓库
- 不在一个分支里混入多个无关需求
- 不在未同步 `main` 的情况下长期并行开发

## 7. 当前项目执行要求

从本规范生效后，后续开发统一按以下要求执行：

1. 开发前先建分支
2. 在自己的分支上完成编码和提交
3. 推送远程分支
4. 发起 PR 到 `main`
5. 检查无问题后再合并

当前项目默认不使用 `develop` 分支，后续如多人并行开发明显增多，再单独评估是否引入。

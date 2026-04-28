# Git 协作规范

更新时间：2026-04-28

## 1. 目标

本项目默认采用 `main + feature` 分支模型。

- `main` 只保留相对稳定、可运行、可演示的代码
- 日常开发必须先创建分支，再提交代码
- 不允许直接在 `main` 上开发并提交
- 功能、测试、文档混合改动时，优先使用能覆盖主任务的分支名

## 2. 分支规则

- `main`：主分支
- `feature/*`：新功能或成套能力建设
- `fix/*`：缺陷修复
- `refactor/*`：重构调整
- `docs/*`：纯文档修改

分支命名示例：

- `feature/video-call-ui`
- `feature/docs-and-instrumentation-bootstrap`
- `fix/contact-list-crash`
- `refactor/package-structure`
- `docs/project-roadmap-update`

## 3. 完整闭环

### 3.1 开发前

每次开始新任务前，先同步主分支，再创建自己的工作分支。

```bash
git switch main
git pull origin main
git switch -c feature/your-task-name
```

### 3.2 开发中提交

开发完成后，在当前分支提交并推送远程：

```bash
git add .
git commit -m "feat: add xxx"
git push -u origin feature/your-task-name
```

### 3.3 提交前最低自检

提交前至少完成以下检查：

- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:lintDebug`
- 如果改动了 `androidTest`，至少保证 `:app:assembleDebugAndroidTest` 可通过
- 如果本机已连接设备或模拟器，再执行 `:app:connectedDebugAndroidTest`

### 3.4 GitHub 合并

推送完成后，在 GitHub 发起 PR，目标分支为 `main`。

流程如下：

1. 选择 `base: main`
2. 选择 `compare: feature/your-task-name`
3. 创建 PR
4. 检查改动范围
5. 确认无问题后合并到 `main`
6. **注意：合并时必须选择 "Create a merge commit"（传统合并方式），严禁使用 "Squash and merge" 或 "Rebase and merge"，以保留完整的分支历史树。**

### 3.5 合并后本地收尾

PR 合并完成后，本地执行以下命令：

```bash
git switch main
git pull origin main
git branch -d feature/your-task-name
```

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
- `test: add instrumentation smoke coverage`
- `fix: handle empty contact list`
- `refactor: reorganize launcher package structure`
- `docs: refresh project documentation`

## 5. 合并规则

- 日常开发分支必须通过 PR 合并到 `main`
- 合并前至少自己完整检查一次改动范围
- 两人协作时，非紧急情况建议让对方看一遍再合并
- 未确认可运行前，不合并到 `main`

## 6. 禁止事项

- 不直接向 `main` 提交代码
- 不把 `.idea`、`build`、普通 `apk`、签名文件等本机或构建产物提交到仓库；下载页使用的 `docs/app-release.apk` 是唯一例外
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

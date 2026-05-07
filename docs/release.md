# Release 流程

## 版本号

1. 在 `app/build.gradle.kts` 更新 `versionCode` 和 `versionName`。
2. 确认本次变更说明、回滚版本号和发布渠道文案已经准备好。

## 构建前检查

1. 复制 `local.properties.example` 为 `local.properties`。
2. 填写 `RELEASE_STORE_FILE`、`RELEASE_KEY_ALIAS`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_PASSWORD`。
3. 如需真实天气能力，填写 `SENIVERSE_UID`、`SENIVERSE_PK`、`TENCENT_KEY`。
4. 确认 `release-key.jks` 仅保留在本地且未加入 Git。
5. 确认 `docs/app-release.apk` 将由本次 release APK 覆盖。

## 构建

```bash
.\build.bat :app:assembleRelease
```

产物默认位于 `app/build/outputs/apk/release/`。

## 测试

```bash
.\build.bat :app:testDebugUnitTest
.\build.bat :app:lintDebug
.\build.bat :benchmark:connectedCheck
```

至少确认以下内容：

- 首页冷启动正常
- 应用管理可打开
- 电话联系人页可打开
- 来电弹窗链路可拉起
- APK 可正常安装覆盖升级

## 发布

1. 在 GitHub Releases 创建新版本并上传 release APK。
2. 用新 APK 覆盖 `docs/app-release.apk`。
3. 更新 `docs/update.json` 的 `versionCode`、`versionName`、`apkUrl` 与 `releaseNotes`。
4. 检查 `docs/index.html` 下载入口是否仍指向最新 APK。
5. 校验静态下载页与 GitHub Releases 备用下载链接都可访问。

## 回滚

1. 恢复上一个稳定版本的 GitHub Release 为最新可见版本。
2. 将 `docs/app-release.apk` 回滚为上一个稳定 APK。
3. 如首页静态页有文案更新，一并回退对应页面内容。
4. 重新验证静态下载页与 GitHub Releases 下载结果。

## 签名风险评估

- 当前工作区存在本地 `release-key.jks` 文件。
- `git ls-files -- release-key.jks` 无输出。
- `git log -- release-key.jks` 在当前可达历史中无记录。
- 以当前仓库状态看，没有证据表明该文件已被当前可达 Git 历史追踪。
- 因此本次先按本地私钥管理处理，不把轮换作为阻塞项。
- 如果该密钥曾在仓库外泄露、曾被上传到其他远端、或被多人共享，下一次公开发布前应立即轮换。

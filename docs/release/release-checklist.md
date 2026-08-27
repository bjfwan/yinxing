# 发布检查清单

更新时间：2026-08-27

## 1. 版本信息

- [x] 当前源码版本为 `versionCode = 17`、`versionName = "2.0.0"`
- [x] v2.0.0 主体变更范围已记录
- [x] 最终发布说明已确认
- [x] 首页视觉收尾已确认

## 2. 当前本地构建基线

- [x] `:app:assembleDebug` 通过
- [x] `:app:testDebugUnitTest` 通过（421 tests, 0 failed）
- [x] `:app:lintDebug` 通过（0 errors，85 warnings，`UnusedResources = 0`）
- [x] `:app:assembleRelease` 通过
- [x] `:app:lintVitalRelease` 通过
- [x] Release APK 输出到 `app/build/outputs/apk/release/app-release.apk`，约 2.61 MiB
- [x] 本轮重新执行 `:app:assembleDebugAndroidTest`
- [ ] 在目标设备完整执行 `:app:connectedDebugAndroidTest`

## 3. 发布前功能检查

- [ ] 默认桌面与首页最终视觉正常
- [ ] 应用管理选择、移除和排序正常
- [ ] 电话联系人列表/宫格、头像、增删改和拨号正常
- [ ] 视频联系人管理与微信视频主动拨号正常
- [ ] 天气详情、城市管理、当前位置与失败回退正常
- [ ] 设置卡片/列表模式、权限入口和版本检查正常
- [ ] 使用真实 SIM 验证系统电话响铃、倒计时、接听、挂断和扬声器

## 4. 权限与兼容性

- [ ] 联系人、电话、通知、定位和无障碍权限文案准确
- [ ] 权限拒绝后页面可恢复，且不会清空已有数据
- [ ] 默认电话角色和厂商设置引导在目标设备可用
- [ ] 微信视频来电自动接听未被描述为当前交付能力

## 5. 发布资产同步

- [x] Markdown 状态文档已更新到 v2.0.0
- [x] 用最终 Release APK 覆盖 `docs/app-release.apk`
- [x] 更新 `docs/update.json` 的版本、下载地址和发布说明
- [x] 更新 `docs/index.html` 的版本号与包体大小
- [x] 上传 GitHub Release，并核对 APK SHA-256
- [x] 部署下载页并验证公开下载链接
- [x] 提交改动、创建 `v2.0.0` 标签并确认远端提交

## 6. 发布口径

- 本地构建通过不等于线上发布完成。
- 真机设计验收不替代系统电话真实 SIM 回归。
- 下载页、`update.json`、GitHub Release 和最终 APK 一致后，才能将 v2.0.0 标记为已发布。

<div align="center">

<img src="https://raw.githubusercontent.com/bjfwan/yinxing/main/docs/Android_App_Icons/google-play/icon.png" width="120" height="120" />

<h1>银杏 · Yinxing Launcher</h1>

<p>专为长辈设计的 Android 简洁桌面</p>

[![License](https://img.shields.io/badge/license-MIT-B8882A?labelColor=1C1914&style=flat-square)](#)
[![Release](https://img.shields.io/badge/⬇︎%20下载%20APK-B8882A?labelColor=1C1914&style=flat-square)](https://yx.likeyou.qzz.io)
[![Platform](https://img.shields.io/badge/Android%207%2B-0369A1?labelColor=1C1914&style=flat-square&logo=android&logoColor=white)](#)
[![Size](https://img.shields.io/badge/1.7%20MB-555?labelColor=1C1914&style=flat-square)](#)
[![Stars](https://img.shields.io/github/stars/bjfwan/yinxing?color=B8882A&labelColor=1C1914&style=flat-square)](https://github.com/bjfwan/yinxing/stargazers)

</div>

---

## 这个项目为什么存在

我是一名大三的学生。开发这个项目，起因很简单——我的爷爷。

AI 发展的速度，今天的我们都有目共睹。有人说它不过是玩具，有人说它无所不能。但对我来说，它给了我一种可能：用自己有限的能力，让我身边的人不至于在信息化的浪潮里被边缘化。

我最初只是想做一个自己用的东西，让爷爷能更顺手地用手机。但做着做着我意识到，在 AI 的浪潮声里，我们身边还有无数的老人，正在悄悄地被这个时代甩在身后。我没有办法改变这件事，但我可以做一件小事。

这个项目也许有很多不足。市面上或许也有无数类似的轮子。但我需要一个能让我及时跟进、持续为爷爷优化的项目——所以我选择开源，希望更多人能看到这件事，也欢迎每一位愿意一起做这件事的人。

**今天，我们正式发布了它。**

---

## 银杏是什么

银杏是一个面向老年用户的 Android Launcher，目标是把手机最常用的事情做到最简单：

| | 功能 | 说明 |
|--|------|------|
| 🔤 | **大字体、大图标** | 视力不好也能看清 |
| 📞 | **一键拨号** | 直接呼叫家人 |
| 📹 | **微信视频** | 和子女视频通话 |
| 🏠 | **简洁桌面** | 去掉一切干扰 |

---

## 下载安装

<div align="center">

**[📥 立即下载 APK → yx.likeyou.qzz.io](https://yx.likeyou.qzz.io)**

</div>

> 安装前请在「设置 → 安全」中开启允许安装未知来源应用

---

## 当前功能

- 桌面主页与默认 Launcher 入口
- 应用管理与桌面应用选择
- 电话联系人查看、添加、编辑、删除、拨号
- 微信视频联系人管理与发起入口
- 低性能模式与基础系统设置入口

---

## 参与贡献

如果你也有同样想帮助身边老人的想法，欢迎提 Issue、PR，或者只是给一个 ⭐ Star——让更多人看到这件事。

```
Fork → 修改 → 提 PR → 一起让更多老人用得上
```

---

## 构建

```bash
# Debug 构建
.\gradlew.bat :app:assembleDebug

# Release 构建
.\gradlew.bat :app:assembleRelease

# 单元测试
.\gradlew.bat :app:testDebugUnitTest

# Lint
.\gradlew.bat :app:lintDebug
```

## 工程结构

```
app/          主应用模块
benchmark/    Baseline Profile 与性能测试
docs/         产品、架构、开发文档
```

## 文档

- [产品需求](docs/product/product-requirements.md)
- [当前架构](docs/architecture/current-architecture.md)
- [构建说明](docs/development/setup-and-build.md)
- [项目路线图](docs/roadmap/project-roadmap.md)

---

## 联系我

有任何想法、建议，或者你也想为身边的老人做点什么，欢迎联系：

📮 **2632507193@qq.com**

---

<div align="center">
  <sub>用代码，陪伴那些被时代遗忘的人。</sub>
</div>

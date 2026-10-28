<div align="center">

# 随堂录

**上课来不及记，就拍一张。课后让 AI 一张一张讲给你听。**

[简体中文](README.md) · [English](README_EN.md)

![platform](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![release](https://img.shields.io/badge/release-v1.5.1-2ea44f)
![size](https://img.shields.io/badge/APK-6.7%20MB-lightgrey)
![license](https://img.shields.io/badge/license-MIT-3da639)

</div>

![界面预览](docs/screenshots/overview.png)

## 它做什么

课上老师讲到关键处，你举起手机拍一张，继续听课，不用停下来抄。

课后打开这条录音，每张照片都配着一段 AI 讲解——它知道你拍的是第几分钟的内容，也知道你当时写了什么备注。看不懂就继续追问，它记得上下文。

```text
录音 ──拍照标记──▶ 课后 ──逐张讲解──▶ 追问 / 合并 / 汇编成提纲
```

录音和照片全部存在手机本地。AI 只在你要讲解时，把你选中的图发出去。

## 界面

| 回放页：逐条讲解 + 进度条标记 | 深色模式 |
|---|---|
| ![回放](docs/screenshots/playback.png) | ![深色](docs/screenshots/dark.png) |

## 功能

### 录音

- 前台服务保活，锁屏切后台继续；AAC 48kHz 立体声
- 来电或音频焦点丢失自动暂停并记录，回放页能看到截断点
- 连续一分钟没人说话提醒一次（老师走到后排、手机被书挡住）
- 可按课程表自动命名：`{课程名}_{yyyyMMdd_HHmmss}.m4a`
- 上课前按课程表提醒，提前几分钟自己填（1–60）

### 标记

- 短按：拍照 → 裁剪 → 写备注；长按：直接拍完入库，不打断听课
- 一个标记可挂多张图；录音途中能回看已拍的内容
- 课后可以重新裁剪，旧讲解自动作废、退回待发送
- 标记「待复习」（我还没弄懂），列表可只看星标

### 看图

- 全屏双指缩放（1×–6×），放大后单指拖动
- 播放条上的标记点按 AI 状态着色，点或拖都能跳

### AI 讲解

- 每个标记一轮完整对话：追问、编辑提示词、重新生成、中途停止
- 多个标记可以**合并成一次请求**，共用一条讲解（拍一页课件常要分两三张）
- 每个标记可单独设提示词；整节课可汇编成「分点提纲 + 术语表 + 三个自测问题」
- 请求前缀逐字节稳定，命中 DeepSeek 上下文缓存
- 回答用离线 markdown-it + KaTeX 渲染，公式表格代码块都支持
- 每条标记显示这条讲解花了多少钱（仅 DeepSeek 端点，按官方峰谷价估算）

### 直接对话

- 首页进入，随手问或发图，首轮结束自动生成标题，会话可回看

### 整理

- 首页时间轴 + 搜索（覆盖标题、课程名、标记备注、AI 回答与多轮追问）
- 回收站、批量选择、zip 备份导入导出（SHA-256 校验合并）
- 应用内检查更新

## 快速开始

### 方式一：直接安装

到 [Releases](https://github.com/username-1234567/voice-note-ai-tutor/releases) 下载最新的 `record-v1.5.1.apk`。

> 需要 Android 10 及以上。安装后要先在「设置 → AI 配置」填一个 API Key，否则拍照后没有讲解。

### 方式二：自己构建

```bash
git clone https://github.com/username-1234567/voice-note-ai-tutor.git
cd voice-note-ai-tutor
./gradlew assembleRelease
```

Android Studio 直接打开根目录也可以。

### API Key

默认接 DeepSeek 官方端点，也支持任何 OpenAI 兼容的 `/chat/completions`。在应用内「设置 → AI 配置」里填 base URL、模型名和 Key。

价格估算只在模型名含 `deepseek` 时显示。需要其他模型适配，欢迎提 [Issue](https://github.com/username-1234567/voice-note-ai-tutor/issues) 或自行修改。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · Room · ExoPlayer · CameraX · uCrop · OkHttp SSE · WebView 内离线 markdown-it + KaTeX

数据库已迭代到 v5，全部通过 Room Migration 升级。

## 文档

- [更新日志](CHANGELOG.md)
- [设计决策记录](docs/)

## 已知限制

- 讲解依赖云端大模型：图会上传到你配置的服务商，离线不可用
- 界面目前只有中文，需要其他语言可以提 Issue 或自行构建
- **备份包含 API Key，且不加密**，分享 zip 前请先清掉

## 数据与隐私

- 录音、图片、AI 回复只存在应用私有目录，无登录、无云同步；卸载即删除，请定期导出备份
- 标记图片、提示词与备注（作为「涉及知识点」）会发送给你配置的 AI 服务；多轮追问随上下文一起发送。除此之外无任何上传

## 关于

本项目由 AI 辅助构建，初衷是解决自己上课记不过来的问题，顺带服务有同样需求的同学。不是商业产品，没有客服，更新随缘。

## 反馈

有建议、遇到问题、或者想要某个功能，都可以提 [Issue](https://github.com/username-1234567/voice-note-ai-tutor/issues)。

如果这个东西帮你省下了抄板书的时间，点个 Star 会让更多同学看到它。

## 许可

[MIT](LICENSE) © 2026 username-1234567

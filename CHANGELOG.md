# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
aligned with `versionName` / `versionCode` in `app/build.gradle`.

Dates and bullets below are derived from **git history** in this repository.
They do **not** assert a Google Play ship date, store listing URL, or remote CI status.

## [Unreleased]

### Added

- Compose 阅读页内系统 TTS（`ReaderTtsController`）；喇叭短按朗读/停止；长按调语速。
- 外观：朗读语速预设（0.75×–1.5×）；自动翻页间隔（关 / 12s–60s）。
- 页脚显示电量（含充电标记）；正文与页脚间距、末行防裁切。

### Changed

- **移除** `LegacyReaderActivity`（经典朗读 / 自动滚动兼容界面）及所有入口；自动翻页改为按页计时。

### Fixed

## [1.0.1] — versionCode 2 — 2026-08-03

GitHub public release tag **`v1.0.1`**. `versionName` / `versionCode` in `app/build.gradle`.

### Added

- **大 TXT 秒开**：首屏正文即时可读；渐进分页 / 窗口加载，避免打开时整本 decode 或主线程 layout。
- **阅读体验**：多纸张主题、亮度、系统/自定义字体、常亮、音量键翻页、翻页动画、自动夜间、段首缩进。
- **目录**：进入目录自动滚到当前章；侧边 scrub 轨（1→20→30 式快速跳章）。
- **系统分享 / 打开方式导入**：`VIEW` / `SEND` 标签「导入到阅笺」；书库页浏览器下载 → 分享打开指引。
- **书架列表元数据路径**：`booksForListing` / `getForListing`，主壳刷新不解码多 MB 正文；`.chars` 缓存字数。
- Compose + Material 3 主壳（书架 / 书库 / 发现 / 我的）、搜索导入、书籍详情、HTTPS 远程/Web 导入、Compose `ReaderActivity`。
- 系统 share/open：`ACTION_VIEW` / `ACTION_SEND` / `ACTION_SEND_MULTIPLE`（TXT/EPUB；多 URI 上限 20）。
- EPUB：OPF `dc:title` / `dc:creator`、可选封面（≤ 2 MiB）、ZIP 备份封面。
- 章节标题识别（中文章回 + English Chapter 等）、本地 32 MiB 导入边界、编码 BOM/XML 感知。
- （历史）经典 TTS 曾经 `LegacyReaderActivity`；现已迁入 Compose 并删除该 Activity。
- 无障碍：TalkBack 角色与标签、关键列表 ≥ 48dp。
- 导入/搜索/备份/导出/离开保存：`Job.cancel` + `canAcceptUi` + JVM 纯函数测试。
- OSS：`LICENSE` **GNU GPL-3.0**、`CONTRIBUTING` / `CODE_OF_CONDUCT` / `SECURITY`、issue/PR 模板、Dependabot、CI（unit + lint + assembleDebug）、`ROADMAP` / `RELEASECHECKLIST` / README 截图。
- Auto Backup include-only 规则（库 prefs、书籍、封面）。

### Changed

- 许可证从准备开源文档阶段收敛为 **GPL-3.0** copyleft；README 重写许可证说明与截图区。
- `compileSdk` / `targetSdk` **36**；SplashScreen；显示名统一 `app_name`。
- 阅读进度主线程外防抖保存；封面解码离主线程。
- 阅读路径跳过整库 migration；大书章节扫描延后。

### Fixed

- 列表 seed migration 不再经 `books()` 全量 decode 正文。
- 渐进→全文切换时进度保持；分页前不全书 layout；末行裁切；目录定位当前章。
- Remote/Web/Search/Profile/BookDetail：取消不报失败 Toast；`CancellationException` 正确上抛。
- Search SAF 一次打开选择器；远程导入重定向后类型/标题；BookCard 未开读进度文案。

# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
aligned with `versionName` / `versionCode` in `app/build.gradle`.

Dates and bullets below are derived from **git history** in this repository.
They do **not** assert a Google Play ship date, store listing URL, or remote CI status.

## [Unreleased]

## [1.0.4] — versionCode 5 — 2026-08-16

GitHub public release tag **`v1.0.4`**.

### Fixed

- 识别「单换行 + 两个全角空格」为段落。`《大主宰》` 一类 TXT 不再把段首缩进拼进上一行，出现横向大空格。
- 大书当前页改为局部精确测量，并串联相邻页。章节标题、短段落不再在页底留下 1～3 行空白。
- 目录 / 查找跳转后从目标偏移重新填页，而不是继续使用固定字符切片。

## [1.0.3] — versionCode 4 — 2026-08-16

GitHub public release tag **`v1.0.3`**.

### Fixed

- 阅读进度滑条在按住拖动期间只更新预览，松手后仅执行一次跳转，消除正文闪屏与快速重挂载。
- 恢复中文段落两字宽首行缩进，并避免对原文已有缩进重复添加。
- 优化大书当前位置分页采样与页底空间利用。
- 将「段首缩进」入口移动到字号与行高附近。

## [1.0.2] — versionCode 3 — 2026-08-08

### Added

- Compose 阅读页内系统 TTS（`ReaderTtsController`）；喇叭短按朗读/停止；长按调语速。
- 外观：朗读语速预设（0.75×–1.5×）；自动翻页间隔（关 / 12s–60s）。
- 页脚显示电量（含充电标记）；正文与页脚间距、末行防裁切。
- 书架支持按最近阅读时间排序；筛选、排序与作者分组在配置变更后保留。
- 阅读页新增「语音管理」底部 Sheet：引擎、Android Voice（声音/语言）、语速、试听与系统 TTS 设置集中管理。
- 语音管理支持按声音名称 / 语言 / 引擎标识搜索，并可筛选本机或在线声音。
- TTS 按段落切块朗读，并在正文用背景高亮当前段落。
- 「我的」阅读概览支持今日 / 近7天 / 近30天时长切换与柱图。

### Changed

- **移除** `LegacyReaderActivity`（经典朗读 / 自动滚动兼容界面）及所有入口；自动翻页改为按页计时。
- 阅读器打开中的提示改用资源文案，并向 TalkBack 暴露礼貌播报的加载状态。
- README 截图更新为三 Tab 文案，并新增语音管理 Sheet 截图。

### Fixed

- TTS 播放 watchdog 改用合成音频真实时长，慢速中文朗读不再被人为超时截断；当前段落高亮与点按跳段稳定生效。
- 本地书库新增、改名、单书导出、全量备份与恢复改为按需读取/流式写入，不再解码或改写无关书籍正文。
- TXT/Web 导入使用严格 UTF-8 校验，正文中合法的替换字符 `�` 不会被误判为 GB18030；重定向后保留最终 HTTPS 来源。
- 大书详情、章节扫描、阅读统计、最近阅读与备份写入移出主线程；删除书籍同步移除最近阅读记录。
- 封面缩略图使用有界缓存并按文件变更失效，减少书架滚动中的重复解码与错误复用。
- 损坏的字号、亮度与 TTS 语速偏好值会被安全钳制；窄屏/大字号下统计范围选项可换行。

- 大 TXT 渐进打开与全文切换时不再重复绑定 TTS；避免旧控制器与新控制器竞争 OEM 引擎导致朗读一直停在准备中。
- TTS 引擎准备期间点击朗读会自动排队，初始化完成后无需再次点击。
- 国产系统隐藏 TTS 服务查询时仍保留已安装的 ColorOS/一加等已知引擎作为回退候选。
- ColorOS / 一加 / OPPO / realme 优先使用应用内媒体播放路由，规避 OEM 音频加固导致的静音；合成文件播放失败时回退到直接朗读。
- ColorOS 系列生成 PCM WAV 后优先用应用内 `AudioTrack` 播放，播放器不支持该格式时再回退到 `MediaPlayer`。
- TTS 合成服务无响应时增加超时恢复，避免朗读永久卡在准备或播放中。
- 朗读引擎弹窗增加不改变阅读进度的短句试听；合成/直接朗读失败时会保留当前段落并自动尝试下一个已安装引擎。
- 渐进→全文切换与 reflow 时进度门控更严，避免页码未就绪就回写错误进度。
- 精确分页 `TextMeasurer` 移出主线程；书架置顶/删除不再全库解码正文。
- 离开阅读页的 leave-save 与主壳 `ON_RESUME` 刷新等待写入完成，避免进度/时长闪旧值。

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

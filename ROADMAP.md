# 阅笺 · 路线图（ROADMAP）

工程：`BiqugeStudio` · 包名：`app.maoyankanshu.novel.selfuse` · 显示名：**阅笺**

本文档记录**已知方向与缺口**，便于贡献者对齐范围。
它**不是**发布日程、商店上线承诺，也**不**替代 [CHANGELOG.md](./CHANGELOG.md) 中已落地的变更记录。

| 文档 | 用途 |
|------|------|
| [README.md](./README.md) | 现状、构建、隐私与技术栈 |
| [RELEASECHECKLIST.md](./RELEASECHECKLIST.md) | Play 上架勾选 |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | PR 范围、密钥、测试 |
| [CHANGELOG.md](./CHANGELOG.md) | 已发布 / Unreleased 摘要 |

**范围：** 仅本仓库 `BiqugeStudio`。不要把无关项目或目录改动混进本工程的 PR。

---

## 硬约束（勿破）

| 项 | 约定 |
|----|------|
| `applicationId` | `app.maoyankanshu.novel.selfuse`（可与原版共存，勿为改名而改包名） |
| Intent `EXTRA_ID` | 恒为 **`"book_id"`**（`ReaderActivity` / `LegacyReaderActivity` / 详情等） |
| 阅读进度 | **0…1000**；书库 SharedPreferences / 文件格式勿无迁移地改 key |
| 明文 HTTP | **禁止**；在线导入仅 `https://` |
| 密钥 | `keystore.properties` / `keystore/*.jks` **永不**提交 |
| 许可证 | 未选定前**不要**自行添加 `LICENSE` 或假定 MIT/Apache/GPL |

---

## 1. 仓库与上架成熟度

维护者决策或外部托管完成后，再改代码/文案；**勿在未就绪时编造**联系邮箱、商店 URL 或远端 CI「全绿」。

| 项 | 状态 | 说明 |
|----|------|------|
| **开源许可证** | 未选定 | 属维护者明确决策；见 README「许可证」与 CONTRIBUTING |
| **隐私政策 HTTPS URL** | 正文已有、公网 URL 未托管 | [`PRIVACY.md`](./PRIVACY.md) 须托管为可匿名访问的 `https://` 后填入 Play Console |
| **安全联系渠道** | 占位 | [`SECURITY.md`](./SECURITY.md)：私密报告；邮箱/表单未发布前勿猜测 |
| **Data safety / 商店文案** | 清单就绪 | 按 README 表 + [RELEASECHECKLIST.md](./RELEASECHECKLIST.md) 填写；产品名 **阅笺** |
| **CI（GitHub Actions）** | 工作流已入库 | [`.github/workflows/android.yml`](./.github/workflows/android.yml)：JDK 17 上 unit test + lint + `assembleDebug`。**以仓库 Actions 记录为准**；文档不声称远端当前是否全绿 |
| **Dependabot** | 已配置 | Gradle + GitHub Actions，**weekly**；合并前本地跑与 CI 对齐的构建 |
| **CHANGELOG / 发版标签** | 流程已文档化 | Unreleased → `## [versionName]`；可选 annotated `v{versionName}` |
| **Play 签名与 AAB** | 本地密钥 | 见 RELEASECHECKLIST；CI **不**做 release 签名构建 |

---

## 2. 产品与工程后续（代码向）

与 README「待继续迁移」一致；实现时请单开 PR，并更新本表与 CHANGELOG。

### 2.1 阅读与朗读

| 方向 | 现状 | 备注 |
|------|------|------|
| **TTS / 语速 / 自动滚动 → Compose** | 仍经 **`LegacyReaderActivity`（Java）**；Compose `ReaderScreen` 顶栏入口跳转 | 安全迁入 Compose 后再评估是否弱化（非删除）经典 Activity |
| 翻页动画 | 未做 | 可选体验项 |
| 更多文本编码检测 | 部分 BOM/XML 已有 | 在现有 UTF-32 BOM 等基础上扩展 |

**已完成、勿再当缺口：** Compose 主阅读（`ReaderActivity` / `ReaderScreen`）、离开进度与时长安全落库（`ReaderLeaveSave`）、详情 / 搜索 / 远程·网页导入 / 个人备份的 Job 取消与 `canAcceptUi` 约定。

### 2.2 导入、书库与网络

| 方向 | 现状 | 备注 |
|------|------|------|
| **下载队列** | **无** | 多任务排队、失败重试等未实现 |
| 仅接入有权使用的内容服务 | 未做 | 勿引入无版权/无授权的扒站逻辑 |
| 评论、书单、登录等 | 未做 | 如未来需要，应设计独立且有明确隐私边界的服务端 |

**已完成、勿再当缺口：** 本地 TXT/EPUB（含 MIME `application/epub+zip`）、OPF 书名/作者、可选封面与备份、书架筛选/排序/可选作者分组、HTTPS 直链与网页导入、维基搜索导入、单书 TXT 导出（SAF CreateDocument）。

### 2.3 质量与无障碍

| 方向 | 现状 | 备注 |
|------|------|------|
| JVM 单测扩展 | 已有章节/进度/导入/取消/备份/导出等纯函数测 | 新纯逻辑优先可测 helper |
| Instrumented / Compose UI | 有 smoke 接线；**不在 CI** 默认跑 | 需设备；Espresso 3.7+ force 见 README |
| TalkBack / 触控目标 | 关键路径已覆盖 | 新 UI 保持 ≥ 48dp 与必要 `contentDescription` / polite `liveRegion` |

---

## 3. 明确不做（当前）

- 为改名或「更像原版」而改 `applicationId` / 破坏 `book_id` Intent。
- 在未选许可证时合并社区提交的 `LICENSE`。
- 在文档中写未托管的隐私 URL、未验证的商店链接、或虚构的 CI 徽章结论。
- 改动本仓库范围外的应用工程。
- 默认启用广告 / 第三方分析 SDK（若未来接入，须同步 PRIVACY、Data safety 与本路线图）。

---

## 4. 如何更新本文件

1. 方向落地 → 从「后续」移到 README「已实现」/ CHANGELOG，并在本文件标注完成或删除过时行。
2. 上架相关勾选变化 → 同步 [RELEASECHECKLIST.md](./RELEASECHECKLIST.md)。
3. 仅文档变更时：保持 **docs-only** commit；跑 `git diff --check`；勿夹带 `app/` 代码除非功能 PR。

最后与仓库源码对齐的维护说明见 git 历史；日期以 commit 为准，不在此编造里程碑日。

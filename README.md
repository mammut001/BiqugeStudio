# 阅笺

阅笺是一个本地优先的 Android TXT/EPUB 阅读器，使用 Kotlin、Jetpack Compose 和 Material 3 构建。书籍、阅读进度、书签和阅读偏好默认保存在设备本地；联网功能只在用户主动导入时使用。

## 功能

- 本地 TXT / EPUB 导入，支持 EPUB 书名、作者和可选封面
- 书架筛选、排序、作者分组、继续阅读和阅读历史
- Compose 阅读器：分页阅读、章节目录、书签、书内查找、主题、字号、行高和亮度调节
- 系统 TTS 与自动滚动（经典阅读界面）
- HTTPS 直链、授权网页和维基文库公版内容导入
- 本地书库 ZIP 备份与恢复，以及单书 TXT 导出
- TalkBack 标签、标题语义、适当的触控目标和加载状态播报

## 当前状态

这是一个可由 Android Studio 直接打开和维护的开源准备中项目。主界面、搜索/导入、书籍详情、书架和 Compose 阅读器已经迁移到当前工程；TTS/自动滚动仍由兼容界面提供。下载队列、更多编码格式和在线服务功能尚未实现。

许可证尚未选定，因此仓库暂不包含 `LICENSE`。在维护者作出决定前，请不要假定本项目采用 MIT、Apache-2.0 或 GPL 等协议。

## 构建与测试

环境要求：

- Android Studio 当前稳定版
- Android SDK 36（`compileSdk` / `targetSdk`）
- JDK 17（推荐 Android Studio 自带 JBR）
- minSdk 23

在 Android Studio 中选择 **Open**，打开 `BiqugeStudio` 根目录并运行 `app` 配置。命令行构建使用仓库内的 Gradle Wrapper：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" # macOS 示例
./gradlew :app:testDebugUnitTest :app:lint :app:assembleDebug
```

可选构建：

```bash
./gradlew :app:connectedDebugAndroidTest # 需要已连接的设备或模拟器
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Release 签名只使用本地 keystore；不要提交 `keystore.properties`、`keystore/` 或 `local.properties`。

## 隐私与网络

- 无账号、广告 SDK 或第三方分析 SDK
- 本地 TXT/EPUB 导入不需要网络
- 在线导入仅允许用户主动提供的 HTTPS 地址，以及已集成的维基文库服务
- 书库正文、封面、进度和书签不会自动上传给项目维护者
- Android 自动备份策略只纳入应用用户数据，详见 [`PRIVACY.md`](./PRIVACY.md)

上架 Google Play 前，必须将隐私政策托管到可匿名访问的 HTTPS 地址，并在 Play Console 中填写该地址。

## 参与贡献

开始前请阅读：

- [`CONTRIBUTING.md`](./CONTRIBUTING.md)：开发环境、测试、代码约定和 PR 范围
- [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md)：社区行为准则
- [`SECURITY.md`](./SECURITY.md)：漏洞报告方式
- [`ROADMAP.md`](./ROADMAP.md)：已知缺口与后续方向
- [`CHANGELOG.md`](./CHANGELOG.md)：变更记录

请保持每个 PR 聚焦于一个问题，新增逻辑优先配套 JVM 单元测试。安全问题不要通过公开 Issue 披露。

## 项目结构

```text
app/src/main/java/app/maoyankanshu/novel/selfuse/
├── MainActivity.kt                 # Compose 主壳
├── SearchActivity.kt               # 本地搜索与导入
├── BookDetailActivity.kt            # 书籍详情、导出与删除
├── RemoteImportActivity.kt          # HTTPS 直链导入
├── WebImportActivity.kt             # HTTPS 网页导入
├── ReaderActivity.kt                # Compose 主阅读器
├── LegacyReaderActivity.java        # TTS 与自动滚动兼容界面
├── ui/                              # Compose screens、reader、theme、components
└── LibraryStore / BookmarkStore / … # 本地数据层
```

`ReaderActivity.EXTRA_ID` 和 `LegacyReaderActivity.EXTRA_ID` 的值均为 `book_id`，属于现有兼容契约；修改前必须提供迁移方案。

## 文档

- [`PRIVACY.md`](./PRIVACY.md)
- [`RELEASECHECKLIST.md`](./RELEASECHECKLIST.md)
- [`ROADMAP.md`](./ROADMAP.md)
- [GitHub Issues](https://github.com/mammut001/BiqugeStudio/issues)

# 阅笺

这是一个可由 Android Studio 直接打开、独立维护的本地阅读器工程。反编译产物只作为功能迁移参考，不会被直接当作可维护源码使用。

## 显示名称与包名

| 项 | 值 |
|----|-----|
| **显示名** | `阅笺`（`app/src/main/res/values/strings.xml` → `app_name`） |
| **包名 / applicationId** | `app.maoyankanshu.novel.selfuse`（可与原版共存，**不要**为改名而改 package） |
| **工程目录 / 模块名** | `BiqugeStudio` |

`AndroidManifest` 使用 `@string/app_name`。关于页、TalkBack、`LibraryStore` 默认说明书作者、备份文件名默认值都引用同一字符串。改显示名时**只改** `app_name` 一处即可。

## Compose 主界面（当前）

主壳与二级页已基本 **Jetpack Compose + Material 3**：

| 界面 | 实现 |
|------|------|
| 书架 / 书城 / 发现 / 我的 | `MainActivity` + `ui/screens/*` |
| 搜索与导入 | `SearchActivity`（Compose） |
| 书籍详情 | `BookDetailActivity`（Compose） |
| HTTPS 直链 / 网页导入 | `RemoteImportActivity` / `WebImportActivity`（Compose） |
| 主阅读 | `ReaderActivity`（Compose，含沉浸正文、主题、字号、目录、书签、查找、朗读及亮度设置） |

- **主题**：壳跟随 `ReaderPreferences.nightMode`；阅读页有纸张/夜间/护眼。
- **导入**：本地 **TXT / EPUB**（SAF）；可选 HTTPS 维基 / 直链 / 网页。
- **无障碍**：关键控件 `contentDescription` / `heading`，触控目标 ≥ 48dp。
- **启动**：Android 12+ SplashScreen；自适应图标 `@mipmap/ic_launcher`（API 23+ 有 mipmap 回退）。

上架步骤见 [`RELEASECHECKLIST.md`](./RELEASECHECKLIST.md)。

## 阅读页：Compose 架构与经典朗读 (TTS) 回退

阅读功能主要由 **Compose `ReaderActivity`**（`ReaderScreen`）提供，用于本地 **TXT / EPUB** 文本的沉浸阅读。系统 TTS 朗读与自动滚动功能通过 **`LegacyReaderActivity`** 提供，在 `ReaderScreen` 顶部工具栏设有专属的经典朗读快捷入口按钮。

| 组件 | 路径 | 职责 |
|------|------|------|
| **Compose 阅读器** | `ReaderActivity.kt` + `ui/reader/*` | 主阅读入口。支持 TXT/EPUB 本地文本渲染、纸张/夜间/护眼主题、字号与行高调节、目录、书签、书内查找、经典朗读入口按钮、0…1000 进度连续落库 |
| **经典朗读 (TTS)** | `LegacyReaderActivity.java` | 朗读与自动滚动界面。由 `ReaderScreen` 顶栏 `VolumeUp` 图标启动，支持系统 TTS 连续朗读、语速调节与定时/自动滚动 |
| **Intent 契约** | `ReaderActivity.EXTRA_ID` / `LegacyReaderActivity.EXTRA_ID` | 恒为 **`"book_id"`**（历史常量，禁止改成 `bookid` 等，否则断链） |
| **数据层** | `LibraryStore` / `BookmarkStore` / `ReaderPreferences` / `ReadingHistory` / `ReadingStats` | 共用数据组件；进度刻度仍为 **0…1000** |

```
书架 / 详情 / 「继续阅读」
        │  putExtra("book_id", id)
        ▼
   ReaderActivity（Compose 主阅读器：TXT / EPUB）
        │  [经典朗读按钮 / AppIntents.legacyReader]
        ▼
 LegacyReaderActivity（Java 连续 TTS 朗读、语速与自动滚动）
```

**Compose 功能全覆盖**：edge-to-edge 阅读面、轻点显隐顶/底栏、页脚时间+百分比、章节正则索引、目录 sheet、上一章/下一章、书签增删跳、书内查找跳转、外观三主题、字号/行高调节、阅读亮度窗口属性、经典朗读 (TTS) 快捷入口。

**不要做的事**：不要改 `EXTRA_ID` 字符串；不要把用户书库格式/SharedPreferences key 改掉而不做迁移。

## 隐私政策 URL 与 Google Play Data safety

### 隐私政策

| 项 | 值 |
|----|-----|
| **政策正文（仓库内）** | [`PRIVACY.md`](./PRIVACY.md) |
| **公开 URL（上架必填）** | 将 `PRIVACY.md` 托管到你的 HTTPS 站点后填入，例如：`https://你的域名/yuejian-privacy` |
| **在控制台的位置** | Play Console → 应用内容 → 隐私政策 |

未托管前可用本地预览：打开仓库根目录 `PRIVACY.md`。商店上架**必须**使用可匿名访问的 `https://` URL。

### Google Play「数据安全」表单建议答案

对照当前代码行为（本地阅读 + 可选用户触发的网络导入）：

| 问题 | 建议声明 |
|------|----------|
| 是否收集或分享用户数据 | **收集**：应用功能所需的本地书库/进度/书签（**不**发送给开发者）。**分享**：否（默认）。 |
| 是否加密传输 | 与第三方通信仅 **HTTPS**（维基 / 用户 HTTPS 直链与网页）；应用层禁止 http 明文导入。本地 TXT/EPUB 不联网。 |
| 用户能否请求删除 | 可清除应用数据、删除书籍/历史；开发者侧无云端副本可删。 |
| 数据类型 → 应用活动 / 文件与文档 | 用户导入的 TXT/EPUB 与阅读正文：**仅设备**，用于阅读功能。 |
| 数据类型 → 应用信息与性能 | 无第三方分析 SDK。 |
| 数据类型 → 个人标识 / 财务 / 位置 / 通讯 | **不收集**。 |
| 是否出售数据 | **否**。 |
| 是否用于广告 | **否**。 |
| 权限 | `INTERNET`（可选在线导入）；无精准位置、通讯录、相机强制权限。 |

完整叙述见 [`PRIVACY.md`](./PRIVACY.md)。若功能变更（例如接入统计 SDK），须同步更新政策与本表。

## 打开与构建

1. 在 Android Studio 选择 **Open**，打开本文件夹。
2. 首次同步时，选择本机 Android SDK；工程使用 **compileSdk / targetSdk 36**（API 36 家族，本机可为 `platforms;android-36` 或 `android-36.1`）、**JDK 17+**（推荐 Android Studio 自带 JBR）。
3. 选择模拟器或已连接手机，点击 Run。

命令行构建与测试（需设置 `JAVA_HOME` 为 JDK 17+）：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # macOS 示例

# 日常开发
./gradlew :app:clean :app:testDebugUnitTest :app:assembleDebug

# 可选：需已连接设备/模拟器（API 35/36 需 Espresso 3.7+，工程已 force）
# ./gradlew :app:connectedDebugAndroidTest

# Play / 发行（需本地 keystore.properties，勿提交密钥）
./gradlew :app:assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew :app:bundleRelease            # app/build/outputs/bundle/release/app-release.aab  ← Play 推荐
```

### Play 上架摘要

1. 托管 [`PRIVACY.md`](./PRIVACY.md) 得到 **HTTPS 隐私政策 URL**。  
2. Play Console 填 Data safety（见上表）与商店文案（名称 **阅笺**、截图、图标 512²）。  
3. 上传 **AAB**（`bundleRelease`），启用 Play App Signing。  
4. 完整勾选清单：[`RELEASECHECKLIST.md`](./RELEASECHECKLIST.md)。

### 测试分层

| 类型 | 路径 | 命令 | 内容 |
|------|------|------|------|
| **JVM 单元测试** | `app/src/test/` | `:app:testDebugUnitTest` | `ChapterIndex` 章节正则与偏移；`ProgressMath` 进度 0…1000 与 HTTPS 校验（无设备） |
| **Instrumented / Compose** | `app/src/androidTest/` | `:app:connectedDebugAndroidTest` | Compose BOM 对齐的 UI smoke（如 `BiqugeTheme`）；需模拟器/真机 |

`androidTest` 与主工程共用 **Compose BOM `2024.10.01`**。

**API 35/36 注意：** Compose `ui-test` 会传递依赖 `espresso-core:3.5.0`，在 Android 15/16 上触发  
`InputManager.getInstance` → `NoSuchMethodException`（`InputManagerEventInjectionStrategy`）。  
工程已 **force** `espresso-core/idling-resource **3.7.0**` 与 `androidx.test:runner/core/rules **1.7.0**`，请勿降回 3.5.x。

要输出可安装的 Release 包，在 Android Studio 的 Gradle 面板执行 `app > Tasks > build > assembleRelease`。生成文件在 `app/build/outputs/apk/release/app-release.apk`，已配置 v1/v2/v3 自用签名。

## Compose 技术栈（稳定版）

| 组件 | 版本 |
|------|------|
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21（含 `org.jetbrains.kotlin.plugin.compose`） |
| Compose BOM | 2024.10.01 |
| Material 3 | 由 Compose BOM 管理 |
| Navigation Compose | 2.8.3 |
| Activity Compose | 1.9.3 |
| compileSdk / targetSdk | **36**（原 35；本机 SDK 可为 36 / 36.1） |
| minSdk | 23 |
| versionCode / versionName | **2** / **1.0.1** |
| JVM target | 17 |
| 明文 HTTP | **禁止**（`networkSecurityConfig` + Remote/Web 仅 HTTPS）；本地 TXT/EPUB 离线 |

未使用 beta / alpha 依赖。

### 目录结构（UI 与阅读）

```
app/src/main/java/app/maoyankanshu/novel/selfuse/
  MainActivity.kt                 # Compose 主壳 + Splash
  SearchActivity.kt               # Compose：本地搜索 / TXT·EPUB / 维基
  BookDetailActivity.kt           # Compose：详情 / 编辑 / 导出 / 删除
  RemoteImportActivity.kt         # Compose：HTTPS 直链 TXT·EPUB
  WebImportActivity.kt            # Compose：HTTPS 单页 HTML
  ReaderActivity.kt               # Compose 主阅读器（EXTRA_ID = "book_id"）
  LegacyReaderActivity.java       # 唯一 Java UI：TTS / 语速 / 自动滚动
  AppIntents.java                 # Intent 工厂（供 Compose 调用）
  LibraryStore / Book / …         # 数据层（Java，无 UI）
  ui/                             # 主壳 screens / reader / theme / components
```

**UI 技术划分（以代码为准）：**

| 类型 | 组件 |
|------|------|
| **Compose** | 主壳四 Tab；`SearchActivity`；`BookDetailActivity`；`RemoteImportActivity`；`WebImportActivity`；`ReaderActivity`（含 `ReaderScreen`） |
| **Java Activity 回退** | `LegacyReaderActivity`（经典朗读与自动滚动） |
| **Java 非 UI** | `LibraryStore`、`Book`、`BookmarkStore`、`EpubReader`、`AppIntents` 等 |

## 已实现

- **Compose 主壳**：Material 3 Scaffold、顶部栏、四 Tab、edge-to-edge、品牌橙 `#FFA414`、自适应图标 + Splash。
- **Compose 搜索与导入**：本地书架过滤；SAF 导入 TXT / EPUB；HTTPS 维基搜索与导入；推荐公版与完整 EPUB 直链。
- **Compose 详情**：继续/开始阅读、编辑元数据、导出 TXT、删除（二次确认）。
- **Compose 直链 / 网页导入**：仅 HTTPS；User-Agent；LibraryStore 落库。
- **Compose 阅读页**：0…1000 进度、章节/目录/书签/查找、纸张·夜间·护眼、可选中文本、经典朗读入口。
- **经典朗读回退**：`ReaderScreen` 顶栏提供入口启动 `LegacyReaderActivity`，支持连续 TTS 朗读与自动滚动。
- 本地书库 ZIP 备份与恢复；今日阅读时长；最近阅读记录。

## 迁移参考

原 APK 的反编译资料位于工作区上级目录的 `analysis/`：

- `analysis/jadx/`：便于阅读与检索的代码参考。
- `analysis/apktool/`：资源、Manifest 与 Smali 参考。

迁移时应将经过整理和测试的功能重写进本工程，不直接复制不可编译的反编译代码。

## 待继续迁移

1. 将 **TTS / 语速 / 自动滚动** 安全迁入 Compose 后，再评估是否弱化（非删除）`LegacyReaderActivity`。（`BookDetail` / `Search` / `RemoteImport` / `WebImport` **已完成** Compose，无需再列。）
2. 更多文本编码、EPUB 封面与书籍分组；翻页动画。
3. 下载队列与仅接入你有权使用的内容服务。
4. 若要恢复评论、书单、登录等功能，需要独立的新服务端。

# 笔趣阁（自用）

这是一个可由 Android Studio 直接打开、独立维护的本地阅读器工程。反编译产物只作为功能迁移参考，不会被直接当作可维护源码使用。

主界面（书架 / 书城 / 发现 / 我的）已迁移为 **Jetpack Compose + Material 3**；阅读页、搜索导入、书籍详情等仍为 Java Activity，通过 Intent 衔接。

## 打开与构建

1. 在 Android Studio 选择 **Open**，打开本文件夹。
2. 首次同步时，选择本机 Android SDK；工程使用 **Android 35、JDK 17+**（推荐 Android Studio 自带 JBR）。
3. 选择模拟器或已连接手机，点击 Run。

命令行构建（需设置 `JAVA_HOME` 为 JDK 17+）：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # macOS 示例
./gradlew :app:assembleDebug
```

要输出可安装的 Release 包，在 Android Studio 的 Gradle 面板执行 `app > Tasks > build > assembleRelease`。生成文件在 `app/build/outputs/apk/release/app-release.apk`，已配置 v1/v2/v3 自用签名。

应用包名为 `app.maoyankanshu.novel.selfuse`，因此可以与原版共存。

## Compose 技术栈（稳定版）

| 组件 | 版本 |
|------|------|
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21（含 `org.jetbrains.kotlin.plugin.compose`） |
| Compose BOM | 2024.10.01 |
| Material 3 | 由 Compose BOM 管理 |
| Navigation Compose | 2.8.3 |
| Activity Compose | 1.9.3 |
| compileSdk / targetSdk | 35 |
| minSdk | 23 |
| JVM target | 17 |

未使用 beta / alpha 依赖。

### 目录结构（Compose 相关）

```
app/src/main/java/app/maoyankanshu/novel/selfuse/
  MainActivity.kt                 # ComponentActivity + setContent + edge-to-edge
  ui/
    BiqugeApp.kt                  # Scaffold + 底部导航 + NavHost
    theme/                        # Color / Type / MaterialTheme
    navigation/MainTabs.kt        # 书架 / 书城 / 发现 / 我的
    components/BookCard.kt        # 书籍卡片（点击 / 长按）
    screens/                      # 四个主 Tab 界面
```

Java 业务层保持不变：`LibraryStore`、`Book`、`ReaderActivity`、`BookDetailActivity`、`SearchActivity` 等。

### 无障碍

- 底部导航、工具栏按钮与书籍卡片提供 TalkBack `contentDescription`
- 章节/设置标题使用 `heading` 语义
- 长按书架卡片打开操作菜单（继续阅读 / 置顶 / 编辑 / 删除）

## 已实现

- **Compose 主壳**：Material 3 Scaffold、顶部栏、四 Tab 导航、edge-to-edge、系统浅色/深色主题（品牌橙 `#FFA414`）。
- 本地 TXT / EPUB 导入（UTF-8、UTF-16、GB18030；EPUB 按标准 Spine 章节顺序）、书架、搜索、书籍详情与删除。
- 书架置顶、书名/作者编辑，以及单本书导出为标准 UTF-8 TXT。
- 用户提供直链的 TXT / EPUB 下载导入（不内置第三方书源）。
- 用户手动提供并有权使用的单页 HTML 网页导入；不内置站点规则、不批量抓取或绕过访问限制。
- 通过官方 MediaWiki API 搜索并导入中文维基文库的公共领域/自由许可文本，导入内容会保留来源链接和 CC BY-SA 许可说明。
- 离线阅读、阅读进度、章节识别、目录、书内查找、可删除书签、上一章/下一章；滚动时会同步当前章节给朗读、书签和章节导航。
- 浅色纸张/夜间/护眼暖色主题、阅读页独立亮度、字号、自动滚动、连续系统 TTS 朗读（长按朗读可调语速）。
- 本地书库 ZIP 备份与恢复。
- 本地书库和阅读概览入口。
- 今日阅读时长统计（仅保存在当前设备）。
- 本地最近阅读记录（可继续打开或一键清除）。

## 迁移参考

原 APK 的反编译资料位于工作区上级目录的 `analysis/`：

- `analysis/jadx/`：便于阅读与检索的代码参考。
- `analysis/apktool/`：资源、Manifest 与 Smali 参考。

迁移时应将经过整理和测试的功能重写进本工程，不直接复制不可编译的反编译代码。

## 待继续迁移

1. 将 `ReaderActivity` / 详情页等逐步迁到 Compose。
2. 更多文本编码、EPUB 封面与书籍分组。
3. 翻页动画、亮度、更多阅读主题和阅读统计。
4. 下载队列与仅接入你有权使用的内容服务。
5. 若要恢复评论、书单、登录等功能，需要独立的新服务端。

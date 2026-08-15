# 阅笺（Yuejian）中国大陆安卓应用商店发行准备审计

> 仓库：BiqugeStudio  
> 用户侧产品名：阅笺  
> 当前应用 ID：`app.maoyankanshu.novel.selfuse`  
> 当前版本：`1.0.2` / versionCode `3`  
> 审计目标：华为、小米、OPPO、vivo、荣耀、腾讯应用宝  
> 本文不替代法律意见；商店后台会持续变化，无法从权威公开资料确认的项目统一标记 **VERIFY IN CURRENT STORE CONSOLE**。

## 1. Executive summary

阅笺的代码形态本身非常适合“本地电子书 / TXT / EPUB 阅读工具”定位：依赖少、权限少、没有账号、广告、分析、推送或自动上传书库；本地文件通过 SAF/系统分享导入；网络能力仅在用户主动操作时用于 Wikisource、公版 EPUB 导出、HTTPS 直链或授权网页导入。

本次 hardening 重点不改变阅读功能，而是补齐中国大陆商店常见审核面：首次启动隐私同意/拒绝、应用内完整隐私政策入口、GPL/源码入口、开发者/备案配置位、网络导入定位说明、发行审计文档，以及权限最小化复核。

### 当前评分

- **代码/隐私工程准备度：约 90/100**（完成本 PR 后）
- **中国大陆正式提交准备度：约 65/100**

主要扣分来自必须由开发者人工完成的事项：真实发行主体与联系方式、APP备案、隐私政策公开 HTTPS 地址、软件著作权/电子版权材料（至少小米明确要求；其他商店以当前后台为准）、最终 release signing 证书与各商店资料。

## 2. Product classification

推荐对审核员描述为：

> **阅笺是一款本地电子书 / TXT / EPUB 阅读工具。用户通过 Android 系统文件选择器、打开方式或分享面板导入自己拥有或有权访问的文件。应用不提供在线小说书源、在线书店、商业小说聚合、账号云书库或 UGC 发布。可选联网能力仅用于用户主动指定的 HTTPS 内容导入，以及 Wikisource/Wikimedia 的公版或开放内容。**

不要将产品描述成“笔趣阁”“免费小说大全”“小说聚合”“在线书城”。历史仓库名 `BiqugeStudio` 不应作为商店展示名称。

## 3. Current implementation evidence

### Identity

- launcher label 使用 `@string/app_name`，当前为“阅笺”。
- namespace / applicationId 均为 `app.maoyankanshu.novel.selfuse`。
- 内部 `Theme.BiqugeStudio`、包结构和仓库名属于历史内部标识，本次不做无意义迁移。

### Permissions

| Permission | Declared | Actual use | Runtime permission | Release decision |
|---|---:|---|---:|---|
| `android.permission.INTERNET` | Yes | Wikisource、Wikimedia EPUB、用户指定 HTTPS 直链/网页 | No | **KEEP** |
| `android.permission.MODIFY_AUDIO_SETTINGS` | main 原有 | 当前 TTS 实现使用 AudioFocus / MediaPlayer / AudioTrack，未发现修改系统全局音频设置 | No | **REMOVE in this PR if tests remain green** |

明确不需要、也不得为上架而新增：`MANAGE_EXTERNAL_STORAGE`、`READ_EXTERNAL_STORAGE`、`READ_MEDIA_*`、定位、通讯录、电话、相机、麦克风。

### Package visibility

Manifest `<queries>` 用于发现 Android TTS_SERVICE 与少量已知 TTS 引擎包。它不是运行时权限，也不代表应用读取完整安装列表。用途必须在隐私政策中披露。

### Exported components

- `MainActivity`: exported，launcher。
- `SearchActivity`: exported，用作 TXT/EPUB Open-with / Share target。
- Reader、BookDetail、RemoteImport、WebImport：not exported。
- 所有 Activity 均显式声明 `android:exported`。

本 PR 需确保 exported `SearchActivity` 在首次通过 Open-with/Share 启动时也不能绕过隐私同意门槛。

### Local storage

- `files/books/`: 书籍正文。
- `files/covers/`: 封面。
- SharedPreferences：`local_library`、`bookmarks`、`reader_preferences`、`reading_history`、`reading_stats`。
- 无 Room/业务 SQLite 依赖。
- 本次新增 `privacy_consent`，只保存已同意的隐私政策版本号。

### Android system backup

`allowBackup=true`，并使用 include-only 的 `backup_rules.xml` / `data_extraction_rules.xml`。当前会把列出的阅读状态以及 `books/`、`covers/` 纳入系统云备份/设备迁移。该事实必须在隐私政策中明确，不能笼统宣传“数据绝不会离开设备”。

### Network/domain inventory

固定目标：

| Host | Purpose | Trigger |
|---|---|---|
| `zh.wikisource.org` | MediaWiki 搜索/页面导入 | 用户主动搜索/导入 |
| `ws-export.wmcloud.org` | Wikisource/Wikimedia EPUB 导出 | 用户主动点击公版 EPUB |

动态目标：

- 用户主动粘贴的任意 **HTTPS** TXT/EPUB URL；
- 用户主动粘贴且有权访问的 **HTTPS** 网页 URL。

安全边界：cleartext HTTP 关闭；Remote/Web importer 要求 `https://`，并在重定向后再次确认最终协议是 HTTPS；正文有大小限制。

### WebView

当前代码未发现 Android WebView。网页导入使用 `HttpURLConnection` 获取 HTML 后在本机转成纯文本。

### TTS

- 使用 Android `TextToSpeech`。
- 可发现/选择系统或已安装 TTS engine。
- 部分 ColorOS/OnePlus 设备使用 `synthesizeToFile` + app-owned MediaPlayer/AudioTrack 兼容路径。
- 用户主动朗读时，文本片段会交给所选 TTS 引擎；如果所选声音为网络声音，具体处理受 TTS 提供方政策约束。

### Third-party dependency / SDK inventory

Production dependencies are AndroidX / Jetpack Compose / Android platform components. No advertising, analytics, attribution, social login, commercial push, payment or tracking SDK is present in `app/build.gradle`.

Test-only dependencies include JUnit, Kotlin test, AndroidX Test/Espresso/Compose test.

## 4. Identity consistency audit

### User-visible product name

`app_name = 阅笺` is correct. Backup filename is derived from `app_name`, so user-visible backup name also follows 阅笺.

### Historical names

The following can remain internal and should not trigger a package-wide rename:

- repository `BiqugeStudio`;
- Kotlin/Compose theme names such as `Theme.BiqugeStudio`;
- package directory structure.

Any user-visible `BiqugeStudio` / “笔趣阁” discovered later should be changed to 阅笺 or removed unless it is explicitly explaining repository history.

### applicationId decision

Current ID: `app.maoyankanshu.novel.selfuse`.

**DECISION REQUIRED BEFORE FIRST PUBLIC RELEASE / APP备案:** whether to keep it.

Changing before the first public release can improve brand neutrality and remove historical naming, but applicationId is the app's update/store identity and affects signing continuity, APP备案 and all future updates. This PR intentionally does not migrate it.

If any public release, store record, production signing setup or APP备案 already uses the current package ID, the migration cost increases substantially.

## 5. Privacy compliance hardening in this PR

- First launch blocks the main UI until user explicitly chooses “同意并继续” or “不同意并退出”.
- Complete privacy policy can be read directly in-app before consent.
- Exported Open-with/Share import entry must use the same consent boundary.
- Settings/About gains a compliance center containing version, package ID, full privacy policy, GPL/open-source notice, source repository and (when configured) APP备案号.
- Developer name/contact and APP备案号 are source-controlled as empty release-owned resources; no fake identity or fake备案号 is invented.
- Primary `PRIVACY.md` is China-facing and no longer centered on Google Play wording.

### RELEASE BLOCKER

Before production submission, replace/configure:

- `[REQUIRED BEFORE RELEASE: developer/legal entity name]`
- `[REQUIRED BEFORE RELEASE: developer contact email]`
- public HTTPS privacy policy URL
- real APP备案号 after approval

The app-store policy page and bundled/in-app policy must describe the same behavior.

## 6. Online import risk containment

Reviewer-facing wording:

> 阅笺不提供在线书源。网络导入仅用于获取用户主动指定且有权访问的 HTTPS 内容，或 Wikisource/Wikimedia 的公版/开放内容。应用没有内置商业小说源、站点抓取规则或在线书店。

Do not remove `INTERNET` merely to look “offline”; it is legitimately used. Do not relax HTTPS restrictions to improve import success rate.

## 7. GPL/open-source distribution audit

Repository license: **GPL-3.0**.

Release experience should retain:

- GPL-3.0 identification;
- source repository URL;
- repository root `LICENSE`;
- corresponding source availability for the distributed version;
- third-party component notices/licenses as applicable.

This PR does not change the project license.

**HUMAN LEGAL REVIEW:** app-store terms can change. If a specific store's distribution terms conflict with GPL obligations, do not silently relicense; review that store's current agreement and distribution mechanics before submission.

## 8. 软件著作权 preparation

Proposed software identity (developer confirmation required):

> **阅笺本地阅读软件 V1.0**

Project-derived material:

- Purpose: local TXT/EPUB import, management and reading on Android.
- Major functions: SAF/share import, local bookshelf, TXT/EPUB parsing, pagination/reader, search, bookmarks, progress/history/preferences, TTS, local backup/restore, reading statistics, optional HTTPS/Wikisource import.
- Technical characteristics: Kotlin + Java, Jetpack Compose/Material 3, Android platform SAF/TTS, local SharedPreferences/private files, `HttpURLConnection` HTTPS networking.
- Supported Android: minSdk 23 (Android 6.0+) in current Gradle configuration; targetSdk 36.
- Current app version: 1.0.2; proposed soft-copyright display version remains a separate developer/legal naming decision.

Material preparation guidance:

1. Use a clean source snapshot matching the intended registered version.
2. Exclude signing keys, `keystore.properties`, `local.properties`, tokens and secrets.
3. Prepare source-program material and user-manual/screenshots according to the **current** China Copyright Protection Center filing instructions; exact page/line formatting should be verified in the filing system rather than copied from old guides.
4. Keep software name/version consistent with the certificate, store listing and APP备案 strategy where the target store requires consistency.

## 9. APP备案 preparation

### Automatically discoverable from project

- App name: 阅笺
- Package/applicationId: `app.maoyankanshu.novel.selfuse`
- Platform: Android
- Version: 1.0.2 / versionCode 3
- minSdk: 23
- targetSdk: 36
- Main functions: local e-book reader + optional user-initiated HTTPS/public-domain import
- Fixed network services: `zh.wikisource.org`, `ws-export.wmcloud.org`
- Dynamic network behavior: user-provided HTTPS hosts
- Permissions: after this PR, expected only `INTERNET`
- SDK inventory: AndroidX/Compose; no ad/analytics/login/push SDK
- Privacy policy source file: `PRIVACY.md`

### Must be manually supplied / derived from final release identity

- developer / organizer legal identity
- identity/business registration materials as applicable
- contact person and contact information
- final public privacy-policy HTTPS URL
- final applicationId decision
- final release signing certificate information/fingerprint required by the chosen备案接入商/store
- APP备案号 after approval
- any access-provider information requested by the备案 system

Do not derive signing fingerprints from a debug key for production paperwork.

## 10. Store-review explanation

Use a concise explanation such as:

> 阅笺是本地 TXT/EPUB 阅读工具。书籍来自用户本地文件、用户主动指定且有权访问的 HTTPS 地址，或 Wikisource/Wikimedia 公版/开放内容。应用不提供内置商业小说书源、在线书店或账号云书库。INTERNET 仅支持用户主动网络导入；TTS 通过 Android 系统/已安装语音引擎实现，因此 Manifest 包含 TTS_SERVICE/package visibility 查询。用户书库和阅读历史不会自动上传给阅笺开发者。

## 11. Per-store readiness matrix

Legend: PASS / NEEDS CHANGE / MANUAL ACTION / NOT APPLICABLE / VERIFY IN CURRENT STORE CONSOLE

| Item | Huawei | Xiaomi | OPPO | vivo | HONOR | Tencent MyApp |
|---|---|---|---|---|---|---|
| Product name in APK = 阅笺 | PASS | PASS | PASS | PASS | PASS | PASS |
| Package identity audited, no auto migration | PASS | PASS | PASS | PASS | PASS | PASS |
| Minimal permissions / no broad storage | PASS | PASS | PASS | PASS | PASS | PASS |
| HTTPS-only network boundary | PASS | PASS | PASS | PASS | PASS | PASS |
| First-run explicit privacy consent | PASS after PR | PASS after PR | PASS after PR | PASS after PR | PASS after PR | PASS after PR |
| In-app full privacy policy | PASS after PR | PASS after PR | PASS after PR | PASS after PR | PASS after PR | PASS after PR |
| Real developer identity/contact | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION |
| Public privacy-policy URL | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION |
| APP备案 completed and metadata consistent | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION |
| In-app备案号 | MANUAL ACTION after number issued | MANUAL ACTION after number issued | VERIFY IN CURRENT STORE CONSOLE | VERIFY IN CURRENT STORE CONSOLE | MANUAL ACTION after number issued | VERIFY IN CURRENT STORE CONSOLE |
| Software copyright / electronic copyright proof | VERIFY IN CURRENT STORE CONSOLE | MANUAL ACTION | VERIFY IN CURRENT STORE CONSOLE | VERIFY IN CURRENT STORE CONSOLE | VERIFY IN CURRENT STORE CONSOLE | VERIFY IN CURRENT STORE CONSOLE |
| Reading-category special commitment/qualification | VERIFY IN CURRENT STORE CONSOLE | VERIFY IN CURRENT STORE CONSOLE | VERIFY IN CURRENT STORE CONSOLE | VERIFY IN CURRENT STORE CONSOLE | VERIFY IN CURRENT STORE CONSOLE | VERIFY IN CURRENT STORE CONSOLE |
| Screenshots, icon, description, age rating/category | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION |
| Production signing identity | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION | MANUAL ACTION |

Notes from authoritative/current public material checked during this audit:

- Huawei public distribution docs require accurate privacy declaration and provide APP备案 guidance for mainland release.
- Xiaomi current publishing rules explicitly require first-run privacy consent, in-app privacy policy, APP备案 consistency, in-app clickable备案号 and software copyright/electronic copyright proof; its rules also list additional material for some “图书阅读” classifications, so final category must be verified in console.
- OPPO official developer-community publishing guidance states备案 should be prepared before app creation and un备案 apps other than offline/single-machine resources cannot be listed. Because 阅笺 contains user-initiated network import, do not assume the offline-resource exception applies.
- HONOR public review/privacy/备案 guidance requires privacy prompts, explicit accept/refuse semantics, mainland备案 handling and prominent备案 number after approval.
- For vivo and Tencent MyApp, exact current store-console evidence for every qualification item was not sufficiently verifiable from authoritative public pages during this repository audit; therefore store-specific items remain **VERIFY IN CURRENT STORE CONSOLE** instead of being guessed.

## 12. Submission checklist

Before producing/submitting a production APK:

- [ ] Decide whether `app.maoyankanshu.novel.selfuse` is the permanent public package ID.
- [ ] Confirm real developer/operating entity.
- [ ] Configure real developer contact email.
- [ ] Host the final privacy policy at a stable public HTTPS URL.
- [ ] Complete APP备案 using the final app name/package/signing identity.
- [ ] Configure the real APP备案号 in app resources after approval.
- [ ] Verify the备案 number is displayed and clickable where required.
- [ ] Obtain/prepare software copyright or accepted electronic copyright material for stores that require it; Xiaomi currently does.
- [ ] Verify whether the selected store category triggers a “图书阅读” commitment or other special material.
- [ ] Prepare final icon, screenshots, short description, long description, privacy declaration, permission explanation and review notes.
- [ ] Create/use the real release keystore outside source control; never commit signing secrets.
- [ ] Record final signing certificate fingerprint(s) required for备案/store consoles.
- [ ] Run release build and install/smoke-test the exact signed artifact.
- [ ] Re-run privacy behavior on first launch, decline, accept, Open-with/Share first-launch path, TTS, backup/restore and network import.
- [ ] Check each current store console immediately before submission for changed rules.

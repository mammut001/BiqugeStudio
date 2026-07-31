# 阅笺 · Google Play 上架检查清单

工程：`BiqugeStudio` · 包名：`app.maoyankanshu.novel.selfuse` · 显示名：**阅笺**

**不要**把 `keystore.properties`、`keystore/*.jks` 提交到公开仓库。本清单只描述流程，不包含密钥内容。

---

## 1. 隐私与 Data safety

| 项 | 状态 / 动作 |
|----|-------------|
| 政策正文 | 仓库 [`PRIVACY.md`](./PRIVACY.md) |
| **公开 HTTPS 隐私政策 URL** | [ ] 托管 `PRIVACY.md` 后填入 Play Console → 应用内容 → 隐私政策 |
| Data safety 表 | [ ] 按 README「Google Play 数据安全」表填写（本地书库；无账号/广告 SDK） |
| 联系邮箱 | [ ] 写入 `PRIVACY.md` 第 8 节 |

---

## 2. 应用标识与图标

| 项 | 说明 |
|----|------|
| `applicationId` | `app.maoyankanshu.novel.selfuse`（勿改，与原版共存） |
| 启动器图标 | `@mipmap/ic_launcher` + `@mipmap/ic_launcher_round` |
| Android 8+ 自适应 | `mipmap-anydpi-v26/ic_launcher.xml`（前景来自 logo） |
| API 23–25 | `mipmap-*/ic_launcher.png` 位图回退 |
| Splash | `Theme.BiqugeStudio.Splash` → 前景 `@mipmap/ic_launcher_foreground` |
| `versionCode` / `versionName` | 见 `app/build.gradle`（上架每次递增 `versionCode`） |

---

## 3. 构建产物（Release）

需本机 JDK 17+ 与已配置的 **本地** 签名（`keystore.properties` 指向 `keystore/*.jks`，文件已在 `.gitignore`）。

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # 示例
cd BiqugeStudio

# 可安装 APK（侧载 / 部分渠道）
./gradlew :app:assembleRelease
# 输出：app/build/outputs/apk/release/app-release.apk

# Play 推荐：Android App Bundle
./gradlew :app:bundleRelease
# 输出：app/build/outputs/bundle/release/app-release.aab
```

| 检查 | 动作 |
|------|------|
| 签名 | [ ] `assembleRelease` / `bundleRelease` 成功且非 unsigned |
| 安装验证 | [ ] 用 release APK 在真机验证冷启动 splash、书架、阅读、导入 |
| AAB 上传 | [ ] Play Console → 正式版/内部测试 → 上传 AAB |

---

## 4. Play listing（商店详情）

| 字段 | 建议 |
|------|------|
| 应用名称 | 阅笺 |
| 简短说明 | 本地 TXT/EPUB 阅读；进度、书签、离线优先 |
| 完整说明 | 强调自有文件导入、可选维基公版、无账号/无广告；注明「经典朗读」含系统 TTS |
| 图标 | 512×512（可由 logo 导出） |
| 功能图形 | 按需 |
| 手机截图 | 至少 2 张：书架、Compose 阅读页；建议再加搜索/导入、详情 |
| 分类 | 图书与工具类中择一 |
| 内容分级 | 按实际填写 |
| 目标受众 | 一般；无儿童定向收集 |
| 联系方式 | 与隐私政策一致 |

---

## 5. 功能与合规自检

| 项 | 期望 |
|----|------|
| 本地 TXT / EPUB | SAF 导入，无需网络 |
| HTTPS 直链 / 网页 | 仅 `https://`；无明文 HTTP |
| Compose UI | 主壳四 Tab；`SearchActivity`；`BookDetailActivity`；`RemoteImportActivity`；`WebImportActivity`；`ReaderActivity`（含 `ReaderScreen`） |
| 阅读与朗读 | 默认 Compose `ReaderActivity`（Intent `book_id` 不变）；`ReaderScreen` 顶栏提供 `LegacyReaderActivity` 入口用于连续 TTS 与自动滚动 |
| 进度 | 0…1000，`LibraryStore` |
| 备份 | 本地 ZIP，用户自选路径 |
| 权限 | 仅 `INTERNET`（可选在线导入） |
| 测试 | `./gradlew :app:testDebugUnitTest` 通过；有设备时 `connectedDebugAndroidTest` |

---

## 6. 密钥与安全（勿泄露）

| 项 | 说明 |
|----|------|
| `keystore.properties` | **本地**；已 gitignore |
| `keystore/*.jks` | **本地备份**；丢失则无法更新同包名应用 |
| Play App Signing | [ ] 建议启用 Google 管理的应用签名 |
| CI | 勿将 keystore 明文写入流水线日志 |

---

## 7. 上架前最后一遍

1. [ ] 隐私 URL 可公网匿名打开  
2. [ ] Data safety 与 `PRIVACY.md` 一致  
3. [ ] `versionCode` 大于商店已上线版本  
4. [ ] `bundleRelease` 成功并上传  
5. [ ] 内部测试轨装包验证主路径  
6. [ ] 商店文案无「笔趣阁」旧品牌混用（产品名：**阅笺**）  

完成以上后提交审核。

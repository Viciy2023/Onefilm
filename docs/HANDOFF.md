# OneFilm 二开工作交接

> **文档状态（2026-10-21）**：历史设计/交接/回归记录，部分内容已被后续实现覆盖。最新项目进度以 [`2026-10-21-project-status.md`](2026-10-21-project-status.md) 和 [`compose-ui-roadmap.md`](compose-ui-roadmap.md) 为准。


**日期**：2026-10-18  
**项目目录**：`F:\APK\Onefilm`  
**状态**：环境已验证（JDK 21 / Gradle 9.7.1 / AGP 9.3.1 / SDK 37 / Kotlin 2.3.20）；尚未导入代码、尚未 `git init`、尚未构建 OneFilm 本体  
**用途**：供新窗口直接接续当前进度，不需要重新调研 FongMi 结构。

---

## 1. 当前状态

- 项目目录 `F:\APK\Onefilm` 现有 FongMi 5.6.3 源码、docs、以及 `spike/media3-official` 分支。
- 已确认 **17 个 `lib-*.aar` 无法公开获取，也无法从 APK 还原**；改为按 `docs/media3-fork-plan.md` 自行实现兼容层。
- 当前分支 `spike/media3-official`：已接入官方 media3 1.8.0，编译剩 125 个错误 / 22 个定制类（见计划文档）。
- 还没有：
  - 可编译的两个 flavor
  - 身份/包名改名（Task 3–4）
  - `:core`/`:ui`/`:designsystem`（Task 6–11）
- 用户最后确认：**长期项目，先做可编译基线，再逐步恢复 MPV/libass/弹幕/DV 引擎**。
- 新窗口不要直接开始改代码；先读 `docs/media3-fork-plan.md` 与 `.superpowers/sdd/.../progress.md`。

---

## 2. 已确认的核心决策

| 项 | 结论 |
|---|---|
| 仓库方式 | 新仓库，不用 GitHub Fork；加 `upstream` remote |
| 上游 | `FongMi/TV` `fongmi` 分支，pin commit `0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e`，版本 5.6.3 |
| 许可证 | 继续 GPL-3.0，保留 LICENSE、版权头、上游归属 |
| App 名 | `OneFilm` |
| TV 包名 | `com.onefilm.android.tv` |
| 手机包名 | `com.onefilm.android.tv.mobile` |
| namespace / Java 包 | `com.onefilm.android.tv` |
| 发版形态 | 先两个 APK（TV / 手机），后续再评估单 APK 自适应 |
| UI 技术 | Compose TV + Compose Mobile |
| 模块 | `:app` + `:core` + `:designsystem` + `:ui` + `:catvod` + `:chaquo` + `:quickjs` |
| 旧 UI | 保留为 fallback，按屏幕逐步切换 |
| 播放器 | 保留原生 Activity/内核，Compose 只做外壳和后续 OSD |
| minSdk | 28（Android 9+） |
| compileSdk / targetSdk | 37 / 37 |
| Java / Kotlin | Java 21 + Kotlin 2.3.20（AGP 9 内置） |
| Compose | BOM 2026.09.00，TV Material 1.1.0，TV Foundation 1.0.0，Activity Compose 1.13.0 |

---

## 2.1 环境基线（2026-10-18 已验证）

探针工程 `C:\Users\Amy\AppData\Local\Temp\sdk37-probe`（在仓库外）实测通过，以下组合可直接用于 OneFilm：

| 项 | 实测值 | 备注 |
|---|---|---|
| JDK | JBR 21.0.11，`C:\Users\Amy\.jdks\jbr-21.0.11` | `JAVA_HOME` 已指向；PATH 里的 JDK 17 不用于构建 |
| Gradle | 9.7.1 | 上游 wrapper 就是 9.7.1；本机缓存的 9.3.1 分发损坏，勿用 |
| AGP | 9.3.1 | 跟随上游 |
| Kotlin | 2.3.20（AGP 内置） | 不要应用 `org.jetbrains.kotlin.android` |
| Compose | BOM 2026.09.00 + 插件 2.3.20 | 编译通过 |
| TV | tv-material 1.1.0、tv-foundation 1.0.0 | 编译通过 |
| Android SDK | `D:\android_sdk`，`platforms\android-37.0` | compileSdk/targetSdk 37 |
| Build-Tools | AGP 自动使用 36.0.0 | 不需要 37.0.0 |
| 设备 | 手机 Redmi 23113RKC6C / Android 16 在线 | 无 TV 设备 |
| 构建结果 | `BUILD SUCCESSFUL in 3m23s` | 探针 APK 30.7MB |

已知环境坑：

- `sdkmanager` 必须加 `--sdk_root=D:\android_sdk`，否则报 `Could not determine SDK root`。
- `build-tools;37.0.0` 下载会报 `unknown archive`；不需要它，AGP 用 36.0.0。
- `F:` 盘仅剩约 15 GB，构建产物可能吃紧，必要时把 `build` 目录放到 D:。

## 3. 已创建文件

```text
F:\APK\Onefilm\
  docs\
    architecture.md
    feature-matrix.md
    upstream.md
    package-rename.md
    HANDOFF.md
    superpowers\plans\2026-10-18-onefilm-foundation.md
```

各文件用途：

- `architecture.md`：总体架构、模块边界、UI 架构、包名、构建版本、风险。
- `feature-matrix.md`：FongMi 功能清单、TV/手机差异、新 UI 迁移阶段、回归项。
- `upstream.md`：上游 pin、GPL 合规、同步策略、第三方 AAR 审计。
- `package-rename.md`：包名 1:1 重命名范围、步骤、审计命令、常见坑。
- `2026-10-18-onefilm-foundation.md`：P0 foundation 实施计划，共 12 个任务。

---

## 4. 关键调研结论

- FongMi 当前是 **Java + Leanback/手机两套 Activity**，没有 Kotlin/Compose。
- `app` 有 `leanback` / `mobile` 两个 product flavor：
  - TV 独有：`CastActivity`、`PushActivity`、`Setting*`、DLNA Renderer、BootReceiver。
  - 手机独有：`FolderActivity`、`HistoryActivity`、`ScanActivity`、Biometric、PiP。
  - `HomeActivity`、`SearchActivity`、`VideoActivity` 等在两套 flavor 里各自实现。
- `app/src/main` 只有 `CrashActivity`、`PlaybackActivity` 等少量共享页面，不能只改 `leanback` 一个目录。
- 引擎模块：`:catvod`、`:chaquo`、`:quickjs`，行为保持不变。
- 构建：AGP 9.3.1、Java 21、compileSdk/targetSdk 37、原 minSdk 24，无 Kotlin/Compose，viewBinding，R8，ABI 分包。

### 包名重命名

- 采用 `com.fongmi.android.tv` → `com.onefilm.android.tv` 的 1:1 替换。
- 首版保留 `com.fongmi.quickjs`、`com.fongmi.hook`、`com.fongmi.chaquo`、`com.fongmi.gradle`，降低 JNI/反射/上游同步风险。
- 源码层已确认没有 `Java_com_fongmi` JNI 名；`app/src/main/python`、`assets`、`catvod` 没有 `com.fongmi` 引用。
- 仍需 P0 审计 `app/libs/*.aar`、native `.so` 是否硬编码旧包名或含再分发限制。
- XML 自定义 View、ProGuard、EventBus index、Manifest、`BuildConfig`/`R` import 都必须一起改。

---

## 5. 目标模块结构

```text
:app  (宿主，leanback/mobile flavor)
  ├─ :ui            (Compose 屏幕，leanback/mobile flavor)
  │    ├─ :core
  │    └─ :designsystem
  ├─ :core
  ├─ :designsystem
  ├─ :catvod
  ├─ :chaquo
  └─ :quickjs
```

职责：

- `:core`：bean、api、db、setting、event、非 UI utils、播放契约。
- `:designsystem`：颜色、字体、形状、Hero/卡片/Rail/Focus。
- `:ui`：Home、Search、Detail、Player OSD、Live、Settings；通过 `PlaybackLauncher` / `LegacyNavigator` 调 `:app`，不反向依赖。
- `:app`：Application、播放器 Activity、旧 UI fallback、flavor 专属能力、Compose 宿主。
- 旧 `HomeActivity` 继续做默认入口；新 `OneFilmHomeActivity` 先作为实验入口，Home 达到 parity 后再切换。

---

## 6. Foundation 计划任务

文件：`docs/superpowers/plans/2026-10-18-onefilm-foundation.md`

1. 新建仓库，导入 FongMi/TV pinned commit。
2. 让 debug/CI 在没有 release keystore 时可构建。
3. 应用身份改名（OneFilm / 版本 / 图标）。
4. 应用包与 namespace 全量重命名。
5. Kotlin/Compose build spike（AGP 9.3.1 + 内置 Kotlin 2.3.20）。
6. 创建 `:core` 并迁移 `bean`。
7. 迁移 `setting` / `event`，拆掉 `Setting` 的 UI 依赖。
8. 迁移 `api` / `db`。
9. 迁移非 UI `utils`，定义 `PlaybackRequest` / `PlaybackLauncher` / `LegacyNavigator`。
10. 创建 `:designsystem` 主题骨架。
11. 创建 `:ui`、宿主 Activity、占位首页，旧 UI fallback。
12. Foundation smoke，打 `v0.1.0-foundation`。

---

## 7. 主要风险

- 包名重命名遗漏 XML/ProGuard/EventBus，导致启动或 release 崩溃。
- AGP 9.3.1 built-in Kotlin 与 Compose 插件冲突。
- `Setting` 依赖 `R`/`ResUtil`，迁移 `:core` 时要拆 UI helper。
- `api`/`db` 可能依赖 `Setting`/`Event`，迁移顺序不能反。
- `:app` 与 `:ui` 的 flavor variant 必须匹配。
- 预编译 AAR/native 可能硬编码旧包名。
- 签名 `local.properties` 可能阻塞新仓库/CI 构建。

---

## 8. 新窗口下一步

1. 先读：
   - `F:\APK\Onefilm\docs\architecture.md`
   - `F:\APK\Onefilm\docs\package-rename.md`
   - `F:\APK\Onefilm\docs\superpowers\plans\2026-10-18-onefilm-foundation.md`
2. 环境已就绪（见 2.1），但仍需用户确认后才执行；不要直接开跑。
3. 用户确认后，从 **Task 1** 开始：
   - 新建 git 仓库（保留现有 `docs`，一起纳入首次提交）
   - 导入 FongMi/TV pin commit
   - 跑基线构建
4. 执行方式由用户选：
   - **Subagent-driven**：每任务独立实现 + 独立 review。
   - **Native**：当前会话逐任务实现，最后整体 review（用户已选此项）。

### 8.1 计划修正（2026-10-18，已写回计划）

- 删除所有 `alias libs.plugins.kotlin.android`：AGP 9 内置 Kotlin，再应用会构建失败。
- `kotlinOptions { jvmTarget = "21" }` → `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }`（放在 `android {}` 外）。
- 不需要 `android.builtInKotlin=false`；任务 5/6/10/11 的构建配置已按实测更新。

---

## 9. 尚未确认

- 是否在阶段二把 `com.fongmi.quickjs` / `hook` / `chaquo` / `gradle` 也改为 `com.onefilm.*`。
- 图片栈用 Glide 复用还是 Coil 新栈（P0 决策）。
- TV 首页导航最终用顶部导航还是左侧栏（P1 视觉稿对比）。

已确认（2026-10-18 补充）：

- 图标先用原创占位矢量（深底 + 暖金 OneFilm 字标），正式 logo 后续替换。
- 现有 `docs` 保留，纳入仓库首次提交，不清空目录。
- 执行方式：Native，当前会话逐任务。

---

## 10. 快速交接提示

新窗口可以直接使用这段作为上下文：

> 项目在 `F:\APK\Onefilm`，当前只有 docs 和 foundation 计划，没有代码。先读 `docs/HANDOFF.md`、`docs/architecture.md` 和 `docs/superpowers/plans/2026-10-18-onefilm-foundation.md`。用户已确认：新仓库、OneFilm、`com.onefilm.android.tv`、手机 `.mobile`、Compose TV+Mobile、先两个 APK、保留 FongMi 引擎和旧 UI fallback；执行方式为 Native 逐任务。环境已验证（JDK 21 / Gradle 9.7.1 / AGP 9.3.1 / SDK 37 / Kotlin 2.3.20，见 HANDOFF 2.1）。当前暂停，等用户确认后再从 Task 1 开始。

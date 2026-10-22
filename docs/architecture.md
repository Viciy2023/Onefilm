# OneFilm 架构设计

> **文档状态（2026-10-21）**：历史设计/交接/回归记录，部分内容已被后续实现覆盖。最新项目进度以 [`2026-10-21-project-status.md`](2026-10-21-project-status.md) 和 [`compose-ui-roadmap.md`](compose-ui-roadmap.md) 为准。


**状态**：Draft  
**日期**：2026-10-18  
**基座**：FongMi/TV `fongmi` 分支，commit `0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e`，版本 5.6.3  
**许可证**：GPL-3.0

## 1. 目标

OneFilm 是一个继续开源分发的影视应用，面向 Android 手机与 Android TV 大屏：

- 保留 FongMi/TV 的完整引擎与功能：`catvod`、`quickjs`、`chaquo`、播放器、直播、解析、配置、多仓、DLNA、弹幕等。
- 重写 UI 层，采用 Jetpack Compose TV + Compose Mobile，做电影感首页，与经典 TVBox 视觉切割。
- 新建独立仓库，不以 GitHub Fork 形式维护；保留 `upstream` remote，便于同步引擎修复。
- TV 与手机先分别发两个 APK；后续再评估单 APK 自适应。
- 全部衍生代码继续使用 GPL-3.0。

## 2. 非目标

- 不重写 `:catvod`、`:chaquo`、`:quickjs` 的爬虫/运行时行为。
- 不重写播放器内核（Media3/IJK/MPV/FFmpeg）。
- 不在首版移除任何 FongMi 功能。
- 不做闭源商业分发。
- 不复制 Apple TV、Disney+、Netflix 的商标、图标或受版权保护的素材；只做设计语言参考。

## 3. 关键决策

| 决策 | 结论 |
|---|---|
| 仓库方式 | 新仓库 + `upstream` remote，不用 GitHub Fork |
| 应用包名 | `com.onefilm.android.tv`（1:1 对应 `com.fongmi.android.tv`） |
| TV APK | `applicationId = com.onefilm.android.tv` |
| 手机 APK | `applicationId = com.onefilm.android.tv.mobile`（`applicationIdSuffix ".mobile"`） |
| namespace / Java 包 | `com.onefilm.android.tv` |
| 引擎包名 | 首版保留 `com.fongmi.quickjs`、`com.fongmi.hook`、`com.fongmi.chaquo`、`com.fongmi.gradle`；可选阶段二再改 |
| 发版形态 | 先两个 APK；单 APK 自适应后续评估 |
| UI 技术 | Compose TV + Compose Mobile |
| 模块结构 | `:app` + `:core` + `:designsystem` + `:ui` + 现有引擎模块 |
| 旧 UI | 保留为 fallback，按屏幕逐步切换 |
| 播放器 | 保留原生 Activity/内核，Compose 只做外壳与后续 OSD |
| minSdk | 28（Android 9+） |
| compileSdk / targetSdk | 37 / 37 |
| Java / Kotlin | Java 21 + Kotlin 2.3.20（AGP 9 内置，不再应用 kotlin-android 插件） |
| Compose | BOM 2026.09.00，TV Material 1.1.0，TV Foundation 1.0.0 |

## 4. 模块架构

```text
:app  (Android application, flavors: leanback / mobile)
  ├─ :ui            (Android library, flavors: leanback / mobile)
  │    ├─ :core
  │    └─ :designsystem
  ├─ :core
  ├─ :designsystem
  ├─ :catvod        (引擎，行为不变)
  ├─ :chaquo        (引擎，行为不变)
  └─ :quickjs       (引擎，行为不变)
```

### :app

职责：

- `Application`、引擎初始化、Room/EventBus/服务注册。
- 播放器 Activity、直播 Activity、旧版 UI Activity、flavor 专属能力。
- 新 UI 的宿主 Activity（Compose 入口）。
- `UiDependencies` 组装与注入。
- `UiRouter`：按屏幕/开关选择新 Compose UI 或旧 Activity。

保留的 flavor 专属能力：

- leanback：Leanback launcher、`CastActivity`、`PushActivity`、`Setting*`、DLNA Renderer、BootReceiver。
- mobile：`FolderActivity`、`HistoryActivity`、`ScanActivity`、Biometric、PiP、ShortcutReceiver、DLNA Cast。

### :core

职责：

- 数据模型：`bean`、VOD/直播/站点/解析结果。
- 数据访问：`api`、`db`、配置、历史、收藏、设置。
- 引擎 facade：对 `:catvod`、`:quickjs`、`:chaquo` 的调用封装。
- 播放契约：`PlaybackRequest`、`PlaybackLauncher` 等接口。
- 非 UI 工具：网络、加密、日志、线程、文件。

约束：

- 不依赖 `:app`。
- 不依赖 Compose。
- 不包含 Activity/Fragment/Dialog/Toast 等 UI 类型。
- 通过 `CoreContext` 或构造注入获得 `Context`，不再直接依赖 `App.get()`。

### :designsystem

职责：

- Compose 主题：颜色、字体、形状、间距、动效时长。
- 基础组件：`OneFilmButton`、`PosterCard`、`HeroBanner`、`ContentRail`、`FocusRing`、`TopNav`。
- TV/手机共用的纯展示组件；TV 专属组件可放在 `:ui` 的 leanback source set。

约束：

- 不依赖 `:app`、`:core`。
- 不包含业务数据模型。
- 只依赖 Compose/Material3 与基础 AndroidX。

### :ui

职责：

- Compose 屏幕：Home、Search、Detail、Library、Live、Settings、Player OSD。
- ViewModel、UI state、导航、焦点管理、动画。
- 通过 `:core` 的 repository/facade 取数据。
- 通过 `UiDependencies` 获得播放器与旧页面跳转能力。

flavor：

- `leanback`：TV 首页、TV 搜索、TV 详情、TV 设置、D-pad 焦点。
- `mobile`：手机首页、手机搜索、手机详情、底部导航、手势。
- `main`：共享导航契约、共享 ViewModel、共享 UI 模型。

约束：

- 不依赖 `:app`。
- 不直接启动 `VideoActivity` 等 `:app` 类；通过 `PlaybackLauncher` / `LegacyNavigator` 接口。
- 不直接访问引擎单例；通过 `:core` facade。

## 5. UI 架构

### 入口

```text
OneFilmHomeActivity (:app, src/main)
  -> setContent { OneFilmApp(uiDependencies) }
      -> OneFilmTheme
          -> OneFilmNavHost
              -> HomeScreen / SearchScreen / DetailScreen / ...
```

- `OneFilmHomeActivity` 放在 `:app/src/main`，两个 flavor 的 manifest 都指向它。
- 旧 `HomeActivity` 保留，作为 fallback；通过设置/调试开关切回。
- 后续每个屏幕新增 `OneFilmXxxActivity` 或在 Compose NavHost 内路由；旧 Activity 不删除，直到新屏幕通过回归。

### 状态与数据

- `:ui` ViewModel 使用 `StateFlow`/`MutableState`。
- 旧引擎回调通过 `callbackFlow`、`suspendCancellableCoroutine` 或适配器转成协程。
- `UiDependencies` 由 `:app` 构造，包含：
  - `playbackLauncher: PlaybackLauncher`
  - `legacyNavigator: LegacyNavigator`
  - `appInfo: AppInfo`
  - 必要的 repository/facade
- 不引入 Hilt/Dagger；首版使用手动依赖注入，减少迁移成本。

### 播放器

- `:app` 保留 `VideoActivity`、`LiveActivity` 及播放内核。
- `:ui` 只发 `PlaybackRequest`，由 `PlaybackLauncher` 启动原生播放器。
- 播放器 OSD 后续用 Compose overlay 实现；播放器 View 通过 `AndroidView` 或 ComposeView 叠加。
- 不把 Media3/IJK/MPV 控制逻辑迁进 Compose。

### 旧 UI fallback

- `UiRouter` 按屏幕开关选择新/旧实现。
- 首版默认：新 Home 可用，其他屏幕仍走旧 Activity。
- 每个新屏幕达到功能 parity 后才切换默认入口。
- 任何阶段都不删除旧 Activity，保证功能不丢失。

## 6. 设计系统方向

### 色彩

| Token | 值 | 用途 |
|---|---|---|
| `Ink` | `#07090C` | 最深背景 |
| `Canvas` | `#0B0E13` | 页面背景 |
| `Surface` | `#141922` | 卡片 |
| `SurfaceElevated` | `#1C2330` | 悬浮层/对话框 |
| `TextPrimary` | `#F7F8FA` | 主文字 |
| `TextSecondary` | `#A8B0BD` | 次级文字 |
| `Accent` | `#E8B45A` | 播放/焦点/品牌 |
| `AccentAlt` | `#7FD1C8` | 直播/辅助状态 |
| `Danger` | `#E06C75` | 删除/错误 |

### 字体

- 标题：系统 Serif（电影感）；如需品牌字体，使用 subset 字体，避免打包整套中文字体。
- 正文：系统 Sans / Noto Sans SC。
- TV：Hero 48–64sp，Section 28–32sp，正文 18–20sp。
- 手机：Hero 32–40sp，Section 22–26sp，正文 15–17sp。

### 首页结构

```text
[Logo] [首页] [电影] [剧集] [直播] [我的]        [搜索] [设置]
┌──────────────────────────────────────────────────────────────┐
│ 全屏 Hero backdrop                                           │
│ 渐变遮罩                                                     │
│ 标题 / 评分 / 年份 / 类型                                    │
│ [▶ 播放] [详情]                                              │
└──────────────────────────────────────────────────────────────┘
继续观看  [16:9 卡片 + 进度条] ...
本周热门  [2:3 海报] ...
新上线    [2:3 海报] ...
分类      [2:3 海报] ...
```

### 焦点与动效

- 焦点卡片：scale 1.05，暖金 2px ring + 柔光，220ms。
- TV 使用 `TvLazyRow`/`TvLazyColumn`，处理边缘焦点与返回键。
- 手机使用 `LazyColumn` + `HorizontalPager` + 底部导航。
- 避免大面积 blur、粒子和持续动画；目标 60fps。

## 7. 包名与身份

### 包名

- TV：`com.onefilm.android.tv`
- 手机：`com.onefilm.android.tv.mobile`
- namespace / Java：`com.onefilm.android.tv`
- App 名：`OneFilm`

### 重命名策略

- 1:1 替换 `com.fongmi.android.tv` → `com.onefilm.android.tv`。
- 目录 `com/fongmi/android/tv` → `com/onefilm/android/tv`。
- 更新 XML 自定义 View、ProGuard、EventBus index、Manifest、BuildConfig/R import。
- 首版保留引擎包名：`com.fongmi.quickjs`、`com.fongmi.hook`、`com.fongmi.chaquo`、`com.fongmi.gradle`。
- 不在 `README`、`UPSTREAM.md`、版权声明中抹除上游归属。

### 发布

- TV APK：`OneFilm-TV-<version>.apk`
- 手机 APK：`OneFilm-Mobile-<version>.apk`
- 每次发布打源码 tag，公开对应源码。
- 保留 `LICENSE`、`NOTICE`、版权头。

## 8. 构建与版本

| 项 | 版本 |
|---|---|
| AGP | 9.3.1（跟随 FongMi 基线） |
| Gradle | 跟随 FongMi wrapper |
| JDK | 21 |
| Kotlin | 2.3.20（AGP 9 内置，不再单独应用 kotlin-android 插件） |
| Compose BOM | 2026.09.00 |
| TV Material | 1.1.0 |
| TV Foundation | 1.0.0 |
| Activity Compose | 1.13.0 |
| minSdk | 28 |
| compileSdk / targetSdk | 37 / 37 |

构建验证：

```powershell
.\gradlew.bat :app:assembleLeanbackDebug :app:assembleMobileDebug
.\gradlew.bat :app:assembleLeanbackRelease :app:assembleMobileRelease
```

P0 必须验证：

- **已验证（2026-10-18）**：AGP 9.3.1 内置 Kotlin 2.3.20，Compose 插件 2.3.20 + BOM 2026.09.00 编译通过。不要再应用 `org.jetbrains.kotlin.android`，`jvmTarget` 用 `kotlin { compilerOptions { ... } }`；不需要 `android.builtInKotlin=false`。
- 两个 flavor 都能编译、安装、启动。
- Chaquopy、QuickJS、播放器、R8 压缩后正常。
- `local.properties` 签名配置不会阻塞 debug/CI。

## 9. 功能保留与测试

- `docs/feature-matrix.md` 为功能清单与回归矩阵。
- 每个新屏幕必须通过对应矩阵项后才切换默认入口。
- 旧 UI 保留，直到新 UI parity。
- 回归重点：配置导入、多仓、搜索、详情、播放、直播、EPG、弹幕、字幕、音轨、倍速、PiP、DLNA/投屏、历史/收藏、设置、备份恢复、外部分享/打开、开机启动。
- 引擎回归：Java/JS/Python 爬虫、解析、代理、DoH/IPv6。

## 10. 上游同步

- `upstream` 指向 `https://github.com/FongMi/TV.git`。
- 首版 pin：`0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e`，版本 5.6.3。
- 只同步引擎模块与关键播放器/安全修复；不合并上游 UI。
- 每月/每季度同步一次；每次同步后跑完整功能矩阵。
- 详见 `docs/upstream.md`。

## 11. 阶段

### P0 基础

1. 导入 FongMi/TV pinned commit，建立新仓库。
2. 应用身份/包名/图标/版本改名。
3. 修复签名配置，保证 debug/CI 可构建。
4. Kotlin/Compose build spike。
5. 抽取 `:core`、`:designsystem`、`:ui` 骨架。
6. 新 Home 壳子 + 旧 UI fallback。
7. 基线 smoke，打 `v0.1.0-foundation`。

### P1 首页与核心浏览

- 设计系统完整 token/组件。
- Home（TV/手机）。
- Search、Detail。
- 焦点、返回、加载态、错误态、空态。

### P2 播放与直播

- Player OSD。
- Live/EPG。
- 弹幕、字幕、音轨、倍速、PiP。

### P3 资料库与设置

- History、Favorites、Keep、Folder。
- Settings 全量迁移。
- 备份/恢复、更新、关于。

### P4 收尾

- 性能、无障碍、TV 焦点、手机手势。
- R8/ABI 分包/包体优化。
- 发布流程、源码 tag、文档。

## 12. 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 包名全量重命名遗漏 XML/ProGuard/EventBus | 运行时崩溃 | 机械替换 + grep 零残留 + 两 flavor 安装 smoke |
| AGP 9 built-in Kotlin 与 Compose 插件冲突 | 构建失败 | 已实测规避：不应用 `kotlin-android`，只用内置 Kotlin + `kotlin.plugin.compose`；`kotlinOptions` 改 `kotlin.compilerOptions` |
| `:core` 抽取破坏 `App.get()`/Setting/Room | 功能回归 | 分包迁移，每步编译；`CoreContext` 适配；完整 smoke |
| flavor variant 不匹配 | `:ui` 依赖解析失败 | `:ui` 使用相同 `device` flavor dimension；CI 构建两 flavor |
| Chaquopy/QuickJS/R8 受影响 | 爬虫/配置加载失败 | 保留引擎模块；R8 规则；加载 ONETV 配置回归 |
| 预编译 AAR 含 `com.fongmi` JNI/反射 | 包名改名后崩溃 | P0 扫描 `app/libs/*.aar`；必要时保留类或替换 |
| 许可证/第三方 AAR | 分发合规 | 审计 `app/libs`、native、字体、图标；保留 NOTICE |
| 品牌/商标 | 发布风险 | 发布前查 OneFilm/OneTV 商标、域名、应用商店冲突 |

## 13. 已确认与待确认

已确认：

- 手机 APK 使用 `com.onefilm.android.tv.mobile` 后缀。
- 先两个 APK，后续再评估单 APK 自适应。
- 应用名 `OneFilm`，应用包 `com.onefilm.android.tv`。

待确认：

- 是否在阶段二把 `com.fongmi.quickjs`/`hook`/`chaquo`/`gradle` 也改为 `com.onefilm.*`。
- 图片栈选 Glide 复用还是 Coil 新栈（P0 决策）。
- TV 首页导航最终采用顶部导航还是左侧栏（P1 视觉稿对比）。

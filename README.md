# OneFilm 影视

OneFilm 是一款面向 Android TV 的影视聚合客户端。项目基于 Jetpack Compose 重做电视端界面，保留了 FongMi/TV 的播放器与 Spider 引擎，支持远程配置、分类浏览、聚合搜索、详情、播放、收藏和历史。

> **重要声明**
>
> 本项目仅用于开发、联调与技术测试。README 截图中的影片、海报和接口地址仅作演示，仓库不附带任何可用的点播或直播内容。
>
> 正式发布版为**不内置任何点播、直播接口的空壳影视软件**；用户需要自行配置合法的内容接口。请勿将本项目用于传播侵权内容或违反当地法律法规的用途。

## 界面预览

截图设备：MiTV-ASTP0，1920×1080。演示内容使用《庆余年》相关搜索结果与播放画面。

### 首页

![OneFilm TV 首页](docs/screenshots/readme/home.png)

### 分类

![OneFilm TV 分类页](docs/screenshots/readme/category.png)

### 搜索

![OneFilm TV 搜索页](docs/screenshots/readme/search.png)

### 搜索结果

![OneFilm TV 搜索结果](docs/screenshots/readme/search-results.png)

### 历史

![OneFilm TV 历史页](docs/screenshots/readme/history.png)

### 详情

![OneFilm TV 详情页](docs/screenshots/readme/detail.png)

### 播放

![OneFilm TV 播放画面](docs/screenshots/readme/player.png)

> 播放器画面来自《庆余年 第二季》演示流；由于电视硬件 Surface 合成限制，系统截图只能稳定获取视频画面，播放器控件层未包含在截图中。

> 以上截图仅用于开发测试与界面说明，不代表发布版内置任何影视资源或接口。

## 主要功能

- **TV 首页**：品牌/接口信息、接口分类、导航、海报卡片。
- **分类浏览**：真实分类、筛选条件、分页加载、返回保留状态。
- **聚合搜索**：搜索历史、热词、拼音建议、并行聚合、结果缓存。
- **详情页**：海报、标题、演员、简介、线路、选集、收藏、倒序。
- **播放器**：Exo/MPV、硬解/软解、字幕/音轨/视轨、弹幕、倍速、片头片尾、续播。
- **TV 遥控器**：主页分类向下进入导航、分类显式左右焦点表、卡片 1.1 倍弹簧缩放、焦点滚动不推整页。
- **全屏播放**：控件自动隐藏，隐藏态左右 ±5 秒、确认暂停、暂停后确认继续。
- **收藏/历史**：首卡自动聚焦，详情返回恢复原卡片。
- **设置**：播放、解码、字幕、弹幕、预加载、接口、主题、DoH、播放超时、缓存清理。
- **持久化缓存**：首页、分类、详情、搜索；设置页可统一清理。
- **外部入口**：支持 `SEARCH`、`SEND`、`VIEW` Intent 进入搜索或播放器。

## 项目结构

```text
OneFilm/
├─ app/                 Android 宿主、TV/手机 flavor、旧播放器与系统能力
├─ compose/             Jetpack Compose TV/手机 UI、导航、设计系统
├─ core/                基础模型与通用逻辑
├─ catvod/              Spider/配置/网络引擎
├─ quickjs/             JavaScript Spider 运行时
├─ chaquo/              Python Spider 运行时
├─ media3compat/        Media3 兼容层
├─ mpvapi/              MPV 播放器接口
├─ docs/                项目文档与界面截图
└─ Release/apk/         本地测试 Release 产物
```

## 构建

环境要求：

- JDK 21
- Android SDK（`compileSdk` 见 `gradle/libs.versions.toml`）
- Python 3.10（Chaquopy 构建需要）
- `app/libs/` 下的配套 AAR
- 本地签名配置 `local.properties`

```properties
sdk.dir=C:/Android/Sdk
storeFile=C:/keys/your-release.jks
keyAlias=your-key-alias
storePassword=your-keystore-password
```

构建 TV 版本：

```powershell
$env:JAVA_HOME='C:\Users\Amy\.jdks\jbr-21.0.11'
.\gradlew.bat :app:assembleLeanbackDebug
.\gradlew.bat :app:assembleLeanbackRelease
```

构建手机版本：

```powershell
.\gradlew.bat :app:assembleMobileDebug
.\gradlew.bat :app:assembleMobileRelease
```

## 测试

```powershell
# 单元测试
.\gradlew.bat :compose:testDebugUnitTest :app:testLeanbackDebugUnitTest --offline

# Compose instrumentation（需要连接设备）
.\gradlew.bat :compose:connectedDebugAndroidTest --offline
```

当前 TV 真机验证：MiTV-ASTP0，Compose instrumentation 10/10 通过。

## 安装

```powershell
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 install -r "Release\apk\leanback-armeabi-v7a-debugsigned-latest.apk"
```

> `Release/apk/*-debugsigned*.apk` 仅用于本地测试，不作为正式发布包。

## 里程碑

| 阶段 | 状态 |
|---|---|
| M1 基础设施 | 100% |
| M2 数据接入 | 100% |
| M3 Compose 播放器 | 100%（播放器 UI 按当前形态保留） |
| M4 设置页 | 100%（TV 范围） |
| M5 TV 回归 | 100%（TV 核心矩阵） |
| M6 收尾 | 100%（Compose UI 范围；旧播放器/直播原生资源保留为运行时兜底） |

详细进度与验证记录见：

- [项目总进度](docs/2026-10-21-project-status.md)
- [Compose UI 路线图](docs/compose-ui-roadmap.md)
- [TV 性能基线](docs/performance-baseline.md)
- [TV 无障碍基线](docs/accessibility-baseline.md)

## 许可证与上游

OneFilm 继续使用 **GPL-3.0**，基于 [FongMi/TV](https://github.com/FongMi/TV) 的 `fongmi` 分支二次开发。

- 上游初始提交：`0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e`
- 许可证：[LICENSE.md](LICENSE.md)
- 第三方与上游声明：[NOTICE](NOTICE)
- 发布说明：[docs/release-notes-0.1.0.md](docs/release-notes-0.1.0.md)

再次强调：**仓库只提供软件外壳与开发接口，不提供任何影视内容或可用点播/直播接口；正式发布版为空壳影视软件。**

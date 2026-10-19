# OneFilm Compose UI 重写设计

- 日期：2026-10-19
- 状态：已确认，待实现计划
- 范围：全部用户界面（手机 + TV），不含播放内核与数据层重写

## 1. 背景

当前 OneFilm 的界面仍基于上游 FongMi 的 XML + Fragment/Activity 体系；Compose 只存在于冒烟页。播放内核、数据层、DLNA、MPV、弹幕、libass 等兼容能力已基本收口。

本设计的目标是与旧 UI **彻底割裂**：所有页面、控制条、弹幕设置、字幕/音轨/偏移面板全部改为 Compose，旧 XML 只保留一个临时回退开关，验收后删除。

## 2. 目标

1. 手机与 TV 的所有用户可见页面由 Compose 实现
2. 建立统一设计系统（颜色、字体、间距、圆角、动效、组件）
3. 新 UI 默认生效，旧 UI 可临时回退
4. 播放、弹幕、字幕、轨道、偏移、PiP、后台播放能力不降级
5. 新代码与旧 UI 物理隔离，避免互相污染

## 3. 非目标

- 不重写 media3 / MPV / libass / 弹幕渲染引擎
- 不重写站点解析、配置、数据库
- 不引入 Hilt / 全新 DI 框架
- 不在本阶段做 UI 多语言扩展与平板专属布局

## 4. 已确认的设计决策

| 决策 | 结果 |
|---|---|
| 视觉方向 | Apple TV 风：深色影院基底、大留白、精致排版、内容优先 |
| 配色 | 中性深色 + 青绿强调色（仅用于进度、选中、焦点） |
| 主题 | 深色默认，浅色可选 |
| 字体 | 系统字体 + 字重/字距排版；时间数字等宽 |
| 材质 | 大圆角 + 轻毛玻璃（低版本半透明纯色回退） |
| 首页结构 | 16:9 Hero + 多行 2:3 海报架 |
| 导航 | 按设备分导航：TV 顶部/侧边，手机底部 Tab，共用路由 |
| 动效 | 明显转场：共享元素、Hero 放大、页面视差，180–260ms |
| 播放器控制 | TV 底部精简条 + 右侧面板；手机手势 + 底部抽屉 |
| 弹幕 | 保留现有 Canvas 引擎绘制，设置/开关用 Compose 重写 |
| 技术落地 | 新 Compose 宿主机（单 Activity + Navigation Compose） |
| 工程结构 | 独立 `:compose` 模块承载全部新 UI |
| 过渡策略 | 新 UI 默认 + 临时回退开关；验收后删除旧 UI |
| 页面顺序 | 首页 → 详情 → 播放器 → 搜索/收藏/历史/直播/设置 |

## 5. 架构

### 5.1 模块

```
:compose  (Android Library, Compose)
  design/        设计 token、主题、通用组件、动效
  navigation/    宿主、路由、TV/手机导航外观
  feature/
    home/
    detail/
    player/
    search/
    library/     收藏 + 历史
    live/
    settings/
  bridge/        面向 app 的数据/播放器接口
  test/          Compose UI 测试

:app
  EntryActivity          新的启动分发器
  ComposeMainActivity    Compose 宿主
  旧 Activity/Fragment   仅回退期保留
```

依赖方向：

```
:app -> :compose -> :core / :media3compat / :mpvapi
```

- `:compose` 不依赖 `:app`
- 数据、配置、播放器统一通过 `bridge` 接口注入
- 不引入 Hilt；由 `:app` 提供单例实现

### 5.2 宿主与回退

- 新增 `EntryActivity` 作为 LAUNCHER：
  - `compose_ui = true`（默认）→ `ComposeMainActivity`
  - `compose_ui = false` → 旧 `HomeActivity`
- `ComposeMainActivity`：单 Activity，承载 `NavHost`
- 旧 `HomeActivity` 保留但不再是 LAUNCHER
- 回退开关放在设置页“实验性”分组，仅回退期存在
- 全页面验收通过后：删除旧 Activity/Fragment/XML、删除开关与 `EntryActivity` 分支

## 6. 设计系统

### 6.1 颜色（深色）

| Token | 值 |
|---|---|
| background | `#0A0A0C` |
| surface | `#141418` |
| surfaceElevated | `#1C1C22` |
| textPrimary | `#F5F5F7` |
| textSecondary | `#9B9BA1` |
| divider | `#2A2A31` |
| accent | `#2ED3C6` |
| accentPressed | `#25B3A8` |
| focusRing | `#2ED3C6` |
| scrim | `#000000` @ 60% |

浅色主题映射同一语义 token，保持 WCAG AA 对比度。

### 6.2 字体

- 系统字体（中文系统默认，英文系统无衬线）
- Display：34sp / 28sp，Bold
- Title：22sp / 17sp，SemiBold
- Body：15sp / 13sp，Regular
- Caption：12sp
- 时间、集数、码率使用等宽数字（`FontFeatureSettings "tnum"`）

### 6.3 形状与材质

- 卡片圆角 20dp，面板 28dp，按钮胶囊
- API 31+：轻毛玻璃（背景模糊 + 半透明）
- API < 31：半透明纯色回退，不牺牲可读性
- 焦点态：1.03 缩放 + 2dp 青绿焦点环 + 轻微提亮

### 6.4 间距

`4 / 8 / 12 / 16 / 24 / 32`；TV 安全边距 48dp；手机水平边距 16dp。

### 6.5 动效

- 页面进出：180–220ms fade + slide
- Hero → 详情：共享元素（海报/背景）
- 焦点移动：120ms 缩放/位移
- 面板：240ms 滑入 + 背景 scrim 淡入
- 低端设备或“减少动画”开启时降级为淡入淡出

## 7. 导航

### 7.1 路由

- 使用 Navigation Compose，类型安全路由
- 主要路由：`home`、`detail/{key}/{id}`、`player`、`search`、`library`、`live`、`settings`
- 返回栈按设备适配：TV 遥控器返回、手机手势返回分别验收

### 7.2 导航外观

- TV：顶部导航条或左侧导航（同一套路由）
- 手机：底部 Tab（首页/搜索/收藏/设置，直播按配置决定是否显示）
- 焦点恢复：从详情返回首页时恢复到原卡片位置

## 8. 页面设计

### 8.1 首页

- 16:9 Hero 轮播（自动轮播可关），显示标题、简介、播放/详情按钮
- 多行 2:3 海报架：继续观看、收藏、分类/推荐
- 空状态、加载骨架、错误重试
- TV：顶部导航 + Hero 焦点优先；手机：顶部分区 + 底部 Tab

### 8.2 详情

- 顶部背景大图 + 渐变遮罩
- 海报、标题、年份/地区/类型、简介展开
- 主 CTA：播放/继续观看
- 选集：网格/列表切换，当前集高亮，自动滚动到当前集
- 线路切换、收藏、搜索同源
- 剧集切换时保留播放位置与轨道偏好

### 8.3 播放器

- 视频画面：`AndroidView` 承载现有 `PlayerView` / MPV `SurfaceView`
- 覆盖层全部 Compose：顶部标题/返回、底部进度与精简控制条、手势区
- TV：底部精简条 + 右侧面板（字幕/音轨/视轨/弹幕/设置）
- 手机：单击显隐、双击播放/暂停、左右滑进度、上下滑亮度/音量、底部抽屉
- 与现有 `PlayerManager`、PiP、后台播放、MediaSession 对接
- 弹幕：现有 Canvas 引擎通过 `AndroidView` 叠加，Compose 负责开关/样式/屏蔽词/透明度/速度面板
- 字幕：libass/ASS 渲染保持现状；字幕样式与偏移面板 Compose 重写

### 8.4 搜索

- 搜索框、历史、站点建议
- 结果网格（2:3 海报）
- 空结果、加载、错误、分页

### 8.5 收藏 / 历史

- 双 Tab，网格/列表切换
- 历史显示进度、续播、删除
- 收藏支持批量管理（至少单条删除）

### 8.6 直播

- 频道分组、收藏频道、EPG（如已有数据）
- 播放器复用同一 Compose 覆盖层
- 无节目单时降级为频道列表

### 8.7 设置

- 分组列表：播放、解码、字幕、弹幕、网络、DLNA、实验性
- 开关、单选、滑块、输入框、弹窗统一组件
- 设置项与现有 `PlayerSetting` / `DecodeSetting` / `SubtitleSetting` / `PreloadSetting` 双向同步

## 9. 状态与数据

- 每页 `ViewModel` + `StateFlow<UiState>`
- UiState 不可变；一次性事件用 `Channel`/`SharedFlow`
- 复用现有单例：`VodConfig`、`SiteApi`、`History`、`PlayerManager`、`PreloadSetting`
- `bridge` 接口示例：
  - `HomeDataSource`（首页数据）
  - `DetailDataSource`（详情/选集/线路）
  - `PlaybackController`（播放、暂停、seek、轨道、偏移、弹幕）
  - `SettingsStore`（读写偏好）
- 所有阻塞调用在 `Dispatchers.IO`，Compose 层不直接触碰网络/数据库

## 10. 回退与迁移

- `compose_ui` 默认 `true`
- 回退期：设置页可切换到旧 UI；切换后重启生效
- 旧 UI 不接受新功能开发
- 全页面验收后删除旧 UI，作为独立提交，便于回滚
- 数据库、偏好结构、播放内核保持不变

## 11. 测试与验收

### 11.1 自动化

- `:compose` 内 Compose UI 测试：首页骨架、详情选集、播放器面板、设置项
- 截图基线：手机深/浅色、TV 1080p
- 现有 `docs/phase2-regression.md` 全部保持通过

### 11.2 真机

- 手机：竖屏/横屏、手势、PiP、后台、深浅主题、低端机滚动
- TV：模拟器 + 真机、遥控器焦点、D-pad 切轨/设置、返回栈
- 播放器：Exo/MPV 双引擎、字幕/音轨/偏移/弹幕

### 11.3 性能

- 首页首屏、滚动帧率、弹幕高密度、播放器面板打开耗时
- 毛玻璃在低端设备自动降级
- 内存与启动时间不得明显劣化

## 12. 里程碑

| 阶段 | 交付 | 验收 |
|---|---|---|
| M1 | `:compose` 模块、设计系统、`EntryActivity`/`ComposeMainActivity`、首页 | 新首页可启动、深浅主题、TV/手机导航 |
| M2 | 详情、选集、线路、收藏入口 | 详情→播放链路可用 |
| M3 | 播放器覆盖层、TV 侧板、手机抽屉、弹幕/字幕/音轨/偏移面板 | 双引擎播放与轨道/偏移回归通过 |
| M4 | 搜索、收藏、历史、直播、设置 | 全页面 parity |
| M5 | 删除旧 UI、动效与性能打磨 | 回归矩阵全绿 |

## 13. 风险

| 风险 | 应对 |
|---|---|
| Compose 在 TV 上的焦点/D-pad 体验 | TV 模拟器早期验证，焦点测试覆盖 |
| 毛玻璃低端机掉帧 | 设备能力检测 + 半透明回退 |
| 播放器 Surface 与 Compose 层级 | `AndroidView` + 独立 overlay 层，先做 spike |
| 新旧 UI 并行导致逻辑重复 | bridge 收口，旧 UI 冻结 |
| 回退期用户困惑 | 开关放实验性分组，默认新 UI |

## 14. 未决事项

无。设计已确认，进入实现计划。

# OneFilm Compose UI 总任务清单

> 规则：每完成一项标记 `[x]`，再进入下一项；每项完成后提交中文 commit。
> 业务逻辑优先复用 FongMi 旧类；只重写 UI。

## M1 基础设施（已完成）

- [x] `:compose` 模块与依赖
- [x] 白灰 iOS 风设计系统（深/浅色）
- [x] 通用组件：PosterCard / HeroBanner / SectionRow / FocusableSurface
- [x] Bridge 体系（Home/Detail/Category/Search/Source/ComposeDependencies）
- [x] Compose 宿主机 + Navigation Compose
- [x] 手机底部 Tab / TV 顶部导航
- [x] EntryActivity 启动路由与旧 UI 回退开关
- [x] 首页（Hero + 分类横滑 + 继续观看 + 推荐）
- [x] 分类页 + 筛选 + 分页
- [x] 详情页（简介、线路、选集、播放 CTA）
- [x] 搜索页（流式结果、快源优先、并发 32、实时进度、缓存）
- [x] 搜索历史/热词/拼音建议（复用 Setting/Word/ZhuToPin）
- [x] 来源选择页 + 搜索结果缓存
- [x] 收藏/历史/设置页面骨架

## M2 数据接入（进行中）

- [x] 收藏页接 `Keep` 真实数据（列表/打开详情/删除）
- [x] 历史页接 `History` 真实数据（进度/续播/删除）
- [x] 搜索历史点击回填与持久化完整回归
- [x] 分类筛选真实条件与分页
- [ ] 来源页换源与旧播放器兜底策略对齐
- [x] 详情页播放改为进入 Compose 播放器（取消旧 Activity 兜底）
- [x] 榜单/推荐卡片空 ID、`msearch:` ID 点击转为同款并行流式搜索
- [x] 搜索会话跨详情保留；离开搜索相关页面才取消
- [x] 海报 `@Referer` / `@User-Agent` 头解析与加载
- [x] 详情页预览秒开 + 详情内存缓存
- [x] 来源聚合搜索 6 秒上限 + 结果写回缓存

## M3 Compose 播放器（下一阶段）

- [x] `PlayerRoute` / `PlayerScreen` 路由与宿主（占位）
- [x] 选集 URL 解析：`SiteApi.playerContent(key, flag, episodeUrl)`
- [x] 视频画面 `AndroidView` 承载 PlayerView / MPV SurfaceView（ExoPlayer 起播）
- [x] Compose 控制条：播放/暂停、进度、快进快退
- [x] 扁平图标控制条：白色细进度条、倍速、选集、循环、上/下集、重播、刷新
- [x] 播放设置面板：解码/渲染/字幕/音轨/片头片尾/弹幕入口
- [x] 播放进度写回旧 `History`，详情页续播
- [x] 全屏横屏 + 沉浸式系统栏，退出恢复竖屏
- [x] 播放错误、重试、缓冲提示
- [ ] TV 底部精简条 + 右侧面板
- [ ] 手机手势：双击播放、左右进度、上下亮度/音量
- [x] 字幕轨道切换 + 字幕偏移（接入旧 PlayerManager）
- [x] 音轨/视轨切换（接入旧 TrackUtil/PlayerManager）
- [x] 弹幕开关、密度、透明度、字号 + DanmakuApi/DanmakuView
- [x] 播放引擎切换（Exo / MPV，复用旧 PlayerManager）
- [x] PiP / 后台播放 / MediaSession（已绑定 PlaybackService）
- [x] 硬解/软解、Surface/Texture、画面适应、片头/片尾、字幕/音画偏移

## M4 设置页（功能接入）

参考 FongMi 原设置项，逐项迁移到 Compose：

- [ ] 播放设置：引擎、解码、后台、缓冲、UA
- [ ] 解码设置：硬解/软解、DV 输出策略
- [ ] 字幕设置：字体、大小、颜色、描边、偏移
- [ ] 弹幕设置：开关、速度、透明度、字号、屏蔽词
- [ ] 预加载设置：开关、下一集、线程、缓存大小
- [ ] 网络与代理：代理、Hosts、超时、DNS
- [ ] DLNA 投屏设置
- [ ] 关于/版本/开源许可
- [ ] 设置项与现有 `*Setting` 类双向绑定
- [ ] 设置页 TV 焦点与手机滚动回归

## M5 TV 端回归

- [ ] TV 模拟器 / 真机安装（x86 临时 ABI 流程记录）
- [ ] 首页焦点、分类横滑、海报架焦点
- [ ] 详情页选集/线路焦点
- [ ] 播放器 D-pad 控制、面板切换
- [ ] 搜索键盘/遥控输入
- [ ] 返回栈与焦点恢复
- [ ] 性能：滚动帧率、首屏、弹幕密度

## M6 收尾

- [ ] 删除旧 XML/Fragment/Activity 与 `compose_ui` 回退
- [ ] 删除临时 bridge（DetailLauncher 旧播放器入口等）
- [ ] Compose UI 测试与截图基线
- [ ] 回归矩阵更新（搜索/收藏/历史/设置/TV）
- [ ] GPL 归属、版权页、发布文档

## 当前焦点

M2 已完成；搜索卡片/榜单卡片统一走并行流式搜索，详情页秒开预览并缓存。
M3 已完成自定义扁平控制条、倍速/选集/上下集、History 续播、横屏沉浸式与错误重试。
播放内核已切回旧 PlayerManager：MPV/Exo、轨道、解码、渲染、偏移、片头片尾均已接入。下一步：PiP/后台/MediaSession、预加载、TV 焦点与弹幕样式面板。

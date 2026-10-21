# 欧歌影视 TV 焦点系统分析

## 样本与产物

- APK：`F:\APK\huawei3798\ouge.apk`
- 反编译目录：`F:\APK\ouge-decompiled`
- 包名：`com.cctv.yangshipin.app.androidq`
- 反编译工具：JADX 1.5.6
- 说明：JADX 报告 30 个反编译错误，但 `resources`、`AndroidManifest.xml`、主要 `com.github.tvbox.osc` 源码和布局均已产出，足以分析详情页焦点与预览播放器。

## 详情页布局

证据：`F:\APK\ouge-decompiled\resources\res\layout\activity_detail.xml`

- 顶部信息区是固定高度 320dp 的水平区域：海报/预览播放器 + 标题、来源、年份、演员、简介。
- “倒序/收藏/立即播放/快速搜索”等按钮放在信息区内部，通过 `Space` + `layout_weight="1"` 推到**信息区底部**，与底部边线贴齐。
- 播放线路使用横向 `TvRecyclerView`，每项 `focusable=true`。
- 集数使用 6 列 `TvRecyclerView`，每项 `focusable=true`；当前集有选中态。
- 右上角预览播放器是 `FragmentContainerView + previewPlayerBlock`，不是 WebView。

## 焦点模型

证据：`F:\APK\ouge-decompiled\sources\com\github\tvbox\osc\ui\activity\DetailActivity.java`

- 使用 Android 原生焦点系统，不依赖 Compose 的焦点遍历。
- 详情页没有给每个按钮单独写 `nextFocus`，而是依赖：
  - `TvRecyclerView` 自己处理横向/纵向子项焦点。
  - 详情页在数据加载完成后主动把焦点放到当前集数。
  - `onKeyDown` 显式处理从线路行按下进入集数网格。
- 关键行为：
  - 数据加载后，`OooOo0o()` 只在当前没有焦点子项时，把焦点 `post` 到当前集数，避免每次刷新都抢焦点。
  - `onKeyDown` 中，焦点在线路行时按 `DPAD_DOWN`，先滚动到当前集，再进入集数网格。
  - 退出全屏时，`onBackPressed()` 恢复详情并 `requestFocus()` 到集数网格。
- 结论：欧歌不是“每个控件随便 requestFocus”，而是**由页面统一决定初始焦点、上下行跳转和返回后的焦点恢复**。

## 选中态与焦点态分离

证据：

- 线路：`item_series_flag.xml` + `shape_source_flag_focus.xml` + `shape_source_flag_line.xml`
- 集数：`item_series.xml` + `shape_source_series_focus.xml`

欧歌把“当前选中”和“当前焦点”分成两套视觉：

- 当前线路：底部蓝色下划线 `shape_source_flag_line`，即使焦点移走也保留。
- 当前焦点：白色 2mm 描边 + 深灰背景。
- 当前集数：adapter 数据中的 `selected` 表示正在播放；焦点由 `TvRecyclerView` 的 item 焦点状态表示。
- 集数列表刷新时不会无条件抢焦点，只有当前没有焦点子项时才聚焦当前集。

这正好对应我们之前的问题：只用焦点高亮表示当前线路，焦点移走后就看不出当前播放线路。

## 全屏与详情页的关系

证据：

- `DetailActivity.java` 的 `autoFullScreenAndPlay`、`OooOoo0()`。
- `com/androidx/je.java` 的 `CLICK_SERIES_AUTO_FULL_SCREEN`。

欧歌的行为：

- 详情页本身默认是“小预览播放器 + 集数列表”。
- 点击集数时，如果 `CLICK_SERIES_AUTO_FULL_SCREEN` 为 true，会主动把预览播放器扩展成 `MATCH_PARENT`，这是**有意设计的自动全屏**。
- 投屏推送等入口会传 `autoFullScreenAndPlay=true`，此时详情页直接进入全屏。
- 返回键退出全屏，再回到小窗和集数区域。

所以欧歌的“全屏”不是内嵌网页，也不是小播放器自己变成全屏；是 `DetailActivity` 的一个明确的全屏状态机。

## 预览播放器实现

证据：

- `sources/com/androidx/lf0.java`
- `com.github.tvbox.osc.player.MyVideoView`
- `xyz.doikki.videoplayer.player.VideoView`

欧歌的预览播放器是原生播放器：

- Doikki `VideoView` / `MyVideoView`
- 外层 `VodController`
- 没有 WebView 承载视频

结论：欧歌的小播放器不是内嵌网页设计。

## 对 OneFilm 的改造建议

1. 详情页焦点由页面统一管理：进入时聚焦“倒序”，从线路按向下进入当前集数，返回时恢复到集数或线路。
2. 选中态和焦点态分开：线路当前播放项保留高亮；焦点再额外加描边。
3. 小窗播放器不抢详情页焦点；只有进入全屏播放路由才让播放器默认聚焦全屏按钮。
4. 不把“自动起播”当成“自动全屏”：自动起播只挂小窗，进入全屏必须由用户操作或显式 `autoFullScreen` 状态触发。

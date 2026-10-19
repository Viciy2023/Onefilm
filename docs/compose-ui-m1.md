# Compose UI M1 验收记录

日期：2026-10-19
状态：完成（M1）

## 交付内容

- 新增 `:compose` 模块（namespace `com.onefilm.android.tv.compose`）
- 设计系统：颜色、字体、形状、间距、深/浅主题
- 通用组件：`PosterCard`、`HeroBanner`、`SectionRow`、`FocusableSurface`
- Bridge：`HomeDataSource`、`DetailLauncher`、`MediaCard`、`HomeFeed`
- app 实现：`AppHomeDataSource`、`HomeFeedMapper`、`AppDetailLauncher`
- 宿主：`ComposeMainActivity`（单 Activity + Navigation Compose）
- 自适应导航：手机底部 Tab，TV 顶部导航
- 首页：16:9 Hero + 2:3 海报架，Loading/Empty/Error/Content 四态
- 启动路由：`EntryActivity`，`compose_ui` 默认 `true`
- 回退：`compose_ui=false` 时进入旧 `HomeActivity`

## 入口与回退

- Launcher：`com.onefilm.android.tv.ui.activity.EntryActivity`
- 新 UI：`com.onefilm.android.tv.compose.host.ComposeMainActivity`
- 偏好：`compose_ui`（默认 `true`）
- 回退验证：真机切换为 `false` 后成功进入旧 `HomeActivity`，验证后已恢复 `true`

## 验证结果

| 项 | 结果 |
|---|---|
| `:compose:testDebugUnitTest` | ✅ HomeViewModel 3 例 |
| `:compose:connectedDebugAndroidTest`（TV 模拟器 API28） | ✅ 9 例全过 |
| 真机 Compose 首页（真实配置/数据） | ✅ Hero、继续观看、推荐、底部 Tab |
| 手机 Entry → Compose | ✅ |
| 回退 Entry → 旧 HomeActivity | ✅ |
| `:app:assembleMobileDebug` | ✅ |
| `:app:assembleLeanbackDebug` | ✅ |

## 已知问题 / 后续

- leanback APK 尚未在 TV 真机/模拟器安装验证（APK 仅 arm64/armeabi-v7a；模拟器需临时加 `x86` ABI 分裂）
- Hero 目前直接用海报作为背景，后续 M2 接入横版 backdrop 字段
- 首页图片加载使用 Coil；旧 UI 仍使用 Glide，待旧 UI 删除后统一
- TV 首页的安全边距/焦点细节需在 M2 用真机校准
- 设计系统的毛玻璃尚未启用（M1 为纯色半透明），M2 按设备能力开启
- 旧 XML UI 仍保留，按计划在全部页面验收后删除

## 相关提交

- `5acb910a2` 模块骨架
- `673adb860` 设计系统
- 组件 / bridge / 宿主 / 首页 / 入口分别独立提交
- 计划：`docs/superpowers/plans/2026-10-19-compose-ui-m1.md`
- 设计：`docs/superpowers/specs/2026-10-19-compose-ui-design.md`

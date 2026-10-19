# Phase 2 回归清单

**更新**：2026-10-19  
**引擎**：MPV（默认）/ ExoPlayer（可切换）

## 已自动化验证

| 项 | 方法 | 结果 |
|---|---|---|
| 冷启动 | adb + dumpsys window | ✅ |
| Exo 播放 | `PlaybackSmokeActivity` + 截图像素 | ✅ Render:1 Drop:0 |
| 弹幕渲染 | `DanmakuSmokeActivity` + 像素/截图 | ✅ |
| libass | `AssSmokeActivity` + 黄色像素统计 | ✅ 1772px |
| 章节解析 | `ChapterSmokeActivity` + uiautomator | ✅ 3 章 |
| 章节跳转 | 点击按钮 + 日志 | ✅ pos=3000 |
| MPV runtime | 独立 APK + AIDL + 截图 | ✅ stddev 78.7 |
| MPV Player 适配 | 手动 Surface + 状态日志 | ✅ pos/dur 正常 |
| MPV 暂停/恢复 | 日志 | ✅ |
| MPV seek | 日志 | ✅ before 3.0 → after 3.16 |
| MPV stop | 日志 | ✅ state=1 |
| DV 策略（MPV） | 日志 `dolby-vision-output=no` | ✅ |
| A/V 偏移（MPV） | 日志 `audio-delay=0.5` / `sub-delay=-0.3` | ✅ |
| 轨道枚举（MPV） | 日志 audio=2 sub=2（一轨一组） | ✅ |
| media3 Tracks 映射 | 日志 groups=4 | ✅ |
| 音轨切换（MPV） | `TrackUtil` → `aid 1→2`，选中态同步 | ✅ |
| 字幕切换/关闭（MPV） | `sid 1→2→0`，选中态同步 | ✅ |
| 音轨/字幕切换（Exo） | `TrackUtil` → groups 选中态 en→zh、English→Chinese→关闭 | ✅ |
| 正式播放器切轨（真机触屏） | uiautomator：设置 → 音轨/字幕 → 点行；日志 + 重开对话框选中态 | ✅ MPV：`aid 1→2`、`sid 1→2→0`；Exo：英语→中文 |
| 引擎切换（MPV↔Exo） | 控制面板点击 + `ExoPlayerImpl Init` / `MpvRuntime setSurface` 日志 | ✅ |
| PiP（MPV） | Home 键 + `dumpsys activity` + 截图 | ✅ `mode=pinned`、`mLastReportedPictureInPictureMode=true` |
| 后台播放（非 PiP） | Home 键 + `dumpsys media_session` | ✅ `PLAYING`，position 5.5s→8.6s 继续前进 |
| 偏移设置 UI（MPV） | 正式播放页 → 设置 → 字幕/音质 → 偏移滑块 | ✅ 字幕 `sub-delay` +173s→0；音频 `audio-delay` 4.3→5.8s |
| 偏移设置 UI（Exo） | 同上；日志 `OneFilmOffset ... engine=exo` | ✅ 字幕 130000→173000→0ms；音频 4400→5800ms |
| 下一集预加载（Exo） | 本地 JSON 站点双集 + `PreloadSetting` 开启 | ✅ `OneFilmPreload request can=true` → `ExoPlayerSession: preload start` → `Preload completed` |
| Exo 音频延迟崩溃修复 | 双集播放回归 | ✅ 修复 `AudioDelayProcessor` 自拷贝 `IllegalArgumentException` |
| leanback 构建/安装 | `assembleLeanbackDebug` + `pm install`（MIUI 拒绝 `adb install`） | ✅ |
| leanback 启动/播放 | 手机横屏 + Exo，60s 视频播放到 ENDED/循环 | ✅ |
| leanback 控制条 | `DPAD_CENTER` 呼出，截图可见“字幕/音轨/视轨”与焦点环 | ✅ |

## 待验证

| 项 | 说明 |
|---|---|
| ExoPlayer DV 策略 | `0003` 已实现并通过单测/接线验证，状态：待验收（缺真实 DV 样片） |
| leanback D-pad 切轨 | 手机 + MIUI 环境下方向键被系统 UI 截获；需 TV/模拟器完成 |
| DLNA | 未回归 |

## 已知限制

- MPV 的 `IMpvRuntime` 新增 `getVideoWidth/getVideoHeight`，必须同步升级 runtime APK；旧版 runtime 下 PiP 不会触发（数值回落为 0）。
- 下一集预加载仅 Exo 引擎生效，且需要同时开启“预加载”和“预加载下一集”；MPV 路径当前为空实现。
- leanback Debug APK 在 MIUI 手机上无法用 `adb install`（`INSTALL_FAILED_USER_RESTRICTED`），改用 `adb push` + `adb shell pm install -r -t --user 0`。
- MPV runtime 需要安装独立 APK 并被系统允许自启动/后台运行。
- 首次绑定前命令会 pending，连接后补发。
- `PlayerView` 不会自动把 Surface 交给 `SimpleBasePlayer`，MPV 模式由 `PlaybackActivity` 自建 SurfaceView 附加。
- 弹幕/字幕 overlay 与 MPV Surface 的层级关系仍需真机确认。
- Exo DV 的策略选择与格式重映射已由单测覆盖，但“真实 DV 片源在 DV/非 DV 设备上的成像”仍需样片与对应硬件，当前不能宣称完整通过。
- DV `ASSUME_UNSUPPORTED` 只解码 HEVC 基础层；Profile 5（IPTPQc2）会出现色彩异常，属于预期限制。

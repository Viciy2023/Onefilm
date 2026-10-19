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
| 轨道枚举（MPV） | 日志 audio=1 sub=1 | ✅ |
| media3 Tracks 映射 | 日志 groups=2 | ✅ |

## 待验证

| 项 | 说明 |
|---|---|
| 正式播放器音轨/字幕对话框 | 需要 UI 点击回归 |
| MPV 播放中的轨道切换 | `aid`/`sid` 切换后听感/画面确认 |
| ExoPlayer DV 策略 | 需要 media3 fork 渲染器补丁 + DV 片源（`0003` 未完成） |
| ExoPlayer A/V 偏移 | 音频：PCM 单元测试通过；字幕：TextRenderer 补丁已接入，待片源验证 |
| 预加载 | 当前默认关闭，需开启后验证 |
| DLNA / PiP / 后台播放 | 未回归 |

## 已知限制

- MPV runtime 需要安装独立 APK 并被系统允许自启动/后台运行。
- 首次绑定前命令会 pending，连接后补发。
- `PlayerView` 不会自动把 Surface 交给 `SimpleBasePlayer`，MPV 模式由 `PlaybackActivity` 自建 SurfaceView 附加。
- 弹幕/字幕 overlay 与 MPV Surface 的层级关系仍需真机确认。

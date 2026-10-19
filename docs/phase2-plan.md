# Phase 2 实施记录

## 状态

| 项 | 状态 | 说明 |
|---|---|---|
| 3.1 弹幕 | ✅ 可用（真机渲染验证） | Canvas 引擎，XML 解析、泳道、样式、过滤 |
| 3.2 libass | ✅ 已验证 | assrender 引擎 + App 接线；自动像素检测通过 |
| 3.3 MPV | 🔜 方案 B | 独立 runtime 模块，隔离 `libavcodec.so` |
| 3.4 Dolby Vision | 🔜 待做 | 需要自建 media3 fork |
| 3.5 预加载/偏移/选集章节 | 🔜 待做 | 部分需要 media3 fork 扩展 Player |

## 3.2 libass

- 依赖：`com.github.LumeraD3v:assrender:1.0.2`（Apache-2.0，含 native）
- 接入：`SubtitleOverlayView` + `AssHandler` + `AssExtractorsFactory`
- 待办：用带 ASS 的 MKV 验证样式/字体/动画；如 `AssExtractorsFactory` 影响非 ASS 片源，按容器类型分流

## 3.3 MPV（方案 B：独立 runtime 模块）

原因：`mpv-android-lib` 与 `lib-decoder-ffmpeg` 都带 `libavcodec.so`，同一 APK 内冲突。

设计：

```text
:app
  └─ 通过 MpvRuntimeClient 连接
:mpvruntime (独立 APK / 模块)
  ├─ mpv-android-lib (libmpv.so + 自己的 ffmpeg)
  ├─ MpvService (前台服务 + Surface 传递)
  └─ AIDL/Binder: open/pause/seek/track/surface
```

要点：

- 主 APK 不再打包 mpv 的 native，避免同名 so 冲突
- 画面通过 SurfaceControl / SurfaceView 传递
- 状态回传用 Binder 回调，主 App 的 `MpvPlayer` 只做 Player 适配
- 安装/升级由主 App 引导，版本号与主 App 解耦

## 3.5 运行时 Surface/TextureView 切换

`androidx.media3.ui.PlayerView` 1.8 没有公开 `setVideoSurfaceView/TextureView`，FongMi 是改 media3-ui 源码实现的。

结论：要实现 `setRender`、DV 策略、章节/选集、A/V 偏移，**必须自建 media3 fork**：

```text
third_party/media3/    # pin androidx/media 1.8.0
  patches/
    0001-player-view-render.patch
    0002-player-chapters-editions.patch
    0003-dolby-vision-policy.patch
    0004-av-offset.patch
```

构建产物替换 `app/libs/lib-*.aar`，与现有 17 个 AAR 结构一致。

## 下一步优先级

1. 建 `third_party/media3` 源码构建（先出干净 AAR，验证可替换）
2. 加 `PlayerView.setRender` 补丁，恢复 Surface/Texture 切换
3. 加 `Player` 章节/选集/A-V 偏移扩展
4. 建 `:mpvruntime` 模块
5. libass 素材验证

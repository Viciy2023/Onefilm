# Media3 fork 重建计划

**状态**：阶段 0 探测完成，编译未通过（预期）  
**分支**：`spike/media3-official`（commit `45181346d`）  
**日期**：2026-10-18

## 1. 背景

FongMi 5.6.3 的 `app/libs` 需要 17 个 `lib-*-release.aar`，这些 AAR 是 FongMi 私有 media3 fork 的构建产物，未公开：

`lib-common`、`lib-container`、`lib-database`、`lib-datasource`、`lib-datasource-okhttp`、`lib-datasource-rtmp`、`lib-decoder`、`lib-decoder-av1`、`lib-decoder-iamf`、`lib-decoder-ffmpeg`、`lib-exoplayer`、`lib-exoplayer-dash`、`lib-exoplayer-hls`、`lib-exoplayer-rtsp`、`lib-exoplayer-smoothstreaming`、`lib-extractor`、`lib-ui`。

已排除的获取方式：

- GitHub 全量 fork 扫描（2957 个）→ 只有项目自身的 5 个 AAR。
- NuvioTV / just-plus-player 的公开 AAR → 原版 media3，缺全部定制类。
- FongMi 5.7.7 APK 反编译（REA/JADX）→ 9444 个类被 R8 混淆到 `defpackage.*`，无 mapping，无法还原 API。

## 2. 已确认的定制 API 面

用官方 `androidx.media3:*:1.8.0` 替换后，`app` 只剩 **125 个编译错误 / 22 个缺失类**：

| 分组 | 类 | 依赖 |
|---|---|---|
| 数据/常量 | `MediaEdition`、`MediaChapter`、`StreamMetadata`、`DolbyVisionOutputPolicy`、`DanmakuConfig` | 无 |
| UI | `PlayerSeekView`、`DanmakuPlayerViewController` | media3-ui / View |
| 播放器 | `MpvPlayer`、`MpvPlayerConfig`、`MpvAndroidOptions`、`MpvSubtitleOptions`、`MpvVideoEqualizer`、`MpvAudioFilter` | libmpv / JNI |
| 字幕 | `LibassPlaybackSession`、`LibassSubtitleController`、`LibassPlayerViewController` | libass / JNI |
| 轨道/音视频 | `DecodeTrackSelector`、`SecondaryTextOutput`、`SecondaryTextTrackSelector`、`AudioTrackAudioOutputProvider` | media3-exoplayer |
| 预加载/工厂 | `DiskPreloadManager`、`MediaSourceFactorySupplier` | media3-exoplayer preload |

其中 `MediaSourceFactorySupplier` 与 `AudioTrackAudioOutputProvider` 在 media3 1.11 中是官方类；若升级到 1.11，定制面可缩到 20 个类，但标准 API 变更会带来更多编译错误（实测 242 个）。

## 3. 分阶段计划

### Phase 1：可编译基线（不追求功能等价）

目标：两个 flavor 编译、安装、ExoPlayer 基本播放可用。

1. 保留官方 media3 1.8.0 依赖。
2. 在 `app/src/main/java/androidx/media3/...` 实现 22 个兼容类：
   - 数据类按调用点补字段；
   - `MpvPlayer` 继承 `ForwardingPlayer`，默认委托 ExoPlayer 或抛 `UnsupportedOperationException`；
   - libass/danmaku/preload 先 no-op；
   - `MpvUtil.isAvailable()` 返回 `false`，设置页隐藏/禁用 MPV 与 libass 开关。
3. 用 `-Xmaxerrs 3000` 迭代到 0 错误。
4. 两个 flavor `assembleDebug` + 真机冷启动 + ExoPlayer 播放。

**验收**：`BUILD SUCCESSFUL`，首页/搜索/详情/ExoPlayer 播放可用；MPV、libass、弹幕、DV 策略明确标注“暂不可用”。

### Phase 2：恢复引擎

按依赖顺序逐个替换 no-op：

1. **libass**：接公开 libass AAR + `libmedia3ass.so`，实现 `LibassPlaybackSession` / `LibassSubtitleController`。
2. **MPV**：接 `mpv-android-lib`，让 `MpvPlayer` 真正实现 `Player` 状态机与 `libmpv` JNI。
3. **弹幕**：用 DanmakuFlameMaster 或自研 Canvas 渲染实现 `DanmakuConfig` / `DanmakuPlayerViewController`。
4. **DOVI/FFmpeg**：从官方 media3 源码构建 `decoder-ffmpeg`、`decoder-av1`、`decoder-iamf`，补 FongMi 的 DOVI P5/P7 逻辑。
5. **预加载/轨道选择**：实现 `DiskPreloadManager`、`SecondaryTextTrackSelector` 等。

每一阶段都必须通过 `docs/feature-matrix.md` 对应回归项后才算完成。

## 4. 工作方式

- 所有实验在 `spike/media3-official` 或后续 `feat/media3-*` 分支，不直接改 `main`。
- 每实现一个类，先让编译错误减少并记录到 `.superpowers/sdd/2026-10-18-onefilm-foundation/progress.md`。
- 兼容层代码统一放 `app/src/main/java/androidx/media3/...`，并在文件头注明 “OneFilm compatibility shim, not upstream media3”。
- 若之后拿到官方 17 个 AAR，直接删除兼容层并恢复 `lib-*.aar` 即可。

## 5. 许可证注意

- 官方 media3 为 Apache-2.0，兼容层新增代码按 GPL-3.0 发布（与 OneFilm 一致）。
- 不要从 APK 复制反编译代码；本计划只按公开 API 重新实现。
- MPV/libmpv（LGPL/GPL）、libass（ISC）、DanmakuFlameMaster（Apache-2.0）需在 `NOTICE` 记录来源与版本。

# Self-built media3 (fork)

我们维护一份 `androidx/media` 源码副本 + 补丁，自己编译 AAR，替换 `app/libs/lib-common|lib-ui|lib-exoplayer-release.aar`。

## 为什么

官方 media3 1.8 缺少 FongMi 依赖的扩展：

- `PlayerView.setRender`（Surface/TextureView 运行时切换）
- `Player` 的章节/选集扩展
- A/V 偏移
- Dolby Vision 输出策略

这些无法用外部 shim 实现，只能在源码层打补丁。

## 构建

```powershell
# 首次或需要重建时
powershell -File scripts/build-media3.ps1

# 指定模块
powershell -File scripts/build-media3.ps1 -Modules lib-ui
```

脚本会：

1. 克隆 `https://github.com/androidx/media.git` 到 `%TEMP%\media3-src`（tag `1.8.0`，shallow）
2. 应用 `third_party/media3/patches/*.patch`
3. 构建 `:lib-common:assembleRelease`、`:lib-ui:assembleRelease`、`:lib-exoplayer:assembleRelease`
4. 复制产物到 `app/libs/`

## 补丁

放在 `third_party/media3/patches/`，命名 `0001-xxx.patch`。

当前补丁：

- `0001-player-view-render.patch` — Surface/TextureView 切换
- `0002-player-chapters-editions.patch` — 章节/版本扩展
- `0003-dolby-vision-policy.patch` — Dolby Vision 输出策略（替代解码器 / HEVC 基础层 / 隧道开关）
- `0004-subtitle-offset.patch` — 字幕时间轴偏移 + 调试 cue 日志

应用顺序按文件编号；`0003` 只修改 `MediaCodecVideoRenderer`，`0004` 只修改 `TextRenderer`。

## 注意

- 构建需要 Android SDK（`local.properties` 的 `sdk.dir`）与 JDK 17+。
- 不要把整个 media3 源码提交进仓库；只提交补丁与脚本。
- 替换 AAR 后必须跑 `PlaybackSmokeActivity` + 真机播放回归。

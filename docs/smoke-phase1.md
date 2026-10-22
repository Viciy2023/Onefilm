# Phase 1 smoke (2026-10-19)

> **文档状态（2026-10-21）**：历史设计/交接/回归记录，部分内容已被后续实现覆盖。最新项目进度以 [`2026-10-21-project-status.md`](2026-10-21-project-status.md) 和 [`compose-ui-roadmap.md`](compose-ui-roadmap.md) 为准。


- Device: Redmi 23113RKC6C, Android 16 (SDK 36)
- APK: app-mobile-arm64-v8a-debug.apk
- Package: com.onefilm.android.tv.mobile
- Install: OK (adb install -r, Success)
- Cold start: OK, focused activity `com.onefilm.android.tv.ui.activity.HomeActivity`
- Crash buffer: empty
- Known gaps: MPV engine, libass subtitles, danmaku rendering, Dolby Vision policy,
  A/V offsets, preload, chapters/editions (see docs/media3-fork-plan.md)
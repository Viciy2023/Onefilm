# Phase 1 smoke (2026-10-19)

- Device: Redmi 23113RKC6C, Android 16 (SDK 36)
- APK: app-mobile-arm64-v8a-debug.apk
- Package: com.onefilm.android.tv.mobile
- Install: OK (adb install -r, Success)
- Cold start: OK, focused activity `com.onefilm.android.tv.ui.activity.HomeActivity`
- Crash buffer: empty
- Known gaps: MPV engine, libass subtitles, danmaku rendering, Dolby Vision policy,
  A/V offsets, preload, chapters/editions (see docs/media3-fork-plan.md)
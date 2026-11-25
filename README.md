# LiquidTikTok

An LSPosed / Xposed module that brings an iOS-style **Liquid Glass** navigation bar, top tab bar, and interactive video seekbar to TikTok, built on top of [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).

## Features

- **Liquid Glass Bottom Navigation Bar**
  - Real-time backdrop blur, lens refraction, chromatic aberration, and mercury stretch animations.
  - Captures live video frames (`SurfaceView` + UI `PixelCopy`) behind the floating pill.
  - Adapts automatically between dark and light themes across TikTok tabs (Feed, Friends, Inbox, Profile).
  - Supports native TikTok tab icons, profile avatar extraction, haptic feedback, and configurable notification badge modes (intro pill → dot, always counter, or always dot).
- **Liquid Glass Top Bar**
  - Replaces the feed top tab strip (*Following* / *For You* / etc.) and Search button with matching floating Liquid Glass pills.
- **Liquid Glass Video Seekbar**
  - Thin resting track and compact thumb while playing so it stays clear of captions and the bottom bar.
  - Expands into a translucent Liquid Glass droplet with lens refraction while scrubbing.
- **Live Settings App**
  - Adjust blur radius, bar opacity, refraction intensity, chromatic aberration, mercury stretch, bottom offset, badge mode, and toggles in real time without restarting TikTok.

## Requirements

- Android 7.0+ (`minSdk 24`)
- Rooted device with **LSPosed** (API 82+)
- TikTok (`com.zhiliaoapp.musically` or `com.ss.android.ugc.trill`)

## Installation

1. Download or build the module APK (`module-debug.apk` / `module-release.apk`).
2. Install the APK on your device.
3. Open **LSPosed Manager** → **Modules** → enable **LiquidTikTok** and ensure **TikTok** is checked in its scope.
4. Force-stop TikTok and reopen it.
5. Open **LiquidTikTok** from your launcher to customize settings live.

## Building from Source

```bash
./gradlew :module:assembleDebug
```

The output APK will be at:
`module/build/outputs/apk/debug/module-debug.apk`

## Project Structure

- `module/` — LiquidTikTok LSPosed module (`MainHook`, `LiquidTikTokBar`, `LiquidConfig`, `SettingsActivity`)
- `backdrop/` — Liquid Glass backdrop rendering library (from AndroidLiquidGlass)
- `app/` — Upstream sample/catalog components

## Credits & License

- Liquid Glass rendering powered by [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).
- Licensed under the [Apache License 2.0](LICENSE).

[中文](README.md) | English

# AMFix

An LSPosed module that fixes the issue where triple-clicking the headset button does not return to the previous track when using a wired headset with Apple Music on some Android devices. It can also replace the lyrics font with a font of your choice.

## Features

- Wired headset: single click play/pause, double click next, triple click previous
- Wired headsets only; Bluetooth headsets are unaffected
- Status detection: Apple Music running + wired headset connected + module enabled in LSPosed; "Module active" only when all three are true
- Custom lyrics font: pick any ttf / otf, lyrics page only
- Three-page UI: Home / More / About with horizontal swiping
- Dark mode, Simplified Chinese / Traditional Chinese / English

## Requirements

- Root (KernelSU / Magisk, etc.)
- LSPosed (Zygisk)
- Apple Music 7.0.0 (other versions untested)
- Android 8.0+

## Install

1. Install the APK
2. Enable the module in LSPosed and check Apple Music in its scope (built in by default)
3. Connect a wired headset
4. Open this app and tap "Restart" at the top right
5. Open Apple Music manually and press the headset button three times

## Font settings

1. Open the "More" page
2. Tap "Lyrics custom font" and pick a ttf / otf file
3. Grant root (the module copies the font into Apple Music's private directory)
4. Go back to Home, tap "Restart", then open the lyrics page in Apple Music

## Changelog

### 3.0.0

- UI rebuilt: three swipeable pages + bottom tab bar + launch splash
- New Home: AMFix title, restart button, module status, switch, tips and version info
- Module state no longer depends on the module app process being alive
- Font loading retries on failure; replacing a font file at the same path is detected
- App name unified as AMFix; "Force stop" renamed to "Restart"
- The lite build is discontinued; "lite 2.0.0" keeps working but is no longer updated

## Privacy

- No data is collected or uploaded
- Root is used only to restart (stop) Apple Music, read the LSPosed module state, and write the font and switch state into Apple Music's private directory

## Build

macOS + Android Studio bundled JBR + SDK build-tools 36:

```bash
export AMFIX_KS_PASS=your-signing-password
bash build.sh
```

The first build downloads `xposed-api.jar` and the pinned AndroidX dependencies (ViewPager2 / RecyclerView / core, see `fetch-libs.sh`) into `libs/`. Output: `build/amfix.apk`.

On Linux, replace `sips` with ImageMagick and adjust the JBR path.

## Feedback

Please include:

- LSPosed logs
- Device model, system version, Apple Music version

## AI disclosure

The code and documentation of this project were produced with the help of an AI coding assistant: **DeepSeek** (running on **DeepSeek Harness**, model **deepseek-flash**). Requirements, feature design, on-device testing and releases are done by the maintainer. AI-generated content may contain errors; rely on actual testing.

## Disclaimer

Unofficial project, not affiliated with Apple Inc. Apple Music is a trademark of Apple Inc. For personal use only, at your own risk.

## License

[MIT](LICENSE)

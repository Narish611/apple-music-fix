[中文](README.md) | English

# Apple Music Fix

An LSPosed module that fixes the issue where triple-clicking the headset button does not return to the previous track when using a wired headset with Apple Music on some Android devices.

## Features

- Wired headset: press once to play/pause, twice to skip to the next track, three times to skip to the previous track
- Wired headsets only; Bluetooth headsets are not affected
- Status detection: the module shows "Module active" only when Apple Music is running, a wired headset is connected, and the module is enabled in LSPosed
- Dark mode and Chinese/English UI

## Requirements

- Root (KernelSU / Magisk, etc.)
- LSPosed (Zygisk)
- Apple Music 7.0.0 (other versions are untested)
- Android 8.0+

## Install

1. Install the APK
2. Enable the module in LSPosed and select Apple Music in its scope (the default scope is built in)
3. Connect a wired headset
4. Open this app and tap "Force stop Apple Music"
5. Open Apple Music manually and triple-press the headset button

## Privacy

- No network permission
- No data is collected or uploaded
- Root is used only to force stop Apple Music and read the module's enabled state from LSPosed

## Build

macOS with Android Studio's bundled JBR and SDK build-tools 36:

```bash
export AMFIX_KS_PASS=your_signing_password
bash build.sh
```

The APK is written to `build/amfix.apk`. The first build creates `dsh.keystore`; do not commit it.

On Linux, replace `sips` with a tool such as ImageMagick and adjust the JBR path.

## Issues

When reporting an issue, please include:

- LSPosed logs
- Device model, OS version, and Apple Music version

## AI Disclosure

Parts of the code and documentation were written with the help of **DeepSeek**, an AI coding assistant running on **DeepSeek Harness** with the **deepseek-flash** model. Requirements, design decisions, on-device testing and releases were done by the maintainer. AI-generated content may contain mistakes; please verify with real testing.

## Disclaimer

Not affiliated with Apple Inc. Apple Music is a trademark of Apple Inc. For personal use only. Use at your own risk.

## License

[MIT](LICENSE)

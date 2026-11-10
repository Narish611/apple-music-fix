# AM 有线耳机修复 / Apple Music Fix

一个 LSPosed 模块，修复 Apple Music 安卓版在有线耳机上「三击回上一首」失效的问题。

An LSPosed module that fixes wired-headset triple-click (previous track) in Apple Music for Android.

---

## 问题 / The problem

Apple Music 7.0.0 改用 AndroidX Media3 的 `MediaSession`，媒体键交给 Media3 默认逻辑处理，只识别 1 击（播放/暂停）和 2 击（下一首）。第 3 击会被当成新的第 1 击，结果是「下一首 + 播放/暂停」，而不是上一首。

Apple Music 7.x delegates media buttons to Media3's default `MediaSession` handling, which only supports single click (play/pause) and double click (next). A third click is treated as a new single click, so triple-click becomes "next + play/pause" instead of the previous track.

其他播放器自己实现了 1/2/3 击判定，所以不受影响。

## 功能 / Features

- 有线耳机：1 击播放/暂停，2 击下一首，3 击上一首（进度 > 3 秒先回到开头）
- 只拦截有线耳机；蓝牙耳机不受影响
- 状态检测：Apple Music 正在运行 + 有线耳机已连接 + LSPosed 中模块已启用，三者同时满足才显示「模块已生效」
- 深色模式、中英文界面

## 要求 / Requirements

- Root（KernelSU / Magisk 等）
- LSPosed（Zygisk 版）
- Apple Music 7.0.0（其他版本请自行测试）
- Android 8.0+

## 安装 / Install

1. 安装 APK
2. 在 LSPosed 里启用模块，作用域勾选 Apple Music（默认已内置）
3. 插上有线耳机
4. 打开本模块，点「强制停止 Apple Music」
5. 手动打开 Apple Music，按三次耳机键

## 工作原理 / How it works

- Hook `android.media.session.MediaSession#setCallback`，再 hook 回调的 `onMediaButtonEvent(Intent)`
- 自己数 1/2/3 击，吞掉原事件，用 `MediaController.TransportControls` 分发
- 只处理 `KEYCODE_HEADSETHOOK(79)`；`KEYCODE_MEDIA_PLAY_PAUSE(85)` 仅在检测到有线耳机时处理
- 另外 hook `MediaPlaybackService.onStartCommand` 的 `ACTION_MEDIA_BUTTON` 作为兜底
- 状态检测通过 `su` 读取 `/data/adb/lspd/config/modules_config.db`

## 隐私 / Privacy

- 不申请 `INTERNET` 权限
- 不收集、不上传任何数据
- root 只用于两件事：强制停止 Apple Music、读取 LSPosed 的模块启用状态

## 构建 / Build

macOS + Android Studio 自带 JBR + SDK build-tools 36：

```bash
export AMFIX_KS_PASS=你的签名密码
bash build.sh
```

产物在 `build/amfix.apk`。首次构建会生成 `dsh.keystore`，请勿提交。

Linux 下需要把 `sips` 换成 ImageMagick 之类的工具，并调整 JBR 路径。

## 版本规则 / Versioning

- versionName 第三位 +1：只改应用内文字
- versionName 第二位 +1：改机制
- versionCode：`yyMMddHH`（北京时间，24 小时制）

## 已知限制 / Known limitations

- 只测试了 Apple Music 7.0.0
- 有线耳机单击有约 0.4 秒判定延迟
- 需要 root；没有 root 无法强制停止和读取 LSPosed 状态

## 反馈 / Issues

遇到问题请附上：

- LSPosed 日志（搜索 `AMFix`）
- `logcat -d | grep AMFix` 的输出
- 设备型号、系统版本、Apple Music 版本

## 免责声明 / Disclaimer

非官方项目，与 Apple Inc. 无关。Apple Music 是 Apple Inc. 的商标。使用风险自负。

Not affiliated with Apple Inc. Apple Music is a trademark of Apple Inc. Use at your own risk.

## 许可证 / License

MIT

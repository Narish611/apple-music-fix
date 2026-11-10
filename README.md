# AM 有线耳机修复 / Apple Music Fix

一个 LSPosed 模块，修复部分安卓设备在 Apple Music 上使用有线耳机时使用耳机键三击回到上一首功能失效的问题。

An LSPosed module that fixes wired-headset triple-click (previous track) in Apple Music for Android.

---

## 功能 / Features

- 有线耳机：按一次播放/暂停，按两次下一首，按三次上一首
- 只修复有线耳机；蓝牙耳机不受影响
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
5. 手动打开 Apple Music，按三次耳机键即可正常使用

## 隐私 / Privacy

- 没有网络权限
- 不收集、不上传任何数据
- root 只用于：强制停止 Apple Music、读取 LSPosed 的模块启用状态

## 构建 / Build

macOS + Android Studio 自带 JBR + SDK build-tools 36：

```bash
export AMFIX_KS_PASS=你的签名密码
bash build.sh
```

产物在 `build/amfix.apk`。首次构建会生成 `dsh.keystore`，请勿提交。

Linux 下需要把 `sips` 换成 ImageMagick 之类的工具，并调整 JBR 路径。

## 反馈 / Issues

遇到问题请附上：

- LSPosed日志
- 设备型号、系统版本、Apple Music 版本

## AI 声明 / AI Disclosure

本项目的代码和文档在 AI 编程助手的协助下完成：**DeepSeek**（运行在 **DeepSeek Harness** 上，模型为 **deepseek-flash**）。需求确认、功能设计、真机测试和发布由维护者完成。AI 生成的内容可能存在错误，请以实际测试为准。

Parts of the code and documentation were written with the help of **DeepSeek**, an AI coding assistant running on **DeepSeek Harness** with the **deepseek-flash** model. Requirements, design decisions, on-device testing and releases were done by the maintainer. AI-generated content may contain mistakes; please verify with real testing.

## 免责声明 / Disclaimer

非官方项目，与 Apple Inc. 无关。Apple Music 是 Apple Inc. 的商标。使用风险自负。

Not affiliated with Apple Inc. Apple Music is a trademark of Apple Inc. Use at your own risk.

## 许可证 / License

MIT

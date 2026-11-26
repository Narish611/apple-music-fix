
# AMFix

一个 LSPosed 模块，修复部分安卓设备在 Apple Music 上使用有线耳机时使用耳机键三击回到上一首功能失效的问题；同时支持把歌词页字体替换为指定字体。

## 功能

- 有线耳机：按一次播放/暂停，按两次下一首，按三次上一首
- 只修复有线耳机；蓝牙耳机不受影响
- 状态检测：Apple Music 正在运行 + 有线耳机已连接 + LSPosed 中模块已启用，三者同时满足才显示「模块已生效」
- 播放页自定义字体：可指定任意 ttf ，只替换歌词页面

## 要求

- Root（KernelSU / Magisk 等）
- LSPosed（Zygisk 版）
- Apple Music 7.0.0
- Android 8.0+

## 隐私

- 不收集、不上传任何数据
- root 只用于：读取 LSPosed 的模块启用状态

## 构建

macOS + Android Studio 自带 JBR + SDK build-tools 36：

```bash
export AMFIX_KS_PASS=你的签名密码
bash build.sh
```

首次构建会自动下载 `xposed-api.jar` 与 `libs/` 下的 AndroidX 依赖（ViewPager2 / RecyclerView / core，固定版本，见 `fetch-libs.sh`），产物在 `build/amfix.apk`。

Linux 下需要把 `sips` 换成 ImageMagick 之类的工具，并调整 JBR 路径。

## 反馈

遇到问题请附上：

- LSPosed 日志
- 设备型号、系统版本、Apple Music 版本

## AI 声明

本项目的代码和文档在 AI 编程助手的协助下完成：**DeepSeek**（运行在 **DeepSeek Harness** 上，模型为 **deepseek-flash**）。需求确认、功能设计、真机测试和发布由维护者完成。AI 生成的内容可能存在错误，请以实际测试为准。

## 免责声明

非官方项目，与 Apple Inc. 无关。Apple Music 是 Apple Inc. 的商标。模块仅支持个人使用，使用风险自负。

## 许可证

[MIT](LICENSE)

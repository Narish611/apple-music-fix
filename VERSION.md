# AMFix

## 版本规则

- 包名：`com.narish61.amefix`
- versionCode：`yyMMddHH`（北京时间，24 小时制），同一小时内连发多版时顺延（`26112700` → `26112701`）
- 只改应用内文字/图标：versionName 第三位 +1
- 改机制（检测逻辑、按键处理、状态同步）：versionName 第二位 +1
- 大版本更新：由用户指定

## 当前

- versionName：`3.0.0`
- versionCode：`26112700`
- 日期：2026-11-27 00 时
- 变更：
  - UI 重构：三页横向滑动（主页 / 更多选项 / 关于）+ 底部标签栏 + 启动遮罩
  - 主页重做：AMFix 标题、右上角重启、模块状态、耳机修复开关、两段说明、模块版本与 Apple Music 版本
  - 状态同步不再依赖模块 App 进程：开关状态写入宿主可读文件，广播与文件按时间戳取新
  - 字体加载失败自动退避重试；同路径替换字体文件可被识别
  - 应用名统一为 AMFix；「强行停止」统一为「重启」；「恢复到初始状态」移至关于页
  - 停止发布精简版（2.0.0 的 no-tools 包可继续使用，不再更新）

## 发布产物

- `AMFix-<versionName>.apk`：完整版（唯一发布包，不再发布精简版）

## 构建

```bash
export AMFIX_KS_PASS=你的密码
bash build.sh
```

依赖说明：
- `xposed-api.jar` 首次构建自动下载
- `libs/` 内的 AndroidX 依赖（ViewPager2 / RecyclerView / core 等固定版本）由 `fetch-libs.sh` 首次构建自动下载，不进入仓库
- 产物在 `build/amfix.apk`

## 更新检查

`update.json` 放在仓库根目录，App 读取：
`https://raw.githubusercontent.com/Narish611/apple-music-fix/main/update.json`
比较 `versionCode`，有新版本时提示并跳转 `url`。

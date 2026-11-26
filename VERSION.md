# AM 有线耳机修复 / Apple Music Fix

## 版本规则

- 完整版包名：`com.narish61.amefix`
- 精简版包名：`com.narish61.amefixm`（仅耳机修复，与完整版可同时安装）
- versionCode：`yyMMddHH`（北京时间，24 小时制），同一小时内连发多版时顺延（`26111102` → `26111103`）
- 只改应用内文字/图标：versionName 第三位 +1
- 改机制（例如检测逻辑、按键处理）：versionName 第二位 +1
- 大版本更新：由用户指定

## 当前

- versionName：`2.0.0`
- versionCode：`26112609`
- 日期：2026-11-26 09 时
- 变更：
  - UI 改版（手写仿 Material 3 Expressive，深浅色）
  - 播放页字体设置：仅保留「歌词页指定字体」，移除内置字体替换
  - 多语言：简体中文 / 繁体中文 / 英文，跟随系统或按应用单独设置
  - 新增「更多选项」页：指定字体、字体设置恢复默认、模块设置恢复初始状态
  - 关于页独立成页，首页增加模块总开关

## 发布产物

命名规则（GitHub Release 附件）：

- `AMFix-<versionName>.apk`：完整版
- `AMFix-<versionName>-no-tools.apk`：精简版（无「更多选项」页，仅耳机修复）

## 构建

完整版在 `~/dsh-amfix`，精简版在 `~/dsh-amfix-lite`，各自执行：

```bash
AMFIX_KS_PASS=你的密码 bash build.sh
```

产物在 `build/amfix.apk`。首次构建会生成 `dsh.keystore`。

## 更新检查

`update.json` 放在仓库根目录，App 读取：
`https://raw.githubusercontent.com/Narish611/apple-music-fix/main/update.json`
比较 `versionCode`，有新版本时提示并跳转 `url`。

# AM 有线耳机修复 / Apple Music Fix

## 版本规则

- 包名：`com.narish61.amefix`
- versionName：`1.1.0`
- versionCode：`yyMMddHH`（北京时间，24 小时制），例如 `26111102`
- 只改应用内文字：versionName 第三位 +1（`1.0.0` → `1.0.1`）
- 改机制（例如检测逻辑、按键处理）：versionName 第二位 +1（`1.0.0` → `1.1.0`）
- 大版本更新：由用户指定

## 当前

- versionName：1.1.0
- versionCode：26111102
- 日期：2026-11-11 02 时
- 变更：新增右上角齿轮（作者信息、项目地址、检查更新），增加 `INTERNET` 权限

## 构建

Mac 上 `~/dsh-amfix`，执行 `AMFIX_KS_PASS=你的密码 bash build.sh`，产物 `build/amfix.apk`。

## 更新检查

`update.json` 放在仓库根目录，App 读取：
`https://raw.githubusercontent.com/Narish611/apple-music-fix/main/update.json`
比较 `versionCode`，有新版本时提示并跳转 `url`。

# AMFix

## 版本规则

- 包名：`com.narish61.amefix`
- versionCode：`yyMMddHH`（北京时间，24 小时制），同一小时内连发多版时顺延（`26112700` → `26112701`）
- 只改应用内文字/图标：versionName 第三位 +1
- 改机制（检测逻辑、按键处理、状态同步）：versionName 第二位 +1
- 大版本更新：由用户指定

## 当前

- versionName：`3.0.0`
- versionCode：`26112702`
- 日期：2026-11-27 01 时
- 变更（在 3.0.0 首版基础上重发，版本名不变）：
  - 启动时静默检查更新：有新版本时主页「模块版本」右侧显示绿色「有更新」，版本板块下方多一行「有更新，点击去更新」；无新版本时两者都不显示
  - 「更多选项」页说明去掉「主页右上角可重启 Apple Music」
  - 「字体设置恢复默认设置」副标题「不正常」改为「异常」

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

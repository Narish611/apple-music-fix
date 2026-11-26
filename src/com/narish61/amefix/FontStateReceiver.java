package com.narish61.amefix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * 宿主 Apple Music 进程启动时通过 QUERY_FONT_STATE 广播来这里取当前状态，
 * 避免依赖 LSPosed 上不可靠的 XSharedPreferences 跨进程读取。
 * 模块 App 侧任何开关/字体变化后也调用 {@link #pushState(Context)} 主动同步。
 */
public class FontStateReceiver extends BroadcastReceiver {

    public static final String ACTION_QUERY = "com.narish61.amefix.QUERY_FONT_STATE";
    public static final String ACTION_STATE = "com.narish61.amefix.FONT_STATE";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_MODULE_PATH = "module_path";
    public static final String EXTRA_MASTER = "master_enabled";
    public static final String EXTRA_CUSTOM_PATH = "custom_font_path";
    private static final String HOST = "com.apple.android.music";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_QUERY.equals(intent.getAction())) {
            return;
        }
        pushState(context);
    }

    /**
     * 读取模块 App 里的全部状态并广播给宿主进程。字体开关、模块总开关、
     * 自定义字体路径都通过这一条广播同步，宿主端据此决定是否替换字体/是否拦截耳机键。
     */
    public static void pushState(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(
                    MoreToolsActivity.PREFS_PUBLIC, Context.MODE_PRIVATE);
            boolean master = sp.getBoolean(MoreToolsActivity.KEY_MASTER_ENABLED, true);
            String customPath = sp.getString(MoreToolsActivity.KEY_CUSTOM_FONT_PATH, "");
            String modulePath = sp.getString(MoreToolsActivity.KEY_MODULE_PATH,
                    context.getApplicationInfo().sourceDir);
            // 字体只有「歌词页指定字体」一个来源：有路径才启用替换，没有就保持原生。
            boolean replaceEnabled = customPath != null && customPath.length() > 0;

            Intent out = new Intent(ACTION_STATE);
            out.setPackage(HOST);
            out.putExtra(EXTRA_ENABLED, replaceEnabled);
            out.putExtra(EXTRA_MASTER, master);
            out.putExtra(EXTRA_CUSTOM_PATH, customPath == null ? "" : customPath);
            out.putExtra(EXTRA_MODULE_PATH, modulePath);
            context.sendBroadcast(out);
        } catch (Throwable ignored) {
        }
    }
}

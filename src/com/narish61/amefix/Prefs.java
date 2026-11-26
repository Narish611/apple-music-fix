package com.narish61.amefix;

/** 模块内部的偏好键集中定义，避免页面与 Hook 各写一份字符串。 */
public final class Prefs {

    public static final String PREFS_PUBLIC = "amfix";

    public static final String KEY_FONT_ENABLED = "lyrics_font_enabled";
    public static final String KEY_FONT_CHANGED = "lyrics_font_changed_at";
    public static final String KEY_FONT_STOPPED = "lyrics_font_stopped_at";
    public static final String KEY_MODULE_PATH = "lyrics_font_module_path";
    public static final String KEY_MASTER_ENABLED = "module_master_enabled";
    public static final String KEY_CUSTOM_FONT_NAME = "custom_font_name";
    public static final String KEY_CUSTOM_FONT_PATH = "custom_font_path";
    public static final String KEY_CUSTOM_FONT_CHANGED = "custom_font_changed_at";
    public static final String KEY_FORCE_NOTICE = "force_notice_once";

    private Prefs() {
    }
}

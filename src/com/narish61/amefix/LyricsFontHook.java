package com.narish61.amefix;

import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class LyricsFontHook {

    private static final String TAG = "AMFix lyrics font";
    private static final String SELF = "com.narish61.amefix";
    private static final String PREFS = "amfix";
    private static final String LYRICS_FRAGMENT =
            "com.apple.android.music.player.fragment.PlayerLyricsViewFragment";
    private static final long REAPPLY_DELAY_MS = 80L;

    private static final List<String> LAYOUTS = Arrays.asList(
            "lyrics_line",
            "lyrics_line_karaoke",
            "lyrics_line_static",
            "lyrics_translation_line_karaoke",
            "lyrics_word_karaoke",
            "lyrics_word_pronunciation",
            "lyrics_pronunciation_mixed_rtl_ltr",
            "lyrics_pronunciation_vertical_group",
            "lyrics_bg_translation_line_karaoke",
            "lyrics_word_karaoke_bg",
            "lyrics_word_pronunciation_bg",
            "lyrics_line_instrumental"
    );

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicReference<Typeface> FONT = new AtomicReference<Typeface>();
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
    private static final AtomicBoolean LOADING = new AtomicBoolean(false);
    private static final AtomicReference<String> MODULE_PATH = new AtomicReference<String>();
    private static final AtomicReference<String> CUSTOM_PATH = new AtomicReference<String>("");
    private static final AtomicBoolean ITALIC_READY = new AtomicBoolean(false);
    private static final AtomicReference<Long> LAST_STATE_TS = new AtomicReference<Long>(0L);
    private static final AtomicReference<String> APPLIED_STAMP = new AtomicReference<String>("");
    private static final java.util.concurrent.atomic.AtomicInteger LOAD_TRIES =
            new java.util.concurrent.atomic.AtomicInteger();
    private static Typeface italicFont;

    // ---- 状态文件直读 ----
    // 模块 App 会把「是否启用 / 总开关 / 模块 APK 路径 / 自定义字体路径」写进宿主
    // 自己 files 目录下的这个小文件。广播依赖模块 App 进程存活，文件不依赖。
    private static final String[] STATE_FILES = new String[]{
            "/data/data/com.apple.android.music/files/amfix_state.conf",
            "/data/user/0/com.apple.android.music/files/amfix_state.conf",
            "/sdcard/Android/data/com.apple.android.music/files/amfix_state.conf",
            "/storage/emulated/0/Android/data/com.apple.android.music/files/amfix_state.conf"
    };
    private static final long STATE_CHECK_MS = 700L;
    private static volatile long lastStateCheck;
    private static volatile String lastStateBody;

    private static final Set<View> PENDING = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));
    private static final WeakHashMap<View, Boolean> RECYCLERS = new WeakHashMap<View, Boolean>();
    private static final WeakHashMap<View, Boolean> ROWS = new WeakHashMap<View, Boolean>();
    private static final WeakHashMap<View, Boolean> DELAYED = new WeakHashMap<View, Boolean>();
    // 记录每个被替换过字体的 TextView 的原始 Typeface，关闭开关/加载失败时回滚。
    private static final WeakHashMap<TextView, Typeface> ORIGINAL_FACES =
            new WeakHashMap<TextView, Typeface>();
    // 同时记录原始 textStyle（BOLD/ITALIC 位），回滚时要连样式一起还原。
    private static final WeakHashMap<TextView, Integer> ORIGINAL_STYLES =
            new WeakHashMap<TextView, Integer>();
    // 记录当前已应用的字型（源 + 样式 + 实际生效的 Typeface 对象）。
    // 用对象身份判断可以避免在 Typeface.create 无缓存的系统版本上反复 setTypeface。
    private static final WeakHashMap<TextView, Applied> APPLIED =
            new WeakHashMap<TextView, Applied>();

    private static final class Applied {
        final Typeface source;
        final int style;
        final Typeface result;

        Applied(Typeface source, int style, Typeface result) {
            this.source = source;
            this.style = style;
            this.result = result;
        }
    }

    private LyricsFontHook() {
    }

    /**
     * 宿主进程启动时调用。注意：这里始终安装 Hook，不再因为读不到开关状态而放弃；
     * 开关状态稍后由 {@link #applyState(boolean, String)} 通过广播同步过来。
     */
    public static void install(XC_LoadPackage.LoadPackageParam lp) {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        try {
            installLayoutHook();
            installFragmentHook(lp.classLoader);
            XposedBridge.log(TAG + " base hooks installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " install error: " + t);
        }
        // 兜底：某些 LSPosed 版本能直接读到 prefs，先读一次。
        try {
            XSharedPreferences prefs = new XSharedPreferences(SELF, Prefs.PREFS_PUBLIC);
            prefs.reload();
            boolean en = prefs.getBoolean(Prefs.KEY_FONT_ENABLED, false);
            String path = prefs.getString(Prefs.KEY_MODULE_PATH, "");
            String custom = prefs.getString(Prefs.KEY_CUSTOM_FONT_PATH, "");
            XposedBridge.log(TAG + " prefs fallback enabled=" + en
                    + " path=" + (path == null ? "null" : path));
            applyState(en, path, custom);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " prefs fallback failed: " + t);
        }
        // 最后用状态文件覆盖：文件是模块 App 主动落的，语义最准，且不依赖模块进程。
        refreshStateFromFile();
    }

    /**
     * 读取模块 App 落在宿主目录里的状态文件。文件不存在（未配置/无 root）时静默跳过，
     * 交回广播通道。内容变化时才重新 applyState，避免每次布局都触发重绘。
     */
    private static void refreshStateFromFile() {
        long now = System.currentTimeMillis();
        if (now - lastStateCheck < STATE_CHECK_MS) {
            return;
        }
        lastStateCheck = now;
        for (String path : STATE_FILES) {
            try {
                java.io.File f = new java.io.File(path);
                if (!f.isFile() || f.length() <= 0 || f.length() > 8192) {
                    continue;
                }
                String body = readText(f);
                if (body == null || body.length() == 0) {
                    continue;
                }
                if (body.equals(lastStateBody)) {
                    return;
                }
                lastStateBody = body;
                boolean enabled = false;
                boolean master = true;
                String module = null;
                String custom = "";
                long ts = 0L;
                for (String line : body.split("\n")) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    if ("enabled".equals(k)) {
                        enabled = "1".equals(v);
                    } else if ("master".equals(k)) {
                        master = "1".equals(v);
                    } else if ("module".equals(k)) {
                        module = v;
                    } else if ("custom".equals(k)) {
                        custom = v;
                    } else if ("ts".equals(k)) {
                        try {
                            ts = Long.parseLong(v);
                        } catch (Throwable ignored) {
                        }
                    }
                }
                if (ts > 0 && ts < LAST_STATE_TS.get()) {
                    XposedBridge.log(TAG + " state file skipped (stale ts=" + ts
                            + " < " + LAST_STATE_TS.get() + ")");
                    return;
                }
                XposedBridge.log(TAG + " state file applied enabled=" + enabled
                        + " master=" + master + " custom=" + custom + " ts=" + ts
                        + " from " + path);
                Module.setMasterEnabled(master);
                applyState(enabled, module, custom, ts);
                checkFontFileChanged();
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static String readText(java.io.File f) throws java.io.IOException {
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            try {
                r.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 由 Module 的广播接收器调用，同步模块 App 里的开关状态、模块 APK 路径与自定义字体路径。
     */
    public static void applyState(boolean enabled, String modulePath, String customPath) {
        applyState(enabled, modulePath, customPath, 0L);
    }

    /**
     * @param ts 该状态的时间戳（毫秒）。广播与状态文件是两条通道，按时间取新：
     *           小于已应用时间戳的旧状态直接丢弃，避免 su 慢一拍时旧文件把新状态覆盖回去。
     */
    public static void applyState(boolean enabled, String modulePath, String customPath, long ts) {
        if (ts > 0) {
            if (ts < LAST_STATE_TS.get()) {
                XposedBridge.log(TAG + " state skipped (stale ts=" + ts
                        + " < " + LAST_STATE_TS.get() + ")");
                return;
            }
            LAST_STATE_TS.set(ts);
        }
        ENABLED.set(enabled);
        if (modulePath != null && modulePath.length() > 0) {
            MODULE_PATH.set(modulePath);
        }
        String custom = (customPath == null || customPath.length() == 0) ? "" : customPath;
        boolean sourceChanged = !custom.equals(CUSTOM_PATH.get());
        CUSTOM_PATH.set(custom);
        XposedBridge.log(TAG + " state enabled=" + enabled + " custom=" + custom + " ts=" + ts);
        if (sourceChanged && FONT.get() != null) {
            // 字体源变了（换了自定义字体或恢复默认），清缓存强制重载。
            FONT.set(null);
            ITALIC_READY.set(false);
            italicFont = null;
        }
        if (enabled) {
            LOAD_TRIES.set(0);
            XposedBridge.log(TAG + " requesting font load");
            maybeLoadFont();
            // 无论字体是否已就绪都刷一次：未就绪时走 restore，避免残留上一轮替换的字体。
            reapplyAll();
        } else {
            // 关闭功能：清缓存并回滚已替换的字体，确保歌词真正回到原生。
            FONT.set(null);
            ITALIC_READY.set(false);
            italicFont = null;
            reapplyAll();
        }
    }

    /**
     * 同一个路径上换了字体文件（用户重新选了一个同名文件、或复制覆盖）时，
     * 状态字符串不会变，光靠 applyState 察觉不到。这里比对文件长度 + 修改时间。
     */
    private static void checkFontFileChanged() {
        String stamp = fontStamp(CUSTOM_PATH.get());
        if (stamp.length() == 0 || stamp.equals(APPLIED_STAMP.get())) {
            return;
        }
        if (FONT.get() == null) {
            // 还没加载成功，交给加载完成时的记录逻辑。
            return;
        }
        XposedBridge.log(TAG + " font file changed (" + APPLIED_STAMP.get() + " -> " + stamp
                + "), reloading");
        FONT.set(null);
        ITALIC_READY.set(false);
        italicFont = null;
        LOAD_TRIES.set(0);
        maybeLoadFont();
        reapplyAll();
    }

    /** 自定义字体文件的指纹；content:// 源无法直接 stat，返回空串表示不参与比对。 */
    private static String fontStamp(String customPath) {
        if (customPath == null || customPath.length() == 0
                || customPath.startsWith("content://")) {
            return "";
        }
        for (String candidate : customCandidates(customPath)) {
            try {
                java.io.File f = new java.io.File(candidate);
                if (f.isFile()) {
                    return f.length() + "@" + f.lastModified();
                }
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    private static android.content.Context hostContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof android.content.Context) {
                return (android.content.Context) app;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void maybeLoadFont() {
        if (FONT.get() != null || !LOADING.compareAndSet(false, true)) {
            return;
        }
        final String custom = CUSTOM_PATH.get();
        final String path = MODULE_PATH.get();
        if ((custom == null || custom.length() == 0) && (path == null || path.length() == 0)) {
            LOADING.set(false);
            XposedBridge.log(TAG + " load skipped: no font source yet");
            return;
        }
        loadFont(path, custom);
    }

    private static void loadFont(final String modulePath, final String customPath) {
        Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "amfix-lyrics-font");
                t.setDaemon(true);
                return t;
            }
        }).execute(new Runnable() {
            @Override
            public void run() {
                final List<String> attempts = new ArrayList<String>();
                try {
                    Typeface tf = null;

                    // 候选 1：自定义字体文件——依次尝试多个已知落点（主通道可能落在宿主
                    // 内部私有目录，也可能落在宿主外部私有目录，后者不受 app_data_file 限制）。
                    if (tf == null && customPath != null && customPath.length() > 0
                            && !customPath.startsWith("content://")) {
                        for (String candidate : customCandidates(customPath)) {
                            if (tf != null) {
                                break;
                            }
                            try {
                                java.io.File file = new java.io.File(candidate);
                                if (file.isFile()) {
                                    tf = new Typeface.Builder(file).build();
                                    XposedBridge.log(TAG + " loaded custom font file: " + candidate
                                            + " size=" + file.length());
                                } else {
                                    attempts.add("missing: " + candidate);
                                }
                            } catch (Throwable t) {
                                attempts.add(candidate + " error: " + t);
                            }
                        }
                    }

                    // 候选 2：自定义字体 content:// Provider（宿主可见时可用）。
                    // 即使主路径是普通文件路径，也把默认 provider URI 作为最后一路尝试。
                    if (tf == null && customPath != null && customPath.length() > 0) {
                        final String providerUri = customPath.startsWith("content://")
                                ? customPath
                                : "content://com.narish61.amefix.fontprovider/custom_font.ttf";
                        try {
                            android.content.Context ctx = hostContext();
                            if (ctx == null) {
                                throw new IllegalStateException("host context unavailable");
                            }
                            Uri uri = Uri.parse(providerUri);
                            ParcelFileDescriptor pfd = ctx.getContentResolver()
                                    .openFileDescriptor(uri, "r");
                            if (pfd == null) {
                                throw new IllegalStateException("provider returned null fd");
                            }
                            try {
                                tf = new Typeface.Builder(pfd.getFileDescriptor()).build();
                            } finally {
                                try {
                                    pfd.close();
                                } catch (Throwable ignored) {
                                }
                            }
                            XposedBridge.log(TAG + " loaded custom font via provider: " + providerUri);
                        } catch (Throwable t) {
                            attempts.add("provider error: " + t);
                        }
                    }

                    if (tf == null) {
                        XposedBridge.log(TAG + " all font sources failed, original font kept: "
                                + attempts);
                        scheduleRetry();
                        return;
                    }
                    if (attempts.size() > 0) {
                        XposedBridge.log(TAG + " custom font unavailable, fell back; reasons: "
                                + attempts);
                    }
                    FONT.set(tf);
                    LOAD_TRIES.set(0);
                    APPLIED_STAMP.set(fontStamp(CUSTOM_PATH.get()));
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            XposedBridge.log(TAG + " font loaded, reapplying observed rows");
                            reapplyAll();
                        }
                    });
                    XposedBridge.log(TAG + " font loaded OK");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + " font load failed, original font kept: " + t);
                    scheduleRetry();
                } finally {
                    LOADING.set(false);
                }
            }
        });
    }

    /** 加载失败退避重试：字体文件可能还没复制完、或权限尚未落地。 */
    private static void scheduleRetry() {
        final int n = LOAD_TRIES.incrementAndGet();
        if (n > 3) {
            XposedBridge.log(TAG + " font load gave up after 3 retries");
            return;
        }
        long delay = n == 1 ? 1000L : (n == 2 ? 3000L : 8000L);
        XposedBridge.log(TAG + " font load retry " + n + " in " + delay + "ms");
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!ENABLED.get() || FONT.get() != null) {
                    return;
                }
                maybeLoadFont();
            }
        }, delay);
    }

    /** 宿主可能读取自定义字体的全部落点，按优先级排列。 */
    private static List<String> customCandidates(String primary) {
        List<String> out = new ArrayList<String>();
        if (primary != null && primary.length() > 0) {
            out.add(primary);
        }
        String[] extra = new String[]{
                "/data/data/com.apple.android.music/files/amfix_custom_font.ttf",
                "/data/user/0/com.apple.android.music/files/amfix_custom_font.ttf",
                "/sdcard/Android/data/com.apple.android.music/files/amfix_custom_font.ttf",
                "/data/media/0/Android/data/com.apple.android.music/files/amfix_custom_font.ttf"
        };
        for (String p : extra) {
            if (!out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private static void installLayoutHook() {
        try {
            XposedHelpers.findAndHookMethod(LayoutInflater.class, "inflate",
                    int.class, ViewGroup.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Integer id = (Integer) param.args[0];
                                LayoutInflater inflater = (LayoutInflater) param.thisObject;
                                String name = inflater.getContext().getResources()
                                        .getResourceEntryName(id);
                                if (name == null || !LAYOUTS.contains(name)) {
                                    return;
                                }
                                Boolean attach = (Boolean) param.args[2];
                                View root = Boolean.TRUE.equals(attach)
                                        ? (ViewGroup) param.args[1]
                                        : (View) param.getResult();
                                if (root == null) {
                                    return;
                                }
                                applyTo(root);
                                installRowObserver(root);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " layout hook error: " + t);
        }
    }

    private static void installFragmentHook(ClassLoader cl) {
        try {
            Class<?> fragment = XposedHelpers.findClass(LYRICS_FRAGMENT, cl);
            XposedHelpers.findAndHookMethod(fragment, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Object recycler = XposedHelpers.callMethod(param.thisObject,
                                "getRecyclerView");
                        if (!(recycler instanceof ViewGroup)) {
                            XposedBridge.log(TAG + " onResume: no RecyclerView");
                            return;
                        }
                        ViewGroup view = (ViewGroup) recycler;
                        XposedBridge.log(TAG + " onResume: RecyclerView found");
                        applyTo(view);
                        installChildObserver(view, cl);
                        scheduleReapply(view);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + " onResume hook: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " fragment hook error: " + t);
        }
    }

    private static void installChildObserver(final ViewGroup recycler, ClassLoader cl) {
        synchronized (RECYCLERS) {
            if (RECYCLERS.containsKey(recycler)) {
                return;
            }
            RECYCLERS.put(recycler, Boolean.TRUE);
        }
        try {
            final Class<?> listenerClass = Class.forName(
                    "androidx.recyclerview.widget.RecyclerView$OnChildAttachStateChangeListener",
                    false, cl);
            InvocationHandler handler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("onChildViewAttachedToWindow".equals(name)) {
                        try {
                            if (args != null && args.length > 0 && args[0] instanceof View) {
                                applyTo((View) args[0]);
                            }
                            scheduleReapply(recycler);
                        } catch (Throwable ignored) {
                        }
                        return null;
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(name)) {
                        return "AMFixLyricsFontChildListener";
                    }
                    return null;
                }
            };
            Object listener = Proxy.newProxyInstance(listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass}, handler);
            recycler.getClass()
                    .getMethod("addOnChildAttachStateChangeListener", listenerClass)
                    .invoke(recycler, listener);
        } catch (Throwable ignored) {
        }
    }

    private static void installRowObserver(View row) {
        synchronized (ROWS) {
            if (ROWS.containsKey(row)) {
                return;
            }
            ROWS.put(row, Boolean.TRUE);
        }
        row.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                scheduleReapply(v);
            }
        });
    }

    private static void scheduleReapply(final View root) {
        synchronized (DELAYED) {
            if (DELAYED.containsKey(root)) {
                return;
            }
            DELAYED.put(root, Boolean.TRUE);
        }
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (DELAYED) {
                    DELAYED.remove(root);
                }
                applyTo(root);
            }
        }, REAPPLY_DELAY_MS);
    }

    private static void reapplyAll() {
        final List<View> roots = new ArrayList<View>();
        synchronized (PENDING) {
            roots.addAll(PENDING);
            PENDING.clear();
        }
        synchronized (ROWS) {
            roots.addAll(ROWS.keySet());
        }
        for (View root : roots) {
            applyTo(root);
        }
    }

    private static void applyTo(View root) {
        if (!(root instanceof ViewGroup) && !(root instanceof TextView)) {
            return;
        }
        // 每次应用前顺手确认一次状态：覆盖「模块 App 没开、宿主自己冷启动/回前台」的情况。
        refreshStateFromFile();
        List<TextView> views = new ArrayList<TextView>();
        collect(root, views);
        Typeface base = FONT.get();
        if (!ENABLED.get() || base == null) {
            // 功能关闭，或指定字体加载失败导致没有可用字体源：把已替换的字体回滚成原生，
            // 避免"上方开关已关闭、歌词却仍是替换字体"的残留。
            restore(views);
            if (ENABLED.get()) {
                synchronized (PENDING) {
                    PENDING.add(root);
                }
            }
            return;
        }
        Typeface target = base;
        Typeface italic = italicFont();
        int changed = 0;
        synchronized (ORIGINAL_FACES) {
            for (TextView view : views) {
                Integer origStyle;
                if (!ORIGINAL_FACES.containsKey(view)) {
                    Typeface old = view.getTypeface();
                    ORIGINAL_FACES.put(view, old);
                    origStyle = Integer.valueOf(old != null ? old.getStyle() : Typeface.NORMAL);
                    ORIGINAL_STYLES.put(view, origStyle);
                } else {
                    origStyle = ORIGINAL_STYLES.get(view);
                }
                int style = origStyle != null ? origStyle.intValue() : Typeface.NORMAL;
                boolean wantItalic = italic != null && (style & Typeface.ITALIC) != 0;
                Typeface use = wantItalic ? italic : target;
                int applyStyle = wantItalic ? Typeface.ITALIC : Typeface.NORMAL;
                // 关键：以 NORMAL（斜体行用 ITALIC）样式应用。Typeface.create 的 weight 由 style
                // 决定（NORMAL=400），因此能覆盖自定义字体自带的 weight——否则 Android 的 fallback
                // 会按同 weight 去系统字体里挑字，缺字语言（拉丁/假名/谚文）就被一起带粗。
                // 字形本身仍取自字体文件，视觉粗细不变。
                Applied rec = APPLIED.get(view);
                if (rec != null && rec.source == use && rec.style == applyStyle
                        && view.getTypeface() == rec.result) {
                    continue;
                }
                try {
                    view.setTypeface(use, applyStyle);
                    APPLIED.put(view, new Applied(use, applyStyle, view.getTypeface()));
                    changed++;
                } catch (Throwable ignored) {
                }
            }
        }
        if (changed > 0) {
            XposedBridge.log(TAG + " applied to " + changed + " TextViews");
        }
    }

    private static void restore(List<TextView> views) {
        int restored = 0;
        synchronized (ORIGINAL_FACES) {
            for (TextView view : views) {
                boolean tracked = ORIGINAL_FACES.containsKey(view);
                Typeface orig = ORIGINAL_FACES.remove(view);
                Integer style = ORIGINAL_STYLES.remove(view);
                APPLIED.remove(view);
                if (!tracked) {
                    continue;
                }
                int restoreStyle = style != null ? style.intValue() : Typeface.NORMAL;
                if (view.getTypeface() == orig) {
                    continue;
                }
                try {
                    view.setTypeface(orig, restoreStyle);
                    restored++;
                } catch (Throwable ignored) {
                }
            }
        }
        if (restored > 0) {
            XposedBridge.log(TAG + " restored original font on " + restored + " TextViews");
        }
    }

    private static Typeface italicFont() {
        if (ITALIC_READY.get()) {
            return italicFont;
        }
        synchronized (LyricsFontHook.class) {
            if (ITALIC_READY.get()) {
                return italicFont;
            }
            try {
                Typeface base = FONT.get();
                italicFont = base == null ? null : Typeface.create(base, Typeface.ITALIC);
            } catch (Throwable t) {
                italicFont = null;
            }
            ITALIC_READY.set(true);
            return italicFont;
        }
    }

    private static void collect(View view, List<TextView> out) {
        if (view instanceof TextView) {
            out.add((TextView) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), out);
            }
        }
    }
}

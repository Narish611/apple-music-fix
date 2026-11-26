package com.narish61.amefix;

import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Module implements IXposedHookLoadPackage {

    private static final String PKG = "com.apple.android.music";
    private static final String SELF = "com.narish61.amefix";
    private static final String SERVICE = "com.apple.android.music.player.MediaPlaybackService";
    private static final long WINDOW_MS = 400L;
    private static final long LONG_PRESS_MS = 600L;

    private static Handler handler;
    private static BroadcastReceiver fontStateReceiver;
    private static MediaSession session;
    private static int count;
    private static long lastEventTime = -1L;
    private static String hookedDesc = "";
    private static boolean receiverReady;
    // 模块总开关（耳机修复功能）。默认开启以兼容升级安装；关闭后 shouldHandle 直接放行按键。
    private static volatile boolean masterEnabled = true;
    private static volatile long lastQueryAt;
    private static final Set<Method> HOOKED = new HashSet<Method>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!PKG.equals(lp.packageName)) {
            return;
        }
        handler = new Handler(Looper.getMainLooper());

        try {
            LyricsFontHook.install(lp);
        } catch (Throwable t) {
            XposedBridge.log("AMFix lyrics font init error: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ensureReceiver((Context) param.thisObject);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("AMFix app hook error: " + t);
        }

        // 每次界面恢复都重新问一次模块 App 的状态（带节流）。
        // 只在进程启动时问一次是不够的：模块 App 被划掉后第一次询问无人应答，
        // 用户重新回到 Apple Music 时不会再有第二次机会——这正是
        // 「不打开模块 App 就不生效」的直接原因之一。
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", lp.classLoader,
                    "onResume", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!(param.thisObject instanceof Context)) {
                                return;
                            }
                            Context ctx = (Context) param.thisObject;
                            ensureReceiver(ctx);
                            queryState(ctx);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("AMFix activity hook error: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    MediaSession.class,
                    "setCallback",
                    MediaSession.Callback.class,
                    Handler.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object cb = param.args[0];
                            if (cb == null) {
                                return;
                            }
                            session = (MediaSession) param.thisObject;
                            hookCallback(cb);
                        }
                    });
            XposedBridge.log("AMFix loaded");
        } catch (Throwable t) {
            XposedBridge.log("AMFix init error: " + t);
        }

        hookService(lp);
    }

    private static void hookService(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod(
                    SERVICE, lp.classLoader, "onStartCommand",
                    Intent.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Intent it = (Intent) param.args[0];
                            if (it == null) {
                                return;
                            }
                            if (!Intent.ACTION_MEDIA_BUTTON.equals(it.getAction())) {
                                return;
                            }
                            KeyEvent ke = null;
                            try {
                                ke = it.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                            } catch (Throwable ignored) {
                            }
                            if (ke == null) {
                                return;
                            }
                            if (!shouldHandle(ke)) {
                                return;
                            }
                            handleKey(ke);
                            param.setResult(Service.START_NOT_STICKY);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("AMFix svc hook error: " + t);
        }
    }

    private static void hookCallback(Object cb) {
        Class<?> c = cb.getClass();
        while (c != null && c != Object.class) {
            Method m = null;
            try {
                m = c.getDeclaredMethod("onMediaButtonEvent", Intent.class);
            } catch (NoSuchMethodException ignored) {
            }
            if (m != null) {
                synchronized (HOOKED) {
                    if (!HOOKED.add(m)) {
                        return;
                    }
                }
                try {
                    m.setAccessible(true);
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            Intent it = (Intent) p.args[0];
                            KeyEvent ke = null;
                            if (it != null) {
                                try {
                                    ke = it.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                                } catch (Throwable ignored) {
                                }
                            }
                            if (ke == null) {
                                return;
                            }
                            if (!shouldHandle(ke)) {
                                return;
                            }
                            p.setResult(Boolean.TRUE);
                            handleKey(ke);
                        }
                    });
                    hookedDesc = m.getDeclaringClass().getName() + "#onMediaButtonEvent";
                    XposedBridge.log("AMFix hooked " + hookedDesc);
                    ensureReceiver(appContext());
                } catch (Throwable t) {
                    XposedBridge.log("AMFix hook error: " + t);
                }
                return;
            }
            c = c.getSuperclass();
        }
        XposedBridge.log("AMFix no onMediaButtonEvent in " + cb.getClass().getName());
    }

    private static boolean shouldHandle(KeyEvent ke) {
        if (!masterEnabled) {
            return false;
        }
        int code = ke.getKeyCode();
        if (code == KeyEvent.KEYCODE_HEADSETHOOK) {
            return true;
        }
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            return wiredConnected();
        }
        return false;
    }

    private static boolean wiredConnected() {
        try {
            Context ctx = appContext();
            if (ctx == null) {
                return false;
            }
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return false;
            }
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || t == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void handleKey(KeyEvent ke) {
        if (ke.getAction() != KeyEvent.ACTION_UP) {
            return;
        }
        if (ke.getRepeatCount() > 0) {
            return;
        }
        if (ke.getEventTime() - ke.getDownTime() > LONG_PRESS_MS) {
            return;
        }
        synchronized (Module.class) {
            if (ke.getEventTime() == lastEventTime) {
                return;
            }
            lastEventTime = ke.getEventTime();
        }
        count++;
        handler.removeCallbacks(ACTION);
        handler.postDelayed(ACTION, WINDOW_MS);
    }

    private static Context appContext() {
        try {
            Object c = XposedHelpers.getObjectField(session, "mContext");
            if (c instanceof Context) {
                return (Context) c;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof Context) {
                return (Context) app;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void ensureReceiver(Context ctx) {
        if (receiverReady || ctx == null) {
            return;
        }
        try {
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    Intent out = new Intent(SELF + ".PONG");
                    out.setPackage(SELF);
                    out.putExtra("hooked", hookedDesc);
                    c.sendBroadcast(out);
                }
            };
            IntentFilter f = new IntentFilter(SELF + ".PING");
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(r, f);
            }

            // 字体开关状态同步：本广播由模块 App 的 FontStateReceiver 应答。
            BroadcastReceiver fs = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    boolean enabled = i.getBooleanExtra("enabled", false);
                    boolean master = i.getBooleanExtra("master_enabled", masterEnabled);
                    String path = i.getStringExtra("module_path");
                    String custom = i.getStringExtra("custom_font_path");
                    long ts = i.getLongExtra("state_ts", 0L);
                    masterEnabled = master;
                    XposedBridge.log("AMFix state master=" + master + " font=" + enabled
                            + " ts=" + ts);
                    LyricsFontHook.applyState(enabled, path, custom, ts);
                }
            };
            fontStateReceiver = fs;
            IntentFilter ff = new IntentFilter("com.narish61.amefix.FONT_STATE");
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(fs, ff, Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(fs, ff);
            }

            // 主动询问模块 App 当前开关状态（此时 receiver 已就绪，能收到应答）。
            receiverReady = true;
            queryState(ctx);
        } catch (Throwable t) {
            // 注册失败时保持 false，让下次 onResume 再试一次。
            receiverReady = false;
            XposedBridge.log("AMFix receiver error: " + t);
        }
    }

    /** 询问模块 App 当前状态，1.2 秒节流，避免频繁广播。 */
    private static void queryState(Context ctx) {
        if (ctx == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastQueryAt < 1200L) {
            return;
        }
        lastQueryAt = now;
        try {
            Intent query = new Intent("com.narish61.amefix.QUERY_FONT_STATE");
            query.setPackage(SELF);
            ctx.sendBroadcast(query);
        } catch (Throwable ignored) {
        }
    }

    /** 供字体 Hook 读取状态文件后同步总开关，两条通道共用同一个标志。 */
    static void setMasterEnabled(boolean enabled) {
        masterEnabled = enabled;
    }

    private static final Runnable ACTION = new Runnable() {
        @Override
        public void run() {
            int n = count;
            count = 0;
            if (session == null) {
                return;
            }
            try {
                MediaController c = session.getController();
                if (c == null) {
                    return;
                }
                MediaController.TransportControls t = c.getTransportControls();
                PlaybackState st = c.getPlaybackState();
                if (n <= 1) {
                    boolean playing = st != null
                            && (st.getState() == PlaybackState.STATE_PLAYING
                            || st.getState() == PlaybackState.STATE_BUFFERING);
                    if (playing) {
                        t.pause();
                    } else {
                        t.play();
                    }
                } else if (n == 2) {
                    t.skipToNext();
                } else {
                    long pos = st == null ? 0L : st.getPosition();
                    if (pos > 3000L) {
                        t.seekTo(0L);
                    } else {
                        t.skipToPrevious();
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log("AMFix action error: " + t);
            }
        }
    };
}

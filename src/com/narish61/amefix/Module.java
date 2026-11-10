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
    private static MediaSession session;
    private static int count;
    private static long lastEventTime = -1L;
    private static String hookedDesc = "";
    private static boolean receiverReady;
    private static final Set<Method> HOOKED = new HashSet<Method>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!PKG.equals(lp.packageName)) {
            return;
        }
        handler = new Handler(Looper.getMainLooper());

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
        receiverReady = true;
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
        } catch (Throwable t) {
            XposedBridge.log("AMFix receiver error: " + t);
        }
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

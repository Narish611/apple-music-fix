package com.narish61.amefix;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 把字体/总开关状态镜像成宿主 Apple Music 自己能读的一个小文本文件。
 *
 * 背景：宿主侧的状态原本只靠一条广播由模块 App 应答。模块 App 被划掉、
 * 或被系统限制后台启动时，宿主冷启动的广播无人应答，状态就停在默认值——
 * 现象就是「不打开模块 App，字体设置不生效，必须强制停止一次 Apple Music」。
 *
 * 这里借助 root 把状态写到宿主自己的 files 目录（与字体复制同一套 su 通道），
 * 宿主进程启动或页面恢复时直接读文件，完全不依赖模块 App 进程是否存活。
 *
 * 文件里带 ts（毫秒时间戳）：广播和文件是两条通道，宿主必须按时间取新，
 * 否则 su 慢一拍时旧文件会把刚广播过去的新状态覆盖回去。
 */
final class StateStore {

    static final String FILE_NAME = "amfix_state.conf";
    private static final String HOST_DATA = "/data/data/com.apple.android.music";
    private static final String HOST_DIR = HOST_DATA + "/files";
    private static final String EXT_DIR = "/data/media/0/Android/data/com.apple.android.music/files";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "amfix-state");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean QUEUED = new AtomicBoolean(false);
    // 写盘期间又有新状态时置位，写完补跑一次，避免吞掉最后一次修改。
    private static final AtomicBoolean AGAIN = new AtomicBoolean(false);
    private static volatile long pendingTs;
    // 内容没变就不必再跑一次 su：onResume 等路径会频繁触发 pushState。
    private static volatile String lastWritten;

    private StateStore() {
    }

    /** 主线程调用安全：实际写盘在后台线程完成，且有合并去重。 */
    static void publish(Context ctx, long ts) {
        if (ctx == null) {
            return;
        }
        final Context app = ctx.getApplicationContext();
        if (app == null) {
            return;
        }
        if (ts > pendingTs) {
            pendingTs = ts;
        }
        if (!QUEUED.compareAndSet(false, true)) {
            AGAIN.set(true);
            return;
        }
        IO.execute(() -> {
            try {
                do {
                    AGAIN.set(false);
                    write(app, pendingTs);
                } while (AGAIN.get());
            } catch (Throwable ignored) {
            } finally {
                QUEUED.set(false);
            }
        });
    }

    private static void write(Context ctx, long ts) {
        SharedPreferences sp = ctx.getSharedPreferences(
                Prefs.PREFS_PUBLIC, Context.MODE_PRIVATE);
        boolean fontEnabled = sp.getBoolean(Prefs.KEY_FONT_ENABLED, false);
        boolean master = sp.getBoolean(Prefs.KEY_MASTER_ENABLED, true);
        String custom = sp.getString(Prefs.KEY_CUSTOM_FONT_PATH, "");
        String module = sp.getString(Prefs.KEY_MODULE_PATH,
                ctx.getApplicationInfo().sourceDir);

        boolean replaceEnabled = fontEnabled || (custom != null && custom.length() > 0);
        String sig = replaceEnabled + "/" + master + "/" + clean(module) + "/" + clean(custom);
        if (sig.equals(lastWritten)) {
            return;
        }
        String body = "v=2\n"
                + "ts=" + ts + "\n"
                + "enabled=" + (replaceEnabled ? 1 : 0) + "\n"
                + "master=" + (master ? 1 : 0) + "\n"
                + "module=" + clean(module) + "\n"
                + "custom=" + clean(custom) + "\n";

        File local = new File(ctx.getCacheDir(), FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(local)) {
            fos.write(body.getBytes("UTF-8"));
            fos.flush();
        } catch (Throwable t) {
            android.util.Log.w("AMFix", "state local write failed: " + t);
            return;
        }

        String cmd = "set +e\n"
                + "src='" + local.getAbsolutePath() + "'\n"
                + "dir='" + HOST_DIR + "'\n"
                + "ext='" + EXT_DIR + "'\n"
                + "test -s \"$src\" || exit 11\n"
                + "mkdir -p \"$dir\" 2>/dev/null\n"
                + "uid=$(stat -c '%u' '" + HOST_DATA + "' 2>/dev/null)\n"
                + "gid=$(stat -c '%g' '" + HOST_DATA + "' 2>/dev/null)\n"
                + "cat \"$src\" > \"$dir/" + FILE_NAME + "\" || exit 12\n"
                + "chown ${uid:-0}:${gid:-0} \"$dir/" + FILE_NAME + "\" 2>/dev/null\n"
                + "chmod 644 \"$dir/" + FILE_NAME + "\" 2>/dev/null\n"
                + "chcon --reference=\"$dir\" \"$dir/" + FILE_NAME + "\" 2>/dev/null"
                + " || restorecon -F \"$dir/" + FILE_NAME + "\" 2>/dev/null\n"
                // 备用落点：外部私有目录，宿主经 FUSE 也能读。
                + "mkdir -p \"$ext\" 2>/dev/null\n"
                + "cat \"$src\" > \"$ext/" + FILE_NAME + "\" 2>/dev/null\n"
                + "chmod 644 \"$ext/" + FILE_NAME + "\" 2>/dev/null\n"
                + "test -s \"$dir/" + FILE_NAME + "\"\n";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            String err = drain(p.getErrorStream());
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            if (!done) {
                p.destroy();
            }
            if (done && p.exitValue() == 0) {
                lastWritten = sig;
            }
            android.util.Log.i("AMFix", "publishState ts=" + ts
                    + " exit=" + (done ? p.exitValue() : -1) + " err=" + err.trim());
        } catch (Throwable t) {
            android.util.Log.w("AMFix", "publishState error: " + t);
        }
    }

    private static String clean(String v) {
        if (v == null) {
            return "";
        }
        return v.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String drain(java.io.InputStream in) {
        StringBuilder sb = new StringBuilder();
        if (in == null) {
            return "";
        }
        try {
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            in.close();
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }
}

package com.narish61.amefix;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static final String PKG = "com.apple.android.music";
    private static final String SELF = "com.narish61.amefix";
    private static final long POLL_MS = 1000L;
    private static final long PONG_VALID_MS = 3000L;
    private static final long LSP_CHECK_MS = 5000L;
    private static final String PREFS = "amfix";
    private static final String KEY_VER = "last_version";
    private static final String KEY_FIRST = "first_run_done";

    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView status;
    private volatile long lastPongAt;
    private volatile int lspEnabled = -1; // -1 unknown, 0 disabled, 1 enabled
    private boolean lspChecking;
    private long lspCheckedAt;
    private int lspFailCount;
    private boolean lspUnavailable;
    private boolean dark;
    private int colorOk;
    private int colorBad;
    private int colorWarn;
    private int colorPlain;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            ping();
            updateStatus();
            checkLsposedAsync();
            h.postDelayed(this, POLL_MS);
        }
    };

    private final BroadcastReceiver pongReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            lastPongAt = SystemClock.elapsedRealtime();
            updateStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        dark = night == Configuration.UI_MODE_NIGHT_YES;
        colorOk = dark ? 0xFF81C784 : 0xFF2E7D32;
        colorBad = dark ? 0xFFE57373 : 0xFFC62828;
        colorWarn = dark ? 0xFFFFB74D : 0xFFEF6C00;
        colorPlain = dark ? Color.WHITE : Color.BLACK;

        float d = getResources().getDisplayMetrics().density;
        final int p = (int) (20 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? Color.BLACK : Color.WHITE);
        root.setPadding(p, p, p, p);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(p, p + insets.getSystemWindowInsetTop(),
                    p, p + insets.getSystemWindowInsetBottom());
            return insets;
        });

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(22);
        title.setTextColor(colorPlain);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText(R.string.subtitle);
        sub.setTextSize(13);
        sub.setTextColor(colorPlain);
        sub.setPadding(0, (int) (6 * d), 0, (int) (16 * d));
        root.addView(sub);

        status = new TextView(this);
        status.setText(R.string.status_checking);
        status.setTextSize(16);
        status.setTextColor(colorPlain);
        status.setPadding(0, 0, 0, (int) (10 * d));
        root.addView(status);

        Button stop = new Button(this);
        stop.setText(R.string.btn_force_stop);
        stop.setOnClickListener(v -> forceStop());
        if (dark) {
            stop.setTextColor(Color.WHITE);
            stop.setBackgroundTintList(ColorStateList.valueOf(0xFF333333));
        }
        root.addView(stop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tip = new TextView(this);
        tip.setText(getString(R.string.tip_check) + "\n\n"
                + getString(R.string.tip_usage) + "\n\n"
                + getString(R.string.tip_version));
        tip.setTextSize(13);
        tip.setTextColor(colorPlain);
        tip.setPadding(0, p, 0, 0);
        root.addView(tip);

        setContentView(root);

        applySystemBars();

        checkDialogs();
    }

    private void applySystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController wic = getWindow().getInsetsController();
            if (wic != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                wic.setSystemBarsAppearance(dark ? 0 : mask, mask);
            }
        } else {
            int flags = dark ? 0
                    : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private long currentVersionCode() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return pi.getLongVersionCode();
            }
            return pi.versionCode;
        } catch (Throwable t) {
            return -1L;
        }
    }

    private void checkDialogs() {
        long code = currentVersionCode();
        if (code < 0) {
            return;
        }
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean first = !sp.getBoolean(KEY_FIRST, false);
        long last = sp.getLong(KEY_VER, 0L);
        sp.edit().putBoolean(KEY_FIRST, true).putLong(KEY_VER, code).apply();
        if (first) {
            showNoticeDialog();
        } else if (last != code) {
            showUpdatedDialog();
        }
    }

    private AlertDialog.Builder dialogBuilder() {
        return dark
                ? new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                : new AlertDialog.Builder(this);
    }

    private void showNoticeDialog() {
        dialogBuilder()
                .setTitle(R.string.notice_title)
                .setMessage(R.string.notice_message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (d, w) -> d.dismiss())
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void showUpdatedDialog() {
        dialogBuilder()
                .setTitle(R.string.update_title)
                .setMessage(R.string.update_message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (d, w) -> forceStop())
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    d.dismiss();
                    finish();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(SELF + ".PONG");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pongReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(pongReceiver, f);
        }
        h.post(poll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(poll);
        try {
            unregisterReceiver(pongReceiver);
        } catch (Throwable ignored) {
        }
    }

    private void ping() {
        Intent i = new Intent(SELF + ".PING");
        i.setPackage(PKG);
        i.addFlags(0x01000000);
        sendBroadcast(i);
    }

    private void updateStatus() {
        boolean hooked = SystemClock.elapsedRealtime() - lastPongAt < PONG_VALID_MS;
        boolean wired = wiredConnected();
        boolean enabled = lspEnabled != 0;
        boolean ok = hooked && wired && enabled;
        status.setText(ok ? R.string.status_active : R.string.status_inactive);
        status.setTextColor(ok ? colorOk : colorBad);
    }

    private boolean wiredConnected() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
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

    private void checkLsposedAsync() {
        if (lspUnavailable || lspChecking) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lspCheckedAt < LSP_CHECK_MS) {
            return;
        }
        lspCheckedAt = now;
        lspChecking = true;
        new Thread(() -> {
            int r = readLsposedEnabled();
            if (r < 0) {
                lspFailCount++;
                if (lspFailCount >= 3) {
                    lspUnavailable = true;
                }
            } else {
                lspEnabled = r;
                lspFailCount = 0;
            }
            lspChecking = false;
            runOnUiThread(this::updateStatus);
        }).start();
    }

    private int readLsposedEnabled() {
        String[] bases = {
                "/data/adb/lspd/config/modules_config.db",
                "/data/adb/lsposed/config/modules_config.db"
        };
        for (String base : bases) {
            try {
                byte[] db = suBase64(base);
                if (db == null || db.length < 32) {
                    continue;
                }
                File f = new File(getCacheDir(), "lspd.db");
                writeFile(f, db);
                byte[] wal = suBase64(base + "-wal");
                File fw = new File(getCacheDir(), "lspd.db-wal");
                if (wal != null && wal.length > 0) {
                    writeFile(fw, wal);
                } else if (fw.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    fw.delete();
                }
                byte[] shm = suBase64(base + "-shm");
                File fs = new File(getCacheDir(), "lspd.db-shm");
                if (shm != null && shm.length > 0) {
                    writeFile(fs, shm);
                } else if (fs.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    fs.delete();
                }
                int r = queryEnabled(f);
                if (r >= 0) {
                    return r;
                }
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private byte[] suBase64(String path) {
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", "base64 '" + path + "' 2>/dev/null"});
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            InputStream is = proc.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                proc.destroy();
                return null;
            }
            if (proc.exitValue() != 0) {
                return null;
            }
            String text = bos.toString("UTF-8").replaceAll("\\s", "");
            if (text.isEmpty()) {
                return null;
            }
            return Base64.decode(text, Base64.DEFAULT);
        } catch (Throwable t) {
            if (proc != null) {
                proc.destroy();
            }
            return null;
        }
    }

    private int queryEnabled(File dbFile) {
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("SELECT enabled FROM modules WHERE module_pkg_name=?",
                    new String[]{SELF});
            if (c.moveToFirst()) {
                return c.getInt(0) != 0 ? 1 : 0;
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return -1;
    }

    private void writeFile(File f, byte[] data) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(data);
        } finally {
            fos.close();
        }
    }

    private void forceStop() {
        status.setText(R.string.state_stopping);
        status.setTextColor(dark ? 0xFF9E9E9E : Color.GRAY);
        new Thread(() -> {
            boolean ok = false;
            try {
                Process proc = Runtime.getRuntime().exec(
                        new String[]{"su", "-c", "am force-stop " + PKG});
                if (proc.waitFor(8, TimeUnit.SECONDS)) {
                    ok = proc.exitValue() == 0;
                } else {
                    proc.destroy();
                }
            } catch (Throwable ignored) {
            }
            final boolean rooted = ok;
            runOnUiThread(() -> {
                if (rooted) {
                    status.setText(R.string.state_stopped);
                    status.setTextColor(colorWarn);
                    Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_LONG).show();
                } else {
                    status.setText(R.string.state_stop_failed);
                    status.setTextColor(colorBad);
                    Toast.makeText(this, R.string.toast_stop_failed, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}

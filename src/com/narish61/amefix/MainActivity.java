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
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static final String PKG = "com.apple.android.music";
    private static final String SELF = "com.narish61.amefix";
    private static final String PROJECT_URL = "https://github.com/Narish611/apple-music-fix";
    private static final String UPDATE_URL =
            "https://raw.githubusercontent.com/Narish611/apple-music-fix/main/update.json";
    private static final long POLL_MS = 1000L;
    private static final long PONG_VALID_MS = 3000L;
    private static final long LSP_CHECK_MS = 5000L;
    private static final String PREFS = "amfix";
    private static final String KEY_VER = "last_version";
    private static final String KEY_FIRST = "first_run_done";
    private static final String KEY_FONT_STOPPED = "lyrics_font_stopped_at";
    private static final String KEY_MODULE_PATH = "lyrics_font_module_path";

    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView status;
    private View statusDot;
    private volatile long lastPongAt;
    private volatile int lspEnabled = -1; // -1 unknown, 0 disabled, 1 enabled
    private boolean lspChecking;
    private long lspCheckedAt;
    private int lspFailCount;
    private boolean lspUnavailable;
    private boolean dark;
    private PopupWindow overflowMenu;
    private boolean masterOn = true;

    // Material 3 tonal palette (baseline purple scheme)
    private int cPrimary;
    private int cOnPrimary;
    private int cPrimaryContainer;
    private int cOnPrimaryContainer;
    private int cSecondaryContainer;
    private int cOnSecondaryContainer;
    private int cSurface;
    private int cSurfaceContainer;
    private int cOnSurface;
    private int cOnSurfaceVariant;
    private int cOutline;

    private int colorOk;
    private int colorBad;
    private int colorWarn;

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

        // 必须在写入任何偏好之前判断：数据被「清除数据」后 getAll() 为空，等同首次安装。
        final boolean freshInstall =
                getSharedPreferences(PREFS, MODE_PRIVATE).getAll().isEmpty();

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_MODULE_PATH, getApplicationInfo().sourceDir).apply();
        masterOn = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(MoreToolsActivity.KEY_MASTER_ENABLED, true);

        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        dark = night == Configuration.UI_MODE_NIGHT_YES;

        if (dark) {
            cPrimary = 0xFFD0BCFF;
            cOnPrimary = 0xFF381E72;
            cPrimaryContainer = 0xFF4F378B;
            cOnPrimaryContainer = 0xFFEADDFF;
            cSecondaryContainer = 0xFF4A4458;
            cOnSecondaryContainer = 0xFFE8DEF8;
            cSurface = 0xFF1C1B1F;
            cSurfaceContainer = 0xFF2B2930;
            cOnSurface = 0xFFE6E1E5;
            cOnSurfaceVariant = 0xFFCAC4D0;
            cOutline = 0xFF938F99;
            colorOk = 0xFF7DCB96;
            colorBad = 0xFFFFB4AB;
            colorWarn = 0xFFFFB74D;
        } else {
            cPrimary = 0xFF6750A4;
            cOnPrimary = 0xFFFFFFFF;
            cPrimaryContainer = 0xFFEADDFF;
            cOnPrimaryContainer = 0xFF21005D;
            cSecondaryContainer = 0xFFE8DEF8;
            cOnSecondaryContainer = 0xFF1E192B;
            cSurface = 0xFFFFFBFE;
            cSurfaceContainer = 0xFFF3EDF7;
            cOnSurface = 0xFF1D1B20;
            cOnSurfaceVariant = 0xFF49454F;
            cOutline = 0xFF79747E;
            colorOk = 0xFF2E7D32;
            colorBad = 0xFFB3261E;
            colorWarn = 0xFF9A6700;
        }

        float d = getResources().getDisplayMetrics().density;
        final int p = (int) (24 * d);
        final int pad = (int) (16 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cSurface);
        root.setPadding(pad, p, pad, pad);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(pad, p + insets.getSystemWindowInsetTop(),
                    pad, p + insets.getSystemWindowInsetBottom());
            return insets;
        });

        // M3 headline: title + tonal icon button
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(cOnSurface);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch master = new Switch(this);
        master.setThumbTintList(masterTint(cOnPrimary, cOutline, dark));
        master.setTrackTintList(masterTint(cPrimary, cOutline, dark));
        master.setChecked(masterOn);
        master.setContentDescription(getString(R.string.app_name));
        master.setOnCheckedChangeListener((button, checked) -> {
            masterOn = checked;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(MoreToolsActivity.KEY_MASTER_ENABLED, checked).apply();
            FontStateReceiver.pushState(this);
            updateStatus();
            Toast.makeText(this, checked
                            ? R.string.master_switch_on_toast
                            : R.string.master_switch_off_toast,
                    Toast.LENGTH_SHORT).show();
        });
        header.addView(master, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageButton more = new ImageButton(this);
        more.setImageResource(R.drawable.ic_more_vert);
        more.setBackground(roundRect(cSecondaryContainer, 50f));
        more.setColorFilter(cOnSecondaryContainer, PorterDuff.Mode.SRC_IN);
        more.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int gp = (int) (9 * d);
        more.setPadding(gp, gp, gp, gp);
        more.setContentDescription(getString(R.string.menu_more));
        more.setOnClickListener(v -> showOverflowMenu(v));
        int gs = (int) (44 * d);
        header.addView(more, new LinearLayout.LayoutParams(gs, gs));
        root.addView(header);

        // M3 body: supporting text
        TextView sub = new TextView(this);
        sub.setText(R.string.subtitle);
        sub.setTextSize(14);
        sub.setTextColor(cOnSurfaceVariant);
        sub.setPadding(0, (int) (8 * d), 0, (int) (24 * d));
        root.addView(sub);

        // M3 card: status surface container with leading state dot
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(roundRect(cSurfaceContainer, 16f));
        int cp = (int) (18 * d);
        card.setPadding(cp, cp, cp, cp);

        View dot = new View(this);
        statusDot = dot;
        dot.setBackground(dotShape(cPrimary));
        int ds = (int) (12 * d);
        card.addView(dot, new LinearLayout.LayoutParams(ds, ds));

        status = new TextView(this);
        status.setText(R.string.status_checking);
        status.setTextSize(17);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setTextColor(cOnSurfaceVariant);
        status.setPadding((int) (14 * d), 0, 0, 0);
        card.addView(status, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // M3 filled button: force stop
        Button stop = new Button(this);
        stop.setText(R.string.btn_force_stop);
        stop.setTextSize(15);
        stop.setTypeface(Typeface.DEFAULT_BOLD);
        stop.setTextColor(cOnPrimary);
        stop.setBackground(roundRect(cPrimary, 20f));
        stop.setAllCaps(false);
        stop.setOnClickListener(v -> forceStop());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (52 * d));
        stopLp.topMargin = (int) (20 * d);
        root.addView(stop, stopLp);

        // M3 body: tips in a tonal container
        TextView tip = new TextView(this);
        tip.setText(getString(R.string.tip_check) + "\n\n"
                + getString(R.string.tip_usage) + "\n\n"
                + getString(R.string.tip_version));
        tip.setTextSize(13);
        tip.setTextColor(cOnSurfaceVariant);
        tip.setBackground(roundRect(cSurfaceContainer, 16f));
        tip.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.topMargin = (int) (24 * d);
        root.addView(tip, tipLp);

        setContentView(root);

        applySystemBars();

        checkDialogs(freshInstall);
    }

    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(color);
        gd.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        return gd;
    }

    private GradientDrawable dotShape(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        return gd;
    }

    private ColorStateList masterTint(int checked, int unchecked, boolean night) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{},
        };
        return new ColorStateList(states, new int[]{checked, unchecked});
    }

    private void showOverflowMenu(View anchor) {
        if (overflowMenu != null && overflowMenu.isShowing()) {
            dismissOverflowMenu();
            return;
        }

        float d = getResources().getDisplayMetrics().density;
        int itemPadV = (int) (12 * d);
        int itemPadH = (int) (18 * d);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackground(roundRect(cSurfaceContainer, 12f));
        menu.setPadding(0, (int) (4 * d), 0, (int) (4 * d));

        TextView moreTools = overflowMenuItem(getString(R.string.menu_more_tools),
                itemPadH, itemPadV, v -> {
                    dismissOverflowMenu();
                    startActivity(new Intent(this, MoreToolsActivity.class));
                overridePendingTransition(R.anim.slide_in_right,
                        R.anim.slide_out_left);
                });
        TextView about = overflowMenuItem(getString(R.string.about_title),
                itemPadH, itemPadV, v -> {
                    dismissOverflowMenu();
                    startActivity(new Intent(this, AboutActivity.class));
                    overridePendingTransition(R.anim.slide_in_right,
                            R.anim.slide_out_left);
                });
        menu.addView(moreTools);
        menu.addView(about);

        overflowMenu = new PopupWindow(menu, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        overflowMenu.setOutsideTouchable(true);
        overflowMenu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            overflowMenu.setElevation(8 * d);
        }
        overflowMenu.setOnDismissListener(() -> overflowMenu = null);
        overflowMenu.showAsDropDown(anchor, 0, (int) (8 * d));
    }

    private TextView overflowMenuItem(String text, int padH, int padV,
                                      View.OnClickListener click) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextSize(15);
        item.setTextColor(cOnSurface);
        item.setPadding(padH, padV, padH, padV);
        item.setMinWidth((int) (132 * getResources().getDisplayMetrics().density));
        item.setOnClickListener(click);
        return item;
    }

    private void dismissOverflowMenu() {
        if (overflowMenu != null) {
            overflowMenu.dismiss();
            overflowMenu = null;
        }
    }

    @Override
    protected void onDestroy() {
        dismissOverflowMenu();
        super.onDestroy();
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

    private String currentVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * @param freshInstall 进入本页时偏好数据是否为空（首次安装，或刚执行过「清除数据」/
     *                     「模块设置恢复到刚安装状态」）。为空时一律按首次使用处理。
     */
    private void checkDialogs(boolean freshInstall) {
        long code = currentVersionCode();
        if (code < 0) {
            return;
        }
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean forced = sp.getBoolean(MoreToolsActivity.KEY_FORCE_NOTICE, false);
        boolean first = freshInstall || forced || !sp.getBoolean(KEY_FIRST, false);
        long last = sp.getLong(KEY_VER, 0L);
        sp.edit()
                .putBoolean(KEY_FIRST, true)
                .putLong(KEY_VER, code)
                .putBoolean(MoreToolsActivity.KEY_FORCE_NOTICE, false)
                .apply();
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

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void checkUpdate() {
        Toast.makeText(this, R.string.checking, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String body = httpGet(UPDATE_URL);
            int code = -1;
            String name = "";
            String url = "";
            try {
                JSONObject o = new JSONObject(body);
                code = o.optInt("versionCode", -1);
                name = o.optString("versionName", "");
                url = o.optString("url", "");
            } catch (Throwable ignored) {
            }
            final int fCode = code;
            final String fName = name;
            final String fUrl = url;
            runOnUiThread(() -> {
                if (fCode < 0) {
                    Toast.makeText(this, R.string.check_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                if (fCode > currentVersionCode()) {
                    dialogBuilder()
                            .setTitle(R.string.update_available)
                            .setMessage(getString(R.string.update_available_msg, fName))
                            .setPositiveButton(R.string.update_go, (d, w) ->
                                    openUrl(fUrl.isEmpty() ? PROJECT_URL + "/releases/latest" : fUrl))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else {
                    Toast.makeText(this, R.string.up_to_date, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private String httpGet(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "amfix");
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) {
                return null;
            }
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toString("UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        masterOn = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(MoreToolsActivity.KEY_MASTER_ENABLED, true);
        // 从更多工具页返回或宿主重启后，重新同步一次状态给 Hook。
        FontStateReceiver.pushState(this);
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
        // 模块总开关关闭时，状态行恒显示"未生效"。
        boolean ok = masterOn && hooked && wired && enabled;
        status.setText(ok ? R.string.status_active : R.string.status_inactive);
        int c = ok ? colorOk : colorBad;
        status.setTextColor(c);
        if (statusDot != null && statusDot.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) statusDot.getBackground()).setColor(c);
        }
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
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putLong(KEY_FONT_STOPPED, System.currentTimeMillis()).apply();
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

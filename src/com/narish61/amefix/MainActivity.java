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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    public static final String PROJECT_URL = "https://github.com/Narish611/apple-music-fix";
    private static final String PKG = "com.apple.android.music";
    private static final String SELF = "com.narish61.amefix";
    private static final String UPDATE_URL =
            "https://raw.githubusercontent.com/Narish611/apple-music-fix/main/update.json";
    private static final long POLL_MS = 1000L;
    private static final long PONG_VALID_MS = 3000L;
    private static final long LSP_CHECK_MS = 5000L;
    private static final String PREFS = "amfix";
    private static final String KEY_VER = "last_version";
    private static final String KEY_FIRST = "first_run_done";
    private static final String KEY_MODULE_PATH = "lyrics_font_module_path";
    private static final String HOST_DIR = "/data/data/" + PKG + "/files";
    private static final String HOST_FONT_PATH = HOST_DIR + "/amfix_custom_font.ttf";
    private static final int REQ_PICK_FONT = 1001;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Skin s;
    private ViewPager2 pager;
    private final View[] tabs = new View[3];
    private final TextView[] tabLabels = new TextView[3];
    private final ImageView[] tabIcons = new ImageView[3];
    private final FrameLayout[] tabPills = new FrameLayout[3];
    private HomePage homePage;
    private MorePage morePage;
    private AboutPage aboutPage;
    private FrameLayout splash;

    private volatile long lastPongAt;
    private volatile int lspEnabled = -1;
    private boolean lspChecking;
    private long lspCheckedAt;
    private int lspFailCount;
    private boolean lspUnavailable;
    private boolean updateChecked;
    boolean masterOn = true;

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

        final boolean freshInstall =
                getSharedPreferences(PREFS, MODE_PRIVATE).getAll().isEmpty();

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_MODULE_PATH, getApplicationInfo().sourceDir).apply();
        masterOn = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(Prefs.KEY_MASTER_ENABLED, true);

        s = new Skin(this);

        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(s.surface);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(s.surface);

        pager = new ViewPager2(this);
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setOffscreenPageLimit(2);
        homePage = new HomePage(this, s);
        morePage = new MorePage(this, s);
        aboutPage = new AboutPage(this, s);
        final View[] pages = new View[]{homePage.create(), morePage.create(), aboutPage.create()};
        pager.setAdapter(new RecyclerView.Adapter<PageHolder>() {
            @Override
            public PageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                FrameLayout wrap = new FrameLayout(parent.getContext());
                wrap.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return new PageHolder(wrap);
            }

            @Override
            public void onBindViewHolder(PageHolder holder, int position) {
                FrameLayout wrap = (FrameLayout) holder.itemView;
                if (pages[position].getParent() instanceof ViewGroup) {
                    ((ViewGroup) pages[position].getParent()).removeView(pages[position]);
                }
                wrap.removeAllViews();
                wrap.addView(pages[position], new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public int getItemCount() {
                return pages.length;
            }
        });
        column.addView(pager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout tabBar = buildTabBar();
        column.addView(tabBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        outer.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 启动遮罩：主题里的 windowBackground 负责冷启动首帧，这里负责进入后的淡出。
        splash = new FrameLayout(this);
        splash.setBackgroundColor(s.surface);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        int ls = s.dp(96);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(ls, ls);
        logoLp.gravity = Gravity.CENTER;
        splash.addView(logo, logoLp);
        outer.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(outer);
        applySystemBars();
        applyInsets(column, tabBar);
        selectTab(0);

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                selectTab(position);
                if (position == 1) {
                    morePage.refresh();
                }
            }
        });

        h.postDelayed(() -> fadeOutSplash(), 220L);
        h.postDelayed(this::checkUpdateSilently, 600L);
        checkDialogs(freshInstall);
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        PageHolder(View itemView) {
            super(itemView);
        }
    }

    private LinearLayout buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(s.surfaceContainer);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, s.dp(6), 0, s.dp(8));

        int[] icons = {R.drawable.ic_tab_home, R.drawable.ic_tab_more, R.drawable.ic_tab_about};
        int[] labels = {R.string.tab_home, R.string.tab_more, R.string.tab_about};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, s.dp(6), 0, s.dp(4));
            item.setOnClickListener(v -> pager.setCurrentItem(index, true));

            // M3 底部导航的选中态：图标后面一块 primaryContainer 药丸，比单纯变色明显得多。
            FrameLayout pill = new FrameLayout(this);
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            int is = s.dp(22);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(is, is);
            ilp.gravity = Gravity.CENTER;
            pill.addView(icon, ilp);
            item.addView(pill, new LinearLayout.LayoutParams(s.dp(56), s.dp(30)));

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(11);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = s.dp(4);
            item.addView(label, llp);

            tabLabels[i] = label;
            tabIcons[i] = icon;
            tabPills[i] = pill;
            bar.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tabs[i] = item;
        }
        return bar;
    }

    private void selectTab(int index) {
        int active = s.primary;
        int inactive = s.onSurfaceVariant;
        for (int i = 0; i < tabs.length; i++) {
            boolean on = i == index;
            tabPills[i].setBackground(on ? s.round(s.primaryContainer, 15f) : null);
            tabIcons[i].setColorFilter(on ? active : inactive,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            tabLabels[i].setTextColor(on ? active : inactive);
            tabLabels[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void applyInsets(final LinearLayout column, final LinearLayout tabBar) {
        column.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            tabBar.setPadding(0, s.dp(6), 0, s.dp(8) + insets.getSystemWindowInsetBottom());
            return insets;
        });
    }

    private void fadeOutSplash() {
        if (splash == null) {
            return;
        }
        splash.animate().alpha(0f).setDuration(260).withEndAction(() -> {
            if (splash != null && splash.getParent() instanceof ViewGroup) {
                ((ViewGroup) splash.getParent()).removeView(splash);
            }
            splash = null;
        }).start();
    }

    // ---------------- 状态与轮询 ----------------

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
        boolean ok = masterOn && hooked && wired && enabled;
        if (homePage != null) {
            homePage.setStatus(ok);
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

    // ---------------- 主页交互 ----------------

    ColorStateList masterTint(int checked, int unchecked, boolean night) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{},
        };
        return new ColorStateList(states, new int[]{checked, unchecked});
    }

    void onMasterChanged(boolean checked) {
        masterOn = checked;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(Prefs.KEY_MASTER_ENABLED, checked).apply();
        FontStateReceiver.pushState(this);
        updateStatus();
        Toast.makeText(this, checked
                        ? R.string.master_switch_on_toast
                        : R.string.master_switch_off_toast,
                Toast.LENGTH_SHORT).show();
    }

    void confirmRestart() {
        dialogBuilder()
                .setTitle(R.string.confirm_restart)
                .setMessage(R.string.confirm_restart_message)
                .setPositiveButton(R.string.btn_restart, (d, w) -> forceStop())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void forceStop() {
        Toast.makeText(this, R.string.state_stopping, Toast.LENGTH_SHORT).show();
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
                            .putLong(Prefs.KEY_FONT_STOPPED, System.currentTimeMillis())
                            .apply();
                    Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_LONG).show();
                    morePage.refresh();
                } else {
                    Toast.makeText(this, R.string.toast_stop_failed, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // ---------------- 字体设置 ----------------

    void onCustomFontCardClicked() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String name = sp.getString(Prefs.KEY_CUSTOM_FONT_NAME, "");
        if (name == null || name.length() == 0) {
            pickFont();
        } else {
            showCancelCustomFontDialog();
        }
    }

    private void pickFont() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "font/ttf", "font/otf", "application/x-font-ttf",
                    "application/octet-stream"});
            startActivityForResult(i, REQ_PICK_FONT);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_FONT || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        String name = displayName(uri);
        File dest = new File(getFilesDir(), "custom_font.ttf");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IllegalStateException("stream null");
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } catch (Throwable t) {
            Toast.makeText(this, R.string.font_copy_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean published = publishFontToHost(dest);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(Prefs.KEY_CUSTOM_FONT_NAME, name)
                .putString(Prefs.KEY_CUSTOM_FONT_PATH, HOST_FONT_PATH)
                .putLong(Prefs.KEY_CUSTOM_FONT_CHANGED, System.currentTimeMillis())
                .putString(KEY_MODULE_PATH, getApplicationInfo().sourceDir)
                .apply();
        Toast.makeText(this, published
                        ? R.string.custom_font_changed_toast
                        : R.string.custom_font_publish_failed,
                Toast.LENGTH_LONG).show();
        FontStateReceiver.pushState(this);
        morePage.refresh();
    }

    private void showCancelCustomFontDialog() {
        dialogBuilder()
                .setTitle(R.string.custom_cancel_title)
                .setMessage(R.string.custom_cancel_message)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(Prefs.KEY_CUSTOM_FONT_NAME, "")
                            .putString(Prefs.KEY_CUSTOM_FONT_PATH, "")
                            .putLong(Prefs.KEY_CUSTOM_FONT_CHANGED, 0L)
                            .apply();
                    new File(getFilesDir(), "custom_font.ttf").delete();
                    removeHostFont();
                    FontStateReceiver.pushState(this);
                    morePage.refresh();
                    Toast.makeText(this, R.string.font_disable_toast, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    void showFontRestoreDialog() {
        dialogBuilder()
                .setTitle(R.string.restore_confirm_title)
                .setMessage(R.string.restore_confirm_message)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putBoolean(Prefs.KEY_FONT_ENABLED, false)
                            .putString(Prefs.KEY_CUSTOM_FONT_NAME, "")
                            .putString(Prefs.KEY_CUSTOM_FONT_PATH, "")
                            .putLong(Prefs.KEY_CUSTOM_FONT_CHANGED, 0L)
                            .apply();
                    new File(getFilesDir(), "custom_font.ttf").delete();
                    removeHostFont();
                    FontStateReceiver.pushState(this);
                    morePage.refresh();
                    Toast.makeText(this, R.string.restore_done_toast, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    void showModuleResetDialog() {
        dialogBuilder()
                .setTitle(R.string.module_reset_confirm_title)
                .setMessage(R.string.module_reset_confirm_message)
                .setPositiveButton(R.string.ok, (d, w) -> resetModule())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void resetModule() {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear()
                    .putBoolean(Prefs.KEY_MASTER_ENABLED, false)
                    .putBoolean(Prefs.KEY_FORCE_NOTICE, true)
                    .commit();
            new File(getFilesDir(), "custom_font.ttf").delete();
            removeHostFont();
            FontStateReceiver.pushState(this);
        } catch (Throwable ignored) {
        }
        finishAffinity();
    }

    private boolean publishFontToHost(File src) {
        try {
            String dataDir = "/data/data/" + PKG;
            String extDir = "/data/media/0/Android/data/" + PKG + "/files";
            String cmd = "set +e\n"
                    + "src='" + src.getAbsolutePath() + "'\n"
                    + "dst='" + HOST_FONT_PATH + "'\n"
                    + "dir='" + HOST_DIR + "'\n"
                    + "test -s \"$src\" || exit 11\n"
                    + "mkdir -p \"$dir\"\n"
                    + "uid=$(stat -c '%u' \"" + dataDir + "\")\n"
                    + "gid=$(stat -c '%g' \"" + dataDir + "\")\n"
                    + "cat \"$src\" > \"$dst\" || exit 12\n"
                    + "chown $uid:$gid \"$dst\"\n"
                    + "chmod 600 \"$dst\"\n"
                    + "chcon --reference=\"$dir\" \"$dst\" 2>/dev/null || restorecon -F \"$dst\" 2>/dev/null\n"
                    + "mkdir -p \"" + extDir + "\" 2>/dev/null\n"
                    + "cat \"$src\" > \"" + extDir + "/amfix_custom_font.ttf\" 2>/dev/null\n"
                    + "chmod 644 \"" + extDir + "/amfix_custom_font.ttf\" 2>/dev/null\n"
                    + "test -s \"$dst\" || test -s \"" + extDir + "/amfix_custom_font.ttf\"\n";
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            drain(proc.getErrorStream());
            boolean finished = proc.waitFor(20, TimeUnit.SECONDS);
            int code = finished ? proc.exitValue() : -1;
            if (!finished) {
                proc.destroy();
            }
            return finished && code == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private void removeHostFont() {
        try {
            String ext = "/data/media/0/Android/data/" + PKG + "/files/amfix_custom_font.ttf";
            Runtime.getRuntime().exec(new String[]{"su", "-c",
                    "rm -f '" + HOST_FONT_PATH + "' '" + ext + "'"})
                    .waitFor(5, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    name = c.getString(idx);
                }
            }
        } catch (Throwable ignored) {
        }
        if (name == null) {
            String last = uri.getLastPathSegment();
            name = last == null ? "custom.ttf" : last;
        }
        return name;
    }

    private static String drain(InputStream in) {
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

    // ---------------- 版本信息 ----------------

    String moduleVersionLabel() {
        return moduleVersionName() + " (" + moduleVersionCode() + ")";
    }

    String moduleVersionName() {
        return currentVersionName();
    }

    long moduleVersionCode() {
        return currentVersionCode();
    }

    String appleMusicVersionLabel() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(PKG, 0);
            String name = pi.versionName == null ? "" : pi.versionName;
            long code;
            if (Build.VERSION.SDK_INT >= 28) {
                code = pi.getLongVersionCode();
            } else {
                code = pi.versionCode;
            }
            return name + " (" + code + ")";
        } catch (Throwable t) {
            return getString(R.string.home_am_absent);
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

    // ---------------- 对话框与更新 ----------------

    private void checkDialogs(boolean freshInstall) {
        long code = currentVersionCode();
        if (code < 0) {
            return;
        }
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean forced = sp.getBoolean(Prefs.KEY_FORCE_NOTICE, false);
        boolean first = freshInstall || forced || !sp.getBoolean(KEY_FIRST, false);
        long last = sp.getLong(KEY_VER, 0L);
        sp.edit()
                .putBoolean(KEY_FIRST, true)
                .putLong(KEY_VER, code)
                .putBoolean(Prefs.KEY_FORCE_NOTICE, false)
                .apply();
        if (first) {
            showNoticeDialog();
        } else if (last != code) {
            showUpdatedDialog();
        }
    }

    private AlertDialog.Builder dialogBuilder() {
        return s.dark
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
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .show();
    }

    void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 启动时静默检查一次：只在主页点亮「有更新」，不弹窗、不打断用户。 */
    private void checkUpdateSilently() {
        if (updateChecked) {
            return;
        }
        updateChecked = true;
        new Thread(() -> {
            String body = httpGet(UPDATE_URL);
            int code = -1;
            String url = "";
            try {
                JSONObject o = new JSONObject(body);
                code = o.optInt("versionCode", -1);
                url = o.optString("url", "");
            } catch (Throwable ignored) {
            }
            final int fCode = code;
            final String fUrl = url;
            runOnUiThread(() -> {
                if (fCode < 0 || homePage == null) {
                    return;
                }
                homePage.setUpdateAvailable(fCode > currentVersionCode(), fUrl);
            });
        }).start();
    }

    void checkUpdate(final TextView stateView) {
        if (stateView != null) {
            stateView.setText(R.string.checking);
            stateView.setTextColor(s.onSurfaceVariant);
            stateView.setVisibility(View.VISIBLE);
        }
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
                    if (stateView != null) {
                        stateView.setText(R.string.check_failed);
                        stateView.setTextColor(s.bad);
                    }
                    return;
                }
                if (fCode > currentVersionCode()) {
                    if (homePage != null) {
                        homePage.setUpdateAvailable(true, fUrl);
                    }
                    if (stateView != null) {
                        stateView.setText(R.string.update_available);
                        stateView.setTextColor(s.ok);
                    }
                    dialogBuilder()
                            .setTitle(R.string.update_available)
                            .setMessage(getString(R.string.update_available_msg, fName))
                            .setPositiveButton(R.string.update_go, (d, w) ->
                                    openUrl(fUrl.isEmpty()
                                            ? PROJECT_URL + "/releases/latest" : fUrl))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else if (stateView != null) {
                    stateView.setText(R.string.up_to_date);
                    stateView.setTextColor(s.ok);
                }
                if (homePage != null && fCode <= currentVersionCode()) {
                    homePage.setUpdateAvailable(false, fUrl);
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

    // ---------------- 生命周期 ----------------

    private void applySystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController wic = getWindow().getInsetsController();
            if (wic != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                wic.setSystemBarsAppearance(s.dark ? 0 : mask, mask);
            }
        } else {
            int flags = s.dark ? 0
                    : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        masterOn = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(Prefs.KEY_MASTER_ENABLED, true);
        if (homePage != null) {
            homePage.refreshMaster(masterOn);
        }
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

    @Override
    public void onBackPressed() {
        if (pager != null && pager.getCurrentItem() != 0) {
            pager.setCurrentItem(0, true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

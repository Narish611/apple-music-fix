package com.narish61.amefix;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class MoreToolsActivity extends Activity {

    private static final String PKG = "com.apple.android.music";
    public static final String PREFS_PUBLIC = "amfix";
    private static final String PREFS = PREFS_PUBLIC;
    public static final String KEY_FONT_STOPPED = "lyrics_font_stopped_at";
    public static final String KEY_MODULE_PATH = "lyrics_font_module_path";
    public static final String KEY_MASTER_ENABLED = "module_master_enabled";
    public static final String KEY_CUSTOM_FONT_NAME = "custom_font_name";
    public static final String KEY_CUSTOM_FONT_PATH = "custom_font_path";
    public static final String KEY_CUSTOM_FONT_CHANGED = "custom_font_changed_at";
    // 一次性标记：数据被清除/重置后，下次进入首页强制弹「使用注意事项」。
    public static final String KEY_FORCE_NOTICE = "force_notice_once";
    private static final String CUSTOM_FONT_FILE = "custom_font.ttf";
    // 主通道：借助 root 把字体复制到宿主自己的 files 目录并 chown 给宿主 uid。
    // 宿主进程能读自己的私有目录，不受 UID 隔离 / 包可见性 / SELinux 影响。
    // 备用通道：ContentProvider（content://），host 可见时可用。
    private static final String HOST_FONT_DIR = "/data/data/" + PKG + "/files";
    private static final String HOST_FONT_PATH = HOST_FONT_DIR + "/amfix_custom_font.ttf";
    private static final int REQ_PICK_FONT = 1001;

    private boolean dark;
    private int cSurface;
    private int cSurfaceContainer;
    private int cOnSurface;
    private int cOnSurfaceVariant;
    private int colorOk;
    private int colorBad;

    private TextView customState;
    private TextView customSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        dark = night == Configuration.UI_MODE_NIGHT_YES;

        if (dark) {
            cSurface = 0xFF1C1B1F;
            cSurfaceContainer = 0xFF2B2930;
            cOnSurface = 0xFFE6E1E5;
            cOnSurfaceVariant = 0xFFCAC4D0;
            colorOk = 0xFF7DCB96;
            colorBad = 0xFFFFB4AB;
        } else {
            cSurface = 0xFFFFFBFE;
            cSurfaceContainer = 0xFFF3EDF7;
            cOnSurface = 0xFF1D1B20;
            cOnSurfaceVariant = 0xFF49454F;
            colorOk = 0xFF2E7D32;
            colorBad = 0xFFB3261E;
        }

        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);
        int top = (int) (24 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cSurface);
        root.setPadding(pad, top, pad, pad);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(pad, top + insets.getSystemWindowInsetTop(),
                    pad, pad + insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setColorFilter(cOnSurface, PorterDuff.Mode.SRC_IN);
        back.setBackground(roundRect(cSurfaceContainer, 50f));
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int bp = (int) (9 * d);
        back.setPadding(bp, bp, bp, bp);
        back.setContentDescription(getString(android.R.string.cancel));
        back.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left,
                    R.anim.slide_out_right);
        });
        int bs = (int) (44 * d);
        header.addView(back, new LinearLayout.LayoutParams(bs, bs));

        TextView title = new TextView(this);
        title.setText(R.string.more_tools_title);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(cOnSurface);
        title.setPadding((int) (12 * d), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton restart = new ImageButton(this);
        restart.setImageResource(R.drawable.ic_restart_alt);
        restart.setColorFilter(cOnSecondaryContainer(this), PorterDuff.Mode.SRC_IN);
        restart.setBackground(roundRect(cSecondaryContainer(this), 50f));
        restart.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int rp = (int) (10 * d);
        restart.setPadding(rp, rp, rp, rp);
        restart.setContentDescription(getString(R.string.btn_force_stop));
        restart.setOnClickListener(v -> showForceStopDialog());
        int rs = (int) (44 * d);
        header.addView(restart, new LinearLayout.LayoutParams(rs, rs));
        root.addView(header);

        // 页面说明：本页选项独立于首页总开关，避免被误认为同样失效。
        TextView notice = new TextView(this);
        notice.setText(R.string.more_tools_notice);
        notice.setTextSize(13);
        notice.setTextColor(cOnSurfaceVariant);
        notice.setBackground(roundRect(cSurfaceContainer, 12f));
        int np = (int) (12 * d);
        notice.setPadding(np, np, np, np);
        LinearLayout.LayoutParams noticeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noticeLp.topMargin = (int) (16 * d);
        root.addView(notice, noticeLp);

        final SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        // ---- Card 1: 歌词页指定字体（点选文件 + 箭头 + 生效状态）----
        LinearLayout customCard = newCard(d);
        LinearLayout customTexts = new LinearLayout(this);
        customTexts.setOrientation(LinearLayout.VERTICAL);
        TextView ct = new TextView(this);
        ct.setText(R.string.custom_font_title);
        ct.setTextSize(16);
        ct.setTypeface(Typeface.DEFAULT_BOLD);
        ct.setTextColor(cOnSurface);
        customTexts.addView(ct);
        customSubtitle = new TextView(this);
        customSubtitle.setTextSize(13);
        customSubtitle.setTextColor(cOnSurfaceVariant);
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        csLp.topMargin = (int) (4 * d);
        customTexts.addView(customSubtitle, csLp);
        customCard.addView(customTexts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        customState = stateView(d);
        customCard.addView(customState, stateLp(d));

        ImageView chevron1 = new ImageView(this);
        chevron1.setImageResource(R.drawable.ic_chevron_right);
        chevron1.setColorFilter(cOnSurfaceVariant, PorterDuff.Mode.SRC_IN);
        int chp = (int) (4 * d);
        chevron1.setPadding(chp, chp, chp, chp);
        customCard.addView(chevron1, new LinearLayout.LayoutParams(
                (int) (24 * d), (int) (24 * d)));
        customCard.setOnClickListener(v -> {
            String name = sp.getString(KEY_CUSTOM_FONT_NAME, "");
            if (name == null || name.length() == 0) {
                // 还没选过：点一下直接进入选择。
                pickFont();
            } else {
                // 已经指定了字体：先确认是否取消，避免误触把配置清掉。
                showCancelCustomFontDialog();
            }
        });
        root.addView(customCard, cardLp(d));

        // ---- Card 2: 恢复默认设置（箭头）----
        LinearLayout restoreCard = newCard(d);
        LinearLayout restoreTexts = new LinearLayout(this);
        restoreTexts.setOrientation(LinearLayout.VERTICAL);
        TextView rt = new TextView(this);
        rt.setText(R.string.restore_default_title);
        rt.setTextSize(16);
        rt.setTypeface(Typeface.DEFAULT_BOLD);
        rt.setTextColor(colorBad);
        restoreTexts.addView(rt);
        TextView rsb = new TextView(this);
        rsb.setText(R.string.restore_default_subtitle);
        rsb.setTextSize(13);
        rsb.setTextColor(cOnSurfaceVariant);
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rbLp.topMargin = (int) (4 * d);
        restoreTexts.addView(rsb, rbLp);
        restoreCard.addView(restoreTexts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron2 = new ImageView(this);
        chevron2.setImageResource(R.drawable.ic_chevron_right);
        chevron2.setColorFilter(cOnSurfaceVariant, PorterDuff.Mode.SRC_IN);
        chevron2.setPadding(chp, chp, chp, chp);
        restoreCard.addView(chevron2, new LinearLayout.LayoutParams(
                (int) (24 * d), (int) (24 * d)));
        restoreCard.setOnClickListener(v -> showRestoreDialog());
        root.addView(restoreCard, cardLp(d));

        // ---- Card 3: 模块设置恢复到刚安装状态（危险操作，标题为红色）----
        LinearLayout moduleCard = newCard(d);
        LinearLayout moduleTexts = new LinearLayout(this);
        moduleTexts.setOrientation(LinearLayout.VERTICAL);
        TextView mt = new TextView(this);
        mt.setText(R.string.module_reset_title);
        mt.setTextSize(16);
        mt.setTypeface(Typeface.DEFAULT_BOLD);
        mt.setTextColor(colorBad);
        moduleTexts.addView(mt);
        TextView msb = new TextView(this);
        msb.setText(R.string.module_reset_subtitle);
        msb.setTextSize(13);
        msb.setTextColor(cOnSurfaceVariant);
        LinearLayout.LayoutParams mbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mbLp.topMargin = (int) (4 * d);
        moduleTexts.addView(msb, mbLp);
        moduleCard.addView(moduleTexts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron3 = new ImageView(this);
        chevron3.setImageResource(R.drawable.ic_chevron_right);
        chevron3.setColorFilter(cOnSurfaceVariant, PorterDuff.Mode.SRC_IN);
        chevron3.setPadding(chp, chp, chp, chp);
        moduleCard.addView(chevron3, new LinearLayout.LayoutParams(
                (int) (24 * d), (int) (24 * d)));
        moduleCard.setOnClickListener(v -> showModuleResetDialog());
        root.addView(moduleCard, cardLp(d));

        sp.edit().putString(KEY_MODULE_PATH, getApplicationInfo().sourceDir).apply();
        setContentView(root);

        refreshCustomState();
        FontStateReceiver.pushState(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCustomState();
    }

    private void pickFont() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_PICK_FONT);
        } catch (Throwable ignored) {
            Toast.makeText(this, R.string.custom_font_subtitle, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_FONT || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        String name = displayName(uri);
        if (name == null || !name.toLowerCase().endsWith(".ttf")) {
            Toast.makeText(this, R.string.custom_font_subtitle, Toast.LENGTH_SHORT).show();
            return;
        }
        File dest = new File(getFilesDir(), CUSTOM_FONT_FILE);
        File tmp = new File(getFilesDir(), CUSTOM_FONT_FILE + ".tmp");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) {
                throw new IllegalStateException("null input");
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            Toast.makeText(this, R.string.custom_font_subtitle, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!tmp.exists() || tmp.length() < 12) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            Toast.makeText(this, R.string.custom_font_subtitle, Toast.LENGTH_SHORT).show();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        dest.delete();
        if (!tmp.renameTo(dest)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            Toast.makeText(this, R.string.custom_font_subtitle, Toast.LENGTH_SHORT).show();
            return;
        }
        // 主通道：root 复制到宿主可读位置（内部私有目录 + 外部私有目录双落点）。
        // Hook 端会依次尝试主路径、内部/外部落点，最后才试 ContentProvider。
        // 因此这里固定存主路径，让 Hook 自己把全部落点走一遍。
        publishFontToHost(dest);
        String hookPath = HOST_FONT_PATH;
        // 指定字体即启用：Hook 端只看 customPath 是否为空来决定是否替换歌词字体。
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_CUSTOM_FONT_NAME, name)
                .putString(KEY_CUSTOM_FONT_PATH, hookPath)
                .putLong(KEY_CUSTOM_FONT_CHANGED, System.currentTimeMillis())
                .apply();
        refreshCustomState();
        FontStateReceiver.pushState(this);
        Toast.makeText(this, R.string.custom_font_changed_toast, Toast.LENGTH_SHORT).show();
    }

    /**
     * 借助 root 把字体复制到宿主可读位置，保证宿主进程能直接读。
     * 主落点：宿主内部私有目录（/data/data/<pkg>/files），需 chown 宿主 uid 并用
     * chcon --reference 精确复制父目录的 SELinux 上下文（含 category，否则宿主 stat 被拒）。
     * 备用落点：宿主外部私有目录（/data/media/0/Android/data/<pkg>/files，宿主 FUSE 可读）。
     */
    private boolean publishFontToHost(File src) {
        try {
            String dataDir = "/data/data/" + PKG;
            String extDir = "/data/media/0/Android/data/" + PKG + "/files";
            String cmd = "set +e\n"
                    + "src='" + src.getAbsolutePath() + "'\n"
                    + "dst='" + HOST_FONT_PATH + "'\n"
                    + "dir='" + HOST_FONT_DIR + "'\n"
                    + "test -s \"$src\" || exit 11\n"
                    + "mkdir -p \"$dir\"\n"
                    + "uid=$(stat -c '%u' \"" + dataDir + "\")\n"
                    + "gid=$(stat -c '%g' \"" + dataDir + "\")\n"
                    + "cat \"$src\" > \"$dst\" || exit 12\n"
                    + "chown $uid:$gid \"$dst\"\n"
                    + "chmod 600 \"$dst\"\n"
                    // 关键：复制宿主 files 目录的完整 SELinux 上下文（含 category）。
                    // 只 chcon 到 app_data_file:s0 会丢失 category，宿主反而读不到。
                    + "chcon --reference=\"$dir\" \"$dst\" 2>/dev/null || restorecon -F \"$dst\" 2>/dev/null\n"
                    // 备用落点：真实路径写入，宿主经 /sdcard FUSE 读自己的外部私有目录。
                    + "mkdir -p \"" + extDir + "\" 2>/dev/null\n"
                    + "cat \"$src\" > \"" + extDir + "/amfix_custom_font.ttf\" 2>/dev/null\n"
                    + "chmod 644 \"" + extDir + "/amfix_custom_font.ttf\" 2>/dev/null\n"
                    + "test -s \"$dst\" || test -s \"" + extDir + "/amfix_custom_font.ttf\"\n";
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            String err = drain(proc.getErrorStream());
            boolean finished = proc.waitFor(12, TimeUnit.SECONDS);
            int code = finished ? proc.exitValue() : -1;
            if (!finished) {
                proc.destroy();
            }
            android.util.Log.i("AMFix", "publishFontToHost exit=" + code + " err=" + err.trim());
            return finished && code == 0;
        } catch (Throwable t) {
            android.util.Log.w("AMFix", "publishFontToHost error: " + t);
        }
        return false;
    }

    private static String drain(java.io.InputStream in) {
        StringBuilder sb = new StringBuilder();
        if (in == null) {
            return "";
        }
        try {
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            in.close();
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private void removeHostFont() {
        try {
            String ext = "/data/media/0/Android/data/" + PKG + "/files/amfix_custom_font.ttf";
            Runtime.getRuntime().exec(
                    new String[]{"su", "-c",
                            "rm -f '" + HOST_FONT_PATH + "' '" + ext + "'"})
                    .waitFor(5, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
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

    /**
     * 修改指定字体：未选过字体 -> 不显示；选过且未强停 -> 未生效；选过且已强停 -> 已生效。
     */
    private void refreshCustomState() {
        if (customState == null || customSubtitle == null) {
            return;
        }
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String name = sp.getString(KEY_CUSTOM_FONT_NAME, "");
        long changed = sp.getLong(KEY_CUSTOM_FONT_CHANGED, 0L);
        customSubtitle.setText(name == null || name.length() == 0
                ? getString(R.string.custom_font_subtitle) : name);
        if (changed <= 0L) {
            customState.setVisibility(View.GONE);
            return;
        }
        showState(customState, changed, sp.getLong(KEY_FONT_STOPPED, 0L));
    }

    private void showState(TextView view, long changed, long stopped) {
        view.setVisibility(View.VISIBLE);
        if (changed > stopped) {
            view.setText(R.string.font_state_pending);
            view.setTextColor(colorBad);
        } else {
            view.setText(R.string.font_state_active);
            view.setTextColor(colorOk);
        }
    }

    /** 已指定字体时再次点击该行：先确认是否取消，避免误触清掉配置。 */
    private void showCancelCustomFontDialog() {
        AlertDialog.Builder builder = dark
                ? new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                : new AlertDialog.Builder(this);
        builder.setTitle(R.string.custom_cancel_title)
                .setMessage(R.string.custom_cancel_message)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    d.dismiss();
                    clearCustomFont();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .show();
    }

    /** 取消指定字体：删掉字体文件与配置，回到「未设置」状态。 */
    private void clearCustomFont() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        //noinspection ResultOfMethodCallIgnored
        new File(getFilesDir(), CUSTOM_FONT_FILE).delete();
        removeHostFont();
        sp.edit()
                .putString(KEY_CUSTOM_FONT_NAME, "")
                .putString(KEY_CUSTOM_FONT_PATH, "")
                .putLong(KEY_CUSTOM_FONT_CHANGED, 0L)
                .apply();
        refreshCustomState();
        FontStateReceiver.pushState(this);
        // 与关闭字体功能使用同一个提醒。
        Toast.makeText(this, R.string.font_disable_toast, Toast.LENGTH_SHORT).show();
    }

    private void showModuleResetDialog() {
        AlertDialog.Builder builder = dark
                ? new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                : new AlertDialog.Builder(this);
        builder.setTitle(R.string.module_reset_confirm_title)
                .setMessage(R.string.module_reset_confirm_message)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    d.dismiss();
                    resetModule();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .show();
    }

    /**
     * 恢复到刚安装状态：等同系统设置里的「清除数据」。
     * 清空全部 SharedPreferences、删除私有字体与宿主侧字体副本、关闭所有功能；
     * 首次使用标记一并清除，因此下次打开会重新弹出首次使用提示。
     */
    private void resetModule() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        //noinspection ResultOfMethodCallIgnored
        new File(getFilesDir(), CUSTOM_FONT_FILE).delete();
        //noinspection ResultOfMethodCallIgnored
        new File(getFilesDir(), CUSTOM_FONT_FILE + ".tmp").delete();
        removeHostFont();
        // 清空全部数据后显式关闭总开关，回到「已安装、未启用」的状态；
        // 同时留下一次性标记，保证下次进入首页仍会弹「使用注意事项」。
        sp.edit().clear()
                .putBoolean(KEY_MASTER_ENABLED, false)
                .putBoolean(KEY_FORCE_NOTICE, true)
                .commit();
        // 通知宿主进程：功能全部关闭，已应用的歌词字体立即还原。
        FontStateReceiver.pushState(this);
        // 退出整个 App（含首页），下次打开会走首次使用流程。
        finishAffinity();
    }

    private void showRestoreDialog() {
        AlertDialog.Builder builder = dark
                ? new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                : new AlertDialog.Builder(this);
        builder.setTitle(R.string.restore_confirm_title)
                .setMessage(R.string.restore_confirm_message)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    d.dismiss();
                    restoreDefaults();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void restoreDefaults() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        //noinspection ResultOfMethodCallIgnored
        new File(getFilesDir(), CUSTOM_FONT_FILE).delete();
        removeHostFont();
        sp.edit()
                .putLong(KEY_FONT_STOPPED, 0L)
                .putString(KEY_CUSTOM_FONT_NAME, "")
                .putString(KEY_CUSTOM_FONT_PATH, "")
                .putLong(KEY_CUSTOM_FONT_CHANGED, 0L)
                .apply();
        refreshCustomState();
        FontStateReceiver.pushState(this);
        forceStop("restore");
    }

    private void showForceStopDialog() {
        AlertDialog.Builder builder = dark
                ? new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                : new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_force_stop)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    d.dismiss();
                    forceStop(null);
                })
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .show();
    }

    /**
     * @param mode null=常规强停提示；"restore"=恢复默认设置的提示。
     */
    private void forceStop(final String mode) {
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
                    if ("restore".equals(mode)) {
                        Toast.makeText(this, R.string.restore_done_toast, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, R.string.toast_stop_failed, Toast.LENGTH_LONG).show();
                }
                refreshCustomState();
            });
        }).start();
    }

    private LinearLayout newCard(float d) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(roundRect(cSurfaceContainer, 16f));
        int cp = (int) (18 * d);
        card.setPadding(cp, cp, cp, cp);
        return card;
    }

    private LinearLayout.LayoutParams cardLp(float d) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (16 * d);
        return lp;
    }

    private TextView stateView(float d) {
        TextView v = new TextView(this);
        v.setTextSize(13);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setVisibility(View.GONE);
        return v;
    }

    private LinearLayout.LayoutParams stateLp(float d) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.rightMargin = (int) (12 * d);
        return lp;
    }

    private int cSecondaryContainer(Context c) {
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? 0xFF4A4458 : 0xFFE8DEF8;
    }

    private int cOnSecondaryContainer(Context c) {
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? 0xFFE8DEF8 : 0xFF1E192B;
    }

    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(color);
        gd.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        return gd;
    }
}

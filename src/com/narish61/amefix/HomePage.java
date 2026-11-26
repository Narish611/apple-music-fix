package com.narish61.amefix;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** 主页：标题 + 重启 + 状态 + 总开关 + 两段说明 + 版本信息。 */
final class HomePage {

    private final MainActivity host;
    private final Skin s;
    private TextView statusText;
    private View statusDot;
    private Switch masterSwitch;

    HomePage(MainActivity host, Skin skin) {
        this.host = host;
        this.s = skin;
    }

    View create() {
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(s.surface);

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = s.dp(20);
        root.setPadding(pad, s.dp(12), pad, s.dp(24));

        // ---- 标题行 ----
        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(host);
        title.setText("AMFix");
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(s.onSurface);
        title.setLetterSpacing(-0.02f);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton restart = new ImageButton(host);
        restart.setImageResource(R.drawable.ic_restart_alt);
        restart.setColorFilter(s.onPrimary, android.graphics.PorterDuff.Mode.SRC_IN);
        restart.setBackground(s.round(s.primary, 50f));
        restart.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int rp = s.dp(10);
        restart.setPadding(rp, rp, rp, rp);
        restart.setContentDescription(host.getString(R.string.btn_restart));
        restart.setOnClickListener(v -> host.confirmRestart());
        int rs = s.dp(44);
        header.addView(restart, new LinearLayout.LayoutParams(rs, rs));
        root.addView(header);

        // ---- 状态 ----
        LinearLayout statusCard = new LinearLayout(host);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setBackground(s.round(s.surfaceContainer, 16f));
        int cp = s.dp(16);
        statusCard.setPadding(cp, cp, cp, cp);

        statusDot = new View(host);
        statusDot.setBackground(s.dot(s.primary));
        int ds = s.dp(10);
        statusCard.addView(statusDot, new LinearLayout.LayoutParams(ds, ds));

        statusText = new TextView(host);
        statusText.setText(R.string.status_checking);
        statusText.setTextSize(16);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setTextColor(s.onSurfaceVariant);
        statusText.setPadding(s.dp(12), 0, 0, 0);
        statusCard.addView(statusText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = s.dp(18);
        root.addView(statusCard, statusLp);

        // ---- 总开关 ----
        LinearLayout masterRow = new LinearLayout(host);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);
        masterRow.setGravity(Gravity.CENTER_VERTICAL);
        masterRow.setBackground(s.round(s.surfaceContainer, 16f));
        masterRow.setPadding(cp, s.dp(8), cp, s.dp(8));

        TextView masterLabel = new TextView(host);
        masterLabel.setText(R.string.home_switch_title);
        masterLabel.setTextSize(16);
        masterLabel.setTypeface(Typeface.DEFAULT_BOLD);
        masterLabel.setTextColor(s.onSurface);
        masterRow.addView(masterLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        masterSwitch = new Switch(host);
        // 开启时滑块用主色（和右上角重启按钮同色），轨道用主色容器，深浅色模式都成立。
        masterSwitch.setThumbTintList(host.masterTint(s.primary, s.outline, s.dark));
        masterSwitch.setTrackTintList(host.masterTint(
                s.primaryContainer, Skin.alpha(s.outline, 0.45f), s.dark));
        masterSwitch.setChecked(host.masterOn);
        masterSwitch.setOnCheckedChangeListener((b, checked) -> host.onMasterChanged(checked));
        masterRow.addView(masterSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams masterLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        masterLp.topMargin = s.dp(12);
        root.addView(masterRow, masterLp);

        // ---- 说明 ----
        TextView tips = new TextView(host);
        tips.setText(host.getString(R.string.tip_check) + "\n\n"
                + host.getString(R.string.tip_usage));
        tips.setTextSize(13);
        tips.setLineSpacing(s.dp(2), 1f);
        tips.setTextColor(s.onSurfaceVariant);
        tips.setBackground(s.round(s.surfaceContainer, 16f));
        tips.setPadding(cp, cp, cp, cp);
        LinearLayout.LayoutParams tipsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipsLp.topMargin = s.dp(12);
        root.addView(tips, tipsLp);

        // ---- 版本信息 ----
        LinearLayout versionCard = new LinearLayout(host);
        versionCard.setOrientation(LinearLayout.VERTICAL);
        versionCard.setBackground(s.round(s.surfaceContainer, 16f));
        versionCard.setPadding(cp, cp, cp, cp);
        addVersionBlock(versionCard, host.getString(R.string.home_module_version),
                host.moduleVersionLabel());
        addVersionBlock(versionCard, host.getString(R.string.home_am_version),
                host.appleMusicVersionLabel());
        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        verLp.topMargin = s.dp(12);
        root.addView(versionCard, verLp);

        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void addVersionBlock(LinearLayout parent, String label, String value) {
        TextView lab = new TextView(host);
        lab.setText(label);
        lab.setTextSize(15);
        lab.setTypeface(Typeface.DEFAULT_BOLD);
        lab.setTextColor(s.onSurface);
        parent.addView(lab);

        TextView val = new TextView(host);
        val.setText(value);
        val.setTextSize(13);
        val.setTextColor(s.onSurfaceVariant);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = s.dp(3);
        lp.bottomMargin = s.dp(12);
        parent.addView(val, lp);
    }

    void setStatus(boolean ok) {
        if (statusText == null) {
            return;
        }
        int color = ok ? s.ok : s.bad;
        statusText.setText(ok ? R.string.status_active : R.string.status_inactive);
        statusText.setTextColor(color);
        if (statusDot != null && statusDot.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
            ((android.graphics.drawable.GradientDrawable) statusDot.getBackground()).setColor(color);
        }
    }

    void refreshMaster(boolean on) {
        if (masterSwitch != null && masterSwitch.isChecked() != on) {
            masterSwitch.setChecked(on);
        }
    }
}

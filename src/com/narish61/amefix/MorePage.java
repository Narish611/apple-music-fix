package com.narish61.amefix;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 更多选项页：说明 + 指定字体 + 字体恢复默认。 */
final class MorePage {

    private final MainActivity host;
    private final Skin s;
    private TextView customSubtitle;
    private TextView customState;

    MorePage(MainActivity host, Skin skin) {
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

        root.addView(header(R.string.tab_more));

        TextView notice = new TextView(host);
        notice.setText(R.string.more_tools_notice);
        notice.setTextSize(13);
        notice.setLineSpacing(s.dp(2), 1f);
        notice.setTextColor(s.onSurfaceVariant);
        notice.setBackground(s.round(s.surfaceContainer, 12f));
        int np = s.dp(14);
        notice.setPadding(np, np, np, np);
        root.addView(notice);

        // ---- 指定字体 ----
        LinearLayout customCard = card();
        LinearLayout texts = new LinearLayout(host);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView ct = new TextView(host);
        ct.setText(R.string.custom_font_title);
        ct.setTextSize(16);
        ct.setTypeface(Typeface.DEFAULT_BOLD);
        ct.setTextColor(s.onSurface);
        texts.addView(ct);

        customSubtitle = new TextView(host);
        customSubtitle.setTextSize(13);
        customSubtitle.setTextColor(s.onSurfaceVariant);
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        csLp.topMargin = s.dp(4);
        texts.addView(customSubtitle, csLp);
        customCard.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        customState = new TextView(host);
        customState.setTextSize(13);
        customState.setTypeface(Typeface.DEFAULT_BOLD);
        customCard.addView(customState);

        ImageView chevron = new ImageView(host);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(s.onSurfaceVariant, android.graphics.PorterDuff.Mode.SRC_IN);
        int chp = s.dp(4);
        chevron.setPadding(chp, chp, chp, chp);
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(s.dp(24), s.dp(24));
        chLp.leftMargin = s.dp(8);
        customCard.addView(chevron, chLp);

        customCard.setOnClickListener(v -> host.onCustomFontCardClicked());
        root.addView(customCard, cardLp());

        // ---- 字体恢复默认 ----
        LinearLayout restoreCard = card();
        LinearLayout restoreTexts = new LinearLayout(host);
        restoreTexts.setOrientation(LinearLayout.VERTICAL);

        TextView rt = new TextView(host);
        rt.setText(R.string.restore_default_title);
        rt.setTextSize(16);
        rt.setTypeface(Typeface.DEFAULT_BOLD);
        rt.setTextColor(s.bad);
        restoreTexts.addView(rt);

        TextView rsb = new TextView(host);
        rsb.setText(R.string.restore_default_subtitle);
        rsb.setTextSize(13);
        rsb.setTextColor(s.onSurfaceVariant);
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rbLp.topMargin = s.dp(4);
        restoreTexts.addView(rsb, rbLp);
        restoreCard.addView(restoreTexts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron2 = new ImageView(host);
        chevron2.setImageResource(R.drawable.ic_chevron_right);
        chevron2.setColorFilter(s.onSurfaceVariant, android.graphics.PorterDuff.Mode.SRC_IN);
        chevron2.setPadding(chp, chp, chp, chp);
        restoreCard.addView(chevron2, new LinearLayout.LayoutParams(s.dp(24), s.dp(24)));
        restoreCard.setOnClickListener(v -> host.showFontRestoreDialog());
        root.addView(restoreCard, cardLp());

        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refresh();
        return scroll;
    }

    private LinearLayout header(int titleRes) {
        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.bottomMargin = s.dp(14);
        header.setLayoutParams(hLp);

        TextView title = new TextView(host);
        title.setText(titleRes);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(s.onSurface);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.ImageButton restart = new android.widget.ImageButton(host);
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
        return header;
    }

    private LinearLayout card() {        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(s.round(s.surfaceContainer, 16f));
        int cp = s.dp(16);
        card.setPadding(cp, cp, cp, cp);
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = s.dp(12);
        return lp;
    }

    /** 刷新「指定字体」两行文字与生效状态。 */
    void refresh() {
        if (customSubtitle == null) {
            return;
        }
        SharedPreferences sp = host.getSharedPreferences(
                Prefs.PREFS_PUBLIC, android.content.Context.MODE_PRIVATE);
        String name = sp.getString(Prefs.KEY_CUSTOM_FONT_NAME, "");
        String path = sp.getString(Prefs.KEY_CUSTOM_FONT_PATH, "");
        boolean picked = path != null && path.length() > 0;

        customSubtitle.setText(picked
                ? (name == null || name.length() == 0
                        ? host.getString(R.string.custom_font_subtitle_picked)
                        : name)
                : host.getString(R.string.custom_font_subtitle));

        if (!picked) {
            customState.setText("");
            return;
        }
        long changed = sp.getLong(Prefs.KEY_CUSTOM_FONT_CHANGED, 0L);
        long stopped = sp.getLong(Prefs.KEY_FONT_STOPPED, 0L);
        boolean active = stopped >= changed;
        customState.setText(active ? R.string.font_state_active : R.string.font_state_pending);
        customState.setTextColor(active ? s.ok : s.warn);
    }
}

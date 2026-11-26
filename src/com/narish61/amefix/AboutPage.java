package com.narish61.amefix;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 关于页：版本与作者、项目地址、检查更新、恢复到初始状态。 */
final class AboutPage {

    private final MainActivity host;
    private final Skin s;
    private TextView updateState;

    AboutPage(MainActivity host, Skin skin) {
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

        root.addView(header());

        // 版本与作者
        LinearLayout infoCard = card();
        TextView info = new TextView(host);
        info.setText(host.getString(R.string.about_info,
                host.moduleVersionName(), host.moduleVersionCode()));
        info.setTextSize(14);
        info.setLineSpacing(s.dp(2), 1f);
        info.setTextColor(s.onSurface);
        infoCard.addView(info, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(infoCard, cardLp());

        // 项目地址
        LinearLayout projectCard = card();
        projectCard.addView(label(R.string.about_project, s.onSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        projectCard.addView(chevron());
        projectCard.setOnClickListener(v -> host.openUrl(MainActivity.PROJECT_URL));
        root.addView(projectCard, cardLp());

        // 检查更新
        LinearLayout updateCard = card();
        updateCard.addView(label(R.string.about_check_update, s.onSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        updateState = new TextView(host);
        updateState.setTextSize(13);
        updateState.setTypeface(Typeface.DEFAULT_BOLD);
        updateState.setVisibility(View.GONE);
        LinearLayout.LayoutParams usLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        usLp.gravity = Gravity.CENTER_VERTICAL;
        usLp.rightMargin = s.dp(10);
        updateCard.addView(updateState, usLp);
        updateCard.addView(chevron());
        updateCard.setOnClickListener(v -> host.checkUpdate(updateState));
        root.addView(updateCard, cardLp());

        // 恢复到初始状态（危险操作）
        LinearLayout resetCard = card();
        LinearLayout texts = new LinearLayout(host);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView rt = new TextView(host);
        rt.setText(R.string.module_reset_title);
        rt.setTextSize(16);
        rt.setTypeface(Typeface.DEFAULT_BOLD);
        rt.setTextColor(s.bad);
        texts.addView(rt);
        TextView rsb = new TextView(host);
        rsb.setText(R.string.module_reset_subtitle);
        rsb.setTextSize(13);
        rsb.setTextColor(s.onSurfaceVariant);
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rbLp.topMargin = s.dp(4);
        texts.addView(rsb, rbLp);
        resetCard.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        resetCard.addView(chevron());
        resetCard.setOnClickListener(v -> host.showModuleResetDialog());
        root.addView(resetCard, cardLp());

        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout header() {
        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.bottomMargin = s.dp(14);
        header.setLayoutParams(hLp);

        TextView title = new TextView(host);
        title.setText(R.string.tab_about);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(s.onSurface);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(host);
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

    private TextView label(int res, int color) {
        TextView t = new TextView(host);
        t.setText(res);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(color);
        return t;
    }

    private ImageView chevron() {
        ImageView v = new ImageView(host);
        v.setImageResource(R.drawable.ic_chevron_right);
        v.setColorFilter(s.onSurfaceVariant, android.graphics.PorterDuff.Mode.SRC_IN);
        int chp = s.dp(4);
        v.setPadding(chp, chp, chp, chp);
        return v;
    }
}

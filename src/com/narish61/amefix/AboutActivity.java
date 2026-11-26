package com.narish61.amefix;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AboutActivity extends Activity {

    private static final String PROJECT_URL = "https://github.com/Narish611/apple-music-fix";
    private static final String UPDATE_URL =
            "https://raw.githubusercontent.com/Narish611/apple-music-fix/main/update.json";

    private boolean dark;
    private int cSurface;
    private int cSurfaceContainer;
    private int cOnSurface;
    private int cOnSurfaceVariant;
    private int colorOk;
    private int colorBad;

    private TextView updateState;

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

        // Header: back + title
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
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });
        int bs = (int) (44 * d);
        header.addView(back, new LinearLayout.LayoutParams(bs, bs));

        TextView title = new TextView(this);
        title.setText(R.string.about_title);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(cOnSurface);
        title.setPadding((int) (12 * d), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        // Card: version info
        LinearLayout infoCard = newCard(d);
        TextView info = new TextView(this);
        info.setText(getString(R.string.about_info, currentVersionName(), currentVersionCode()));
        info.setTextSize(14);
        info.setTextColor(cOnSurface);
        infoCard.addView(info, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(infoCard, cardLp(d));

        // Card: project page (title + arrow)
        LinearLayout projectCard = newCard(d);
        TextView projectTitle = new TextView(this);
        projectTitle.setText(R.string.about_project);
        projectTitle.setTextSize(16);
        projectTitle.setTypeface(Typeface.DEFAULT_BOLD);
        projectTitle.setTextColor(cOnSurface);
        projectCard.addView(projectTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        projectCard.addView(chevron(d), chevronLp(d));
        projectCard.setOnClickListener(v -> openUrl(PROJECT_URL));
        root.addView(projectCard, cardLp(d));

        // Card: check for updates (title + status left of arrow + arrow)
        LinearLayout updateCard = newCard(d);
        TextView updateTitle = new TextView(this);
        updateTitle.setText(R.string.about_check_update);
        updateTitle.setTextSize(16);
        updateTitle.setTypeface(Typeface.DEFAULT_BOLD);
        updateTitle.setTextColor(cOnSurface);
        updateCard.addView(updateTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        updateState = new TextView(this);
        updateState.setTextSize(13);
        updateState.setTypeface(Typeface.DEFAULT_BOLD);
        updateState.setVisibility(View.GONE);
        LinearLayout.LayoutParams usLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        usLp.gravity = Gravity.CENTER_VERTICAL;
        usLp.rightMargin = (int) (12 * d);
        updateCard.addView(updateState, usLp);

        updateCard.addView(chevron(d), chevronLp(d));
        updateCard.setOnClickListener(v -> checkUpdate());
        root.addView(updateCard, cardLp(d));

        setContentView(root);
    }

    private void checkUpdate() {
        showUpdateState(getString(R.string.checking), cOnSurfaceVariant);
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
                    showUpdateState(getString(R.string.check_failed), colorBad);
                    return;
                }
                if (fCode > currentVersionCode()) {
                    showUpdateState(getString(R.string.update_available), colorOk);
                    dialogBuilder()
                            .setTitle(R.string.update_available)
                            .setMessage(getString(R.string.update_available_msg, fName))
                            .setPositiveButton(R.string.update_go, (d, w) ->
                                    openUrl(fUrl.isEmpty() ? PROJECT_URL + "/releases/latest" : fUrl))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else {
                    showUpdateState(getString(R.string.up_to_date), colorOk);
                }
            });
        }).start();
    }

    private void showUpdateState(String text, int color) {
        if (updateState == null) {
            return;
        }
        updateState.setText(text);
        updateState.setTextColor(color);
        updateState.setVisibility(View.VISIBLE);
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

    private AlertDialog.Builder dialogBuilder() {
        return dark
                ? new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                : new AlertDialog.Builder(this);
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

    private ImageView chevron(float d) {
        ImageView v = new ImageView(this);
        v.setImageResource(R.drawable.ic_chevron_right);
        v.setColorFilter(cOnSurfaceVariant, PorterDuff.Mode.SRC_IN);
        int p = (int) (4 * d);
        v.setPadding(p, p, p, p);
        return v;
    }

    private LinearLayout.LayoutParams chevronLp(float d) {
        return new LinearLayout.LayoutParams((int) (24 * d), (int) (24 * d));
    }

    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(color);
        gd.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        return gd;
    }
}

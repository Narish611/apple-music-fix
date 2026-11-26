package com.narish61.amefix;

import android.app.Activity;
import android.app.WallpaperManager;
import android.app.WallpaperColors;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;

/**
 * 三个页面共用的配色与尺寸计算。
 *
 * <p>取色来源用 {@link WallpaperManager#getWallpaperColors(int)}：这是系统 Monet 取色的结果，
 * 各 ROM（含 HyperOS）都实现了；比直接读 android:color/system_accent1_* 那套框架资源可靠 ——
 * 实测部分 ROM 把 system_accent1_40 直接删了、system_neutral1_10 改成近白色，
 * 照着读会出现深色模式整屏发白的问题。拿不到壁纸色时退回固定的 Material 基准色板。
 */
final class Skin {

    final boolean dark;
    /** 是否用到了壁纸取色。 */
    final boolean monet;
    final int primary;
    final int onPrimary;
    final int primaryContainer;
    final int onPrimaryContainer;
    final int secondaryContainer;
    final int onSecondaryContainer;
    final int surface;
    final int surfaceContainer;
    final int onSurface;
    final int onSurfaceVariant;
    final int outline;
    final int ok;
    final int bad;
    final int warn;
    final float density;

    Skin(Activity a) {
        int night = a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        dark = night == Configuration.UI_MODE_NIGHT_YES;
        density = a.getResources().getDisplayMetrics().density;

        int seed = wallpaperSeed(a);
        monet = seed != 0;

        if (monet) {
            float[] hsv = new float[3];
            Color.colorToHSV(seed, hsv);
            float h = hsv[0];
            float cs = clamp(hsv[1], 0.30f, 0.80f);
            if (dark) {
                primary = hsv(h, cs * 0.85f, 0.84f);
                onPrimary = hsv(h, cs * 0.80f, 0.16f);
                primaryContainer = hsv(h, cs * 0.90f, 0.38f);
                onPrimaryContainer = hsv(h, cs * 0.45f, 0.93f);
                secondaryContainer = hsv(h + 20f, cs * 0.35f, 0.26f);
                onSecondaryContainer = hsv(h, cs * 0.30f, 0.92f);
                surface = hsv(h, 0.030f, 0.078f);
                surfaceContainer = hsv(h, 0.035f, 0.145f);
                onSurface = hsv(h, 0.020f, 0.93f);
                onSurfaceVariant = hsv(h, 0.030f, 0.80f);
                outline = hsv(h, 0.045f, 0.56f);
            } else {
                primary = hsv(h, cs, 0.42f);
                onPrimary = 0xFFFFFFFF;
                primaryContainer = hsv(h, cs * 0.80f, 0.90f);
                onPrimaryContainer = hsv(h, cs * 0.70f, 0.18f);
                secondaryContainer = hsv(h + 20f, cs * 0.35f, 0.92f);
                onSecondaryContainer = hsv(h, cs * 0.55f, 0.19f);
                surface = hsv(h, 0.012f, 0.995f);
                surfaceContainer = hsv(h, 0.025f, 0.945f);
                onSurface = hsv(h, 0.020f, 0.12f);
                onSurfaceVariant = hsv(h, 0.030f, 0.34f);
                outline = hsv(h, 0.040f, 0.50f);
            }
            ok = dark ? hsv(140f, 0.35f, 0.78f) : hsv(140f, 0.55f, 0.38f);
            bad = dark ? hsv(4f, 0.55f, 0.88f) : hsv(4f, 0.70f, 0.48f);
            warn = dark ? hsv(36f, 0.60f, 0.88f) : hsv(36f, 0.80f, 0.45f);
        } else if (dark) {
            primary = 0xFFD0BCFF;
            onPrimary = 0xFF381E72;
            primaryContainer = 0xFF4F378B;
            onPrimaryContainer = 0xFFEADDFF;
            secondaryContainer = 0xFF4A4458;
            onSecondaryContainer = 0xFFE8DEF8;
            surface = 0xFF1C1B1F;
            surfaceContainer = 0xFF2B2930;
            onSurface = 0xFFE6E1E5;
            onSurfaceVariant = 0xFFCAC4D0;
            outline = 0xFF938F99;
            ok = 0xFF7DCB96;
            bad = 0xFFFFB4AB;
            warn = 0xFFFFB74D;
        } else {
            primary = 0xFF6750A4;
            onPrimary = 0xFFFFFFFF;
            primaryContainer = 0xFFEADDFF;
            onPrimaryContainer = 0xFF21005D;
            secondaryContainer = 0xFFE8DEF8;
            onSecondaryContainer = 0xFF1E192B;
            surface = 0xFFFFFBFE;
            surfaceContainer = 0xFFF3EDF7;
            onSurface = 0xFF1D1B20;
            onSurfaceVariant = 0xFF49454F;
            outline = 0xFF79747E;
            ok = 0xFF2E7D32;
            bad = 0xFFB3261E;
            warn = 0xFF9A6700;
        }

        android.util.Log.i("AMFix", "skin dark=" + dark + " monet=" + monet
                + " seed=" + Integer.toHexString(seed)
                + " surface=" + Integer.toHexString(surface)
                + " onSurface=" + Integer.toHexString(onSurface)
                + " primary=" + Integer.toHexString(primary));
    }

    /** 系统算好的壁纸主色；取不到返回 0。 */
    private static int wallpaperSeed(Activity a) {
        if (Build.VERSION.SDK_INT < 27) {
            return 0;
        }
        try {
            WallpaperManager wm = WallpaperManager.getInstance(a);
            if (wm == null) {
                return 0;
            }
            WallpaperColors wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc == null || wc.getPrimaryColor() == null) {
                return 0;
            }
            int c = wc.getPrimaryColor().toArgb();
            float[] hsv = new float[3];
            Color.colorToHSV(c, hsv);
            // 近灰/近黑的壁纸主色不适合当强调色，直接用基准色板。
            return hsv[1] < 0.06f ? 0 : c;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int hsv(float h, float s, float v) {
        float hue = ((h % 360f) + 360f) % 360f;
        return Color.HSVToColor(new float[]{hue, clamp(s, 0f, 1f), clamp(v, 0f, 1f)});
    }

    /** 给颜色加透明度，用于分割线这类弱化元素。 */
    static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (Math.round(255 * a) << 24);
    }

    int dp(float v) {
        return Math.round(v * density);
    }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(color);
        gd.setCornerRadius(radiusDp * density);
        return gd;
    }

    GradientDrawable dot(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        return gd;
    }
}

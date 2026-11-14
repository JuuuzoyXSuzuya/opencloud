package dev.opencloud.region;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Runtime repaint of TikTok's brand pink.
 *
 * <p>The build rewrites the pink wherever the bytecode spells it out as a
 * constant, and the colour readers/writers below, to land here.  A colour is
 * moved only when it sits in the pink family (hue near {@code #FE2C55}, both
 * saturation and value high); everything else — the cyan, a photo, a white
 * avatar ring — is left exactly as it was.  The move is the same hue,
 * saturation and value step that takes the brand pink to the chosen accent,
 * and alpha is preserved.</p>
 */
public final class Accent {
    private static final int PINK = 0xFFFE2C55;

    private Accent() {
    }

    /** The pink itself, as a constant in the bytecode. */
    public static int accent() {
        return enabled() ? shift(PINK) : PINK;
    }

    // ------------------------------------------------------------ read paths

    public static int color(Resources resources, int id) {
        return shift(resources.getColor(id));
    }

    public static int colorThemed(Resources resources, int id, Resources.Theme theme) {
        return shift(resources.getColor(id, theme));
    }

    public static int colorContext(Context context, int id) {
        return shift(context.getColor(id));
    }

    // ----------------------------------------------------------- apply paths

    public static void textColor(TextView view, int color) {
        view.setTextColor(shift(color));
    }

    public static void background(View view, int color) {
        view.setBackgroundColor(shift(color));
    }

    public static void statusBar(Window window, int color) {
        window.setStatusBarColor(shift(color));
    }

    public static void navigationBar(Window window, int color) {
        window.setNavigationBarColor(shift(color));
    }

    public static void shapeColor(GradientDrawable shape, int color) {
        shape.setColor(shift(color));
    }

    public static void paintColor(Paint paint, int color) {
        paint.setColor(shift(color));
    }

    public static void tint(Drawable drawable, int color) {
        drawable.setTint(shift(color));
    }

    public static void colorFilter(ImageView view, int color) {
        view.setColorFilter(shift(color));
    }

    public static ColorStateList valueOf(int color) {
        return ColorStateList.valueOf(shift(color));
    }

    // ---------------------------------------------------------------- core

    private static boolean enabled() {
        return Plugins.isEnabled(App.context(), Plugins.TIKTOK_ACCENT);
    }

    /** Moves a pink-family colour onto the accent; other colours pass through. */
    static int shift(int color) {
        if ((color >>> 24) == 0) {
            return color; // fully transparent: nothing to repaint
        }
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (!pinkish(hsv)) {
            return color;
        }
        float[] target = new float[3];
        Color.colorToHSV(targetColor(), target);
        float[] pink = new float[3];
        Color.colorToHSV(PINK, pink);
        hsv[0] = wrap(hsv[0] + target[0] - pink[0]);
        hsv[1] = clamp(hsv[1] + target[1] - pink[1]);
        hsv[2] = clamp(hsv[2] + target[2] - pink[2]);
        int repainted = Color.HSVToColor(hsv);
        return Color.argb(Color.alpha(color),
                Color.red(repainted), Color.green(repainted), Color.blue(repainted));
    }

    private static boolean pinkish(float[] hsv) {
        float hue = hsv[0];
        boolean inWindow = hue < 20f || hue >= 330f;
        return inWindow && hsv[1] >= 0.45f && hsv[2] >= 0.45f;
    }

    private static int targetColor() {
        return Palette.tiktokAccent(App.context());
    }

    private static float wrap(float hue) {
        float value = hue % 360f;
        return value < 0f ? value + 360f : value;
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }
}

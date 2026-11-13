package dev.opencloud.region;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Material 3 Expressive building blocks, drawn with framework views only.
 *
 * <p>The mod cannot depend on androidx: it is compiled with plain javac and
 * dropped into TikTok's dex list.  Everything here is therefore a small,
 * deliberate re-implementation of the expressive language: the wide shape
 * scale, the tonal containers, the display/title/body type ramp, press
 * feedback with an overshoot and dialog surfaces that are not the platform
 * default.</p>
 */
final class Ui {
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    final Context context;
    final boolean dark;

    final int bg;
    final int surfaceLow;
    final int surfaceHigh;
    final int surfaceHighest;
    final int onBg;
    final int muted;
    final int outline;
    final int outlineVariant;
    final int primary;
    final int onPrimary;
    final int primaryContainer;
    final int onPrimaryContainer;
    final int secondaryContainer;
    final int onSecondaryContainer;
    final int tertiary;
    final int onTertiary;
    final int tertiaryContainer;
    final int onTertiaryContainer;
    final int error;
    final int errorContainer;
    final int onErrorContainer;

    Ui(Context context, Palette palette, boolean dark) {
        this.context = context;
        this.dark = dark;
        this.bg = palette.surface;
        this.surfaceLow = palette.surfaceContainerLow;
        this.surfaceHigh = palette.surfaceContainerHigh;
        this.surfaceHighest = palette.surfaceContainerHighest;
        this.onBg = palette.onSurface;
        this.muted = palette.onSurfaceVariant;
        this.outline = palette.outline;
        this.outlineVariant = palette.outlineVariant;
        this.primary = palette.primary;
        this.onPrimary = palette.onPrimary;
        this.primaryContainer = palette.primaryContainer;
        this.onPrimaryContainer = palette.onPrimaryContainer;
        this.secondaryContainer = palette.secondaryContainer;
        this.onSecondaryContainer = palette.onSecondaryContainer;
        this.tertiary = palette.tertiary;
        this.onTertiary = palette.onTertiary;
        this.tertiaryContainer = palette.tertiaryContainer;
        this.onTertiaryContainer = palette.onTertiaryContainer;
        this.error = palette.error;
        this.errorContainer = palette.errorContainer;
        this.onErrorContainer = palette.onErrorContainer;
    }

    int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------- shapes

    GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    GradientDrawable outlined(int fill, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = rounded(fill, radiusDp);
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), strokeColor);
        }
        return drawable;
    }

    RippleDrawable ripple(int fill, int radiusDp, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                rounded(fill, radiusDp), rounded(0xFFFFFFFF, radiusDp));
    }

    RippleDrawable rippleOutlined(int radiusDp, int strokeDp, int strokeColor, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                outlined(0x00000000, radiusDp, strokeDp, strokeColor),
                rounded(0xFFFFFFFF, radiusDp));
    }

    // ------------------------------------------------------------- motion

    /** Expressive press feedback: shrink fast, spring back slowly. */
    void press(final View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View touched, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    touched.animate().cancel();
                    touched.animate().scaleX(0.96f).scaleY(0.96f)
                            .setDuration(120)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    touched.animate().cancel();
                    touched.animate().scaleX(1f).scaleY(1f)
                            .setDuration(280)
                            .setInterpolator(new OvershootInterpolator(1.8f))
                            .start();
                }
                return false;
            }
        });
    }

    /** Fade and lift the content in when a page is created. */
    void appear(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(18));
        view.animate().alpha(1f).translationY(0f)
                .setDuration(320)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    // ---------------------------------------------------------------- type

    private TextView styled(String value, float size, Typeface face, int color, float tracking) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTypeface(face);
        view.setTextColor(color);
        view.setLetterSpacing(tracking);
        return view;
    }

    TextView display(String value) {
        return styled(value, 32, BOLD, onBg, -0.02f);
    }

    TextView headline(String value) {
        return styled(value, 26, BOLD, onBg, -0.01f);
    }

    TextView title(String value) {
        return styled(value, 20, MEDIUM, onBg, -0.005f);
    }

    TextView item(String value) {
        return styled(value, 17, MEDIUM, onBg, 0f);
    }

    TextView body(String value) {
        return styled(value, 14, REGULAR, muted, 0.005f);
    }

    TextView caption(String value) {
        return styled(value, 12, REGULAR, muted, 0.01f);
    }

    TextView label(String value) {
        TextView view = styled(value, 11, MEDIUM, primary, 0.10f);
        view.setAllCaps(true);
        return view;
    }

    TextView symbol(String value, float size, int color) {
        TextView view = styled(value, size, REGULAR, color, 0f);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    /** Small rounded label, used for the build facts and plugin metadata. */
    TextView chip(String value, int fill, int ink) {
        TextView view = styled(value, 11, MEDIUM, ink, 0.06f);
        view.setGravity(Gravity.CENTER);
        view.setAllCaps(true);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setBackground(rounded(fill, 12));
        return view;
    }

    /** Round monogram that leads a category card. */
    TextView disc(String letter, int fill, int ink) {
        TextView view = styled(letter, 18, BOLD, ink, 0f);
        view.setGravity(Gravity.CENTER);
        int size = dp(48);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        view.setBackground(rounded(fill, 24));
        return view;
    }

    // ------------------------------------------------------------ controls

    TextView filled(String label, View.OnClickListener listener) {
        return button(label, primary, onPrimary, 0, 0, listener);
    }

    TextView tonal(String label, View.OnClickListener listener) {
        return button(label, secondaryContainer, onSecondaryContainer, 0, 0, listener);
    }

    TextView outlined(String label, View.OnClickListener listener) {
        return button(label, 0x00000000, onBg, 1, outline, listener);
    }

    TextView danger(String label, View.OnClickListener listener) {
        return button(label, errorContainer, onErrorContainer, 0, 0, listener);
    }

    private TextView button(String label, int fill, int ink, int strokeDp, int strokeColor,
                            View.OnClickListener listener) {
        TextView view = styled(label, 15, MEDIUM, ink, 0.01f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(24), dp(15), dp(24), dp(15));
        view.setMinHeight(dp(52));
        if (strokeDp > 0) {
            view.setBackground(rippleOutlined(26, strokeDp, strokeColor, withAlpha(onBg, 0.10f)));
        } else {
            view.setBackground(ripple(fill, 26, withAlpha(ink, 0.16f)));
        }
        view.setClickable(true);
        view.setFocusable(true);
        press(view);
        if (listener != null) {
            view.setOnClickListener(listener);
        }
        return view;
    }

    /** Circular icon button, used for the page back affordance. */
    TextView icon(String glyph, View.OnClickListener listener) {
        TextView view = symbol(glyph, 20, onBg);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        view.setBackground(ripple(surfaceHigh, 24, withAlpha(onBg, 0.10f)));
        view.setClickable(true);
        view.setFocusable(true);
        press(view);
        if (listener != null) {
            view.setOnClickListener(listener);
        }
        return view;
    }

    /** The expressive switch, wired to the same listeners as a platform one. */
    Toggle toggle() {
        return new Toggle(context, primary, surfaceHighest, onPrimary, outline);
    }

    View divider() {
        View view = new View(context);
        view.setBackgroundColor(withAlpha(outlineVariant, 0.9f));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return view;
    }

    // ------------------------------------------------------------- dialogs

    /** Rounds the platform dialog and applies the expressive type ramp to it. */
    void styleDialog(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        try {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                // The platform background is an inset drawable; replacing it
                // outright would make the dialog touch the screen edges.
                window.setBackgroundDrawable(
                        new InsetDrawable(rounded(surfaceHigh, 28), dp(16)));
            }
        } catch (Throwable ignored) {
            // Keep the platform background if the window is not available.
        }
        try {
            int titleId = dialog.getContext().getResources()
                    .getIdentifier("alertTitle", "id", "android");
            TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
            if (title != null) {
                title.setTextSize(22);
                title.setTypeface(BOLD);
                title.setTextColor(onBg);
                title.setLetterSpacing(-0.01f);
            }
        } catch (Throwable ignored) {
            // The title is optional.
        }
        tintButton(dialog, AlertDialog.BUTTON_POSITIVE);
        tintButton(dialog, AlertDialog.BUTTON_NEGATIVE);
        tintButton(dialog, AlertDialog.BUTTON_NEUTRAL);
    }

    private void tintButton(AlertDialog dialog, int which) {
        try {
            Button button = dialog.getButton(which);
            if (button != null) {
                button.setAllCaps(false);
                button.setTextSize(15);
                button.setTypeface(MEDIUM);
                button.setTextColor(primary);
            }
        } catch (Throwable ignored) {
            // getButton() only works once the dialog is shown.
        }
    }

    // ------------------------------------------------------------- helpers

    static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(alpha * 255f) << 24);
    }

    /** Near-black or white, whichever keeps enough contrast on {@code background}. */
    static int contrastOn(int background) {
        double luminance = (0.2126 * android.graphics.Color.red(background)
                + 0.7152 * android.graphics.Color.green(background)
                + 0.0722 * android.graphics.Color.blue(background)) / 255.0;
        return luminance > 0.55 ? 0xFF1A1A1A : 0xFFFFFFFF;
    }
}
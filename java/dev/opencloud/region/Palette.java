package dev.opencloud.region;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;

/**
 * Material 3 Expressive colour roles.
 *
 * <p>On Android 12+ the neutral and accent roles come from the system tonal
 * palettes (android.R.color.system_*), which are generated from the wallpaper,
 * so the screen picks up the user's Material You colours.  Older devices, or a
 * failed lookup, fall back to the M3 baseline scheme.</p>
 *
 * <p>The expressive variants of the scheme (secondary/tertiary containers) are
 * derived from the accent: a hue-shifted tertiary and a softened secondary
 * container.  That keeps the scheme coherent for every accent the user can
 * pick without shipping ten hand-tuned palettes.</p>
 */
final class Palette {
    final int surface;
    final int surfaceContainerLowest;
    final int surfaceContainerLow;
    final int surfaceContainer;
    final int surfaceContainerHigh;
    final int surfaceContainerHighest;
    final int onSurface;
    final int onSurfaceVariant;
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
    final int onError;
    final int errorContainer;
    final int onErrorContainer;

    /** "system" keeps the Material You accent, the other keys override it. */
    private static final String SYSTEM = "system";

    private static final String[] ACCENT_KEYS = {
            SYSTEM, "violet", "blue", "green", "teal",
            "orange", "red", "pink", "yellow", "graphite",
    };

    private static final String[] ACCENT_LABELS = {
            "Системный (Material You)", "Фиолетовый", "Синий", "Зелёный", "Бирюзовый",
            "Оранжевый", "Красный", "Розовый", "Жёлтый", "Графит",
    };

    /** primary, onPrimary, primaryContainer, onPrimaryContainer (light). */
    private static final int[][] ACCENT_LIGHT = {
            {0, 0, 0, 0},
            {0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D},
            {0xFF0B57D0, 0xFFFFFFFF, 0xFFD3E3FD, 0xFF041E49},
            {0xFF2E6B3A, 0xFFFFFFFF, 0xFFC8F0CE, 0xFF0A2E14},
            {0xFF00696D, 0xFFFFFFFF, 0xFF9CF1F5, 0xFF002021},
            {0xFF8B5000, 0xFFFFFFFF, 0xFFFFDCBE, 0xFF2D1600},
            {0xFFB3261E, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410002},
            {0xFF984061, 0xFFFFFFFF, 0xFFFFD9E2, 0xFF3E001D},
            {0xFF6D5E00, 0xFFFFFFFF, 0xFFF9E287, 0xFF211B00},
            {0xFF5D5D5D, 0xFFFFFFFF, 0xFFE3E3E3, 0xFF1B1B1B},
    };

    /** primary, onPrimary, primaryContainer, onPrimaryContainer (dark). */
    private static final int[][] ACCENT_DARK = {
            {0, 0, 0, 0},
            {0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF},
            {0xFFA8C7FA, 0xFF062E6F, 0xFF0842A0, 0xFFD3E3FD},
            {0xFFA6D89A, 0xFF0D3318, 0xFF1E4D2B, 0xFFC8F0CE},
            {0xFF80D4D8, 0xFF003739, 0xFF004F52, 0xFF9CF1F5},
            {0xFFFFB86B, 0xFF482300, 0xFF673C00, 0xFFFFDCBE},
            {0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6},
            {0xFFFFB0C8, 0xFF560027, 0xFF7B2D4E, 0xFFFFD9E2},
            {0xFFE8C34B, 0xFF3A3100, 0xFF544A00, 0xFFF9E287},
            {0xFFC6C6C6, 0xFF303030, 0xFF474747, 0xFFE2E2E2},
    };

    private static final int LIGHT_ERROR = 0xFFB3261E;
    private static final int LIGHT_ON_ERROR = 0xFFFFFFFF;
    private static final int LIGHT_ERROR_CONTAINER = 0xFFF9DEDC;
    private static final int LIGHT_ON_ERROR_CONTAINER = 0xFF410002;
    private static final int DARK_ERROR = 0xFFF2B8B5;
    private static final int DARK_ON_ERROR = 0xFF601410;
    private static final int DARK_ERROR_CONTAINER = 0xFF8C1D18;
    private static final int DARK_ON_ERROR_CONTAINER = 0xFFF9DEDC;

    private Palette(int surface, int onSurface, int onSurfaceVariant,
                    int surfaceContainerHigh, int primary, int onPrimary,
                    int primaryContainer, int onPrimaryContainer, int outline,
                    int error, int onError, int errorContainer, int onErrorContainer) {
        this.surface = surface;
        this.onSurface = onSurface;
        this.onSurfaceVariant = onSurfaceVariant;
        this.surfaceContainerHigh = surfaceContainerHigh;
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.primaryContainer = primaryContainer;
        this.onPrimaryContainer = onPrimaryContainer;
        this.outline = outline;
        this.error = error;
        this.onError = onError;
        this.errorContainer = errorContainer;
        this.onErrorContainer = onErrorContainer;

        // Expressive tonal ladder: the containers step away from the surface at
        // fixed rates, so every palette (baseline, dynamic or accent) gets the
        // same sense of depth.
        this.surfaceContainerLowest = mix(surface, surfaceContainerHigh, 0.12f);
        this.surfaceContainerLow = mix(surface, surfaceContainerHigh, 0.35f);
        this.surfaceContainer = mix(surface, surfaceContainerHigh, 0.65f);
        this.surfaceContainerHighest = mix(surfaceContainerHigh, onSurface, 0.05f);
        this.outlineVariant = mix(outline, surface, 0.55f);

        // Expressive secondary: a softer, lower-contrast companion to primary.
        int secondary = mix(primaryContainer, surface, 0.55f);
        this.secondaryContainer = secondary;
        this.onSecondaryContainer = readableOn(secondary);

        // Expressive tertiary: the accent rotated to the next hue family.
        int shifted = shiftHue(primary);
        this.tertiary = shifted;
        this.onTertiary = readableOn(shifted);
        int tertiaryContainer = mix(shifted, surface, 0.78f);
        this.tertiaryContainer = tertiaryContainer;
        this.onTertiaryContainer = readableOn(tertiaryContainer);
    }

    static Palette of(Context context, boolean dark) {
        Palette base = base(context, dark);
        int index = indexOf(selected(context));
        if (index <= 0) {
            return base;
        }
        int[] row = (dark ? ACCENT_DARK : ACCENT_LIGHT)[index];
        return base.withAccent(row[0], row[1], row[2], row[3]);
    }

    /** Accent keys in display order, "system" first. */
    static String[] accents() {
        return ACCENT_KEYS.clone();
    }

    static String label(String key) {
        int index = indexOf(key);
        return ACCENT_LABELS[index < 0 ? 0 : index];
    }

    /** The stored accent key, or "system" when nothing (or garbage) is stored. */
    static String selected(Context context) {
        String key = Prefs.open(context).getString(Prefs.ACCENT, SYSTEM);
        return indexOf(key) < 0 ? SYSTEM : key;
    }

    static void select(Context context, String key) {
        Prefs.open(context).edit()
                .putString(Prefs.ACCENT, indexOf(key) < 0 ? SYSTEM : key)
                .apply();
    }

    /** Raw colour the TikTok repaint targets; "system" falls back to mint. */
    static int tiktokAccent(Context context) {
        int index = indexOf(selected(context));
        if (index <= 0) {
            return 0xFF8DD1B0;
        }
        return ACCENT_LIGHT[index][0];
    }

    /** Colour of the swatch for {@code key}, independent of the stored choice. */
    static int accentPreview(Context context, String key, boolean dark) {
        int index = indexOf(key);
        if (index <= 0) {
            return base(context, dark).primary;
        }
        return (dark ? ACCENT_DARK : ACCENT_LIGHT)[index][0];
    }

    private Palette withAccent(int primary, int onPrimary, int container, int onContainer) {
        return new Palette(surface, onSurface, onSurfaceVariant, surfaceContainerHigh,
                primary, onPrimary, container, onContainer, outline,
                error, onError, errorContainer, onErrorContainer);
    }

    private static int indexOf(String key) {
        if (key == null) {
            return 0;
        }
        for (int i = 0; i < ACCENT_KEYS.length; i++) {
            if (ACCENT_KEYS[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static Palette base(Context context, boolean dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return dynamic(context, dark);
            } catch (Throwable ignored) {
                // System palette resources are missing on some OEM builds.
            }
        }
        return baseline(dark);
    }

    private static Palette dynamic(Context context, boolean dark) {
        if (dark) {
            return new Palette(
                    context.getColor(android.R.color.system_neutral1_900),
                    context.getColor(android.R.color.system_neutral1_50),
                    context.getColor(android.R.color.system_neutral2_200),
                    context.getColor(android.R.color.system_neutral1_700),
                    context.getColor(android.R.color.system_accent1_200),
                    context.getColor(android.R.color.system_accent1_900),
                    context.getColor(android.R.color.system_accent1_700),
                    context.getColor(android.R.color.system_accent1_100),
                    context.getColor(android.R.color.system_neutral2_400),
                    DARK_ERROR, DARK_ON_ERROR, DARK_ERROR_CONTAINER, DARK_ON_ERROR_CONTAINER);
        }
        return new Palette(
                context.getColor(android.R.color.system_neutral1_50),
                context.getColor(android.R.color.system_neutral1_900),
                context.getColor(android.R.color.system_neutral2_700),
                context.getColor(android.R.color.system_neutral1_200),
                context.getColor(android.R.color.system_accent1_600),
                context.getColor(android.R.color.system_neutral1_50),
                context.getColor(android.R.color.system_accent1_100),
                context.getColor(android.R.color.system_accent1_900),
                context.getColor(android.R.color.system_neutral2_500),
                LIGHT_ERROR, LIGHT_ON_ERROR, LIGHT_ERROR_CONTAINER, LIGHT_ON_ERROR_CONTAINER);
    }

    private static Palette baseline(boolean dark) {
        if (dark) {
            return new Palette(
                    0xFF141218, 0xFFE6E0E9, 0xFFCAC4D0,
                    0xFF2B2930,
                    0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF,
                    0xFF938F99,
                    DARK_ERROR, DARK_ON_ERROR, DARK_ERROR_CONTAINER, DARK_ON_ERROR_CONTAINER);
        }
        return new Palette(
                0xFFFEF7FF, 0xFF1D1B20, 0xFF49454F,
                0xFFECE6F0,
                0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D,
                0xFF79747E,
                LIGHT_ERROR, LIGHT_ON_ERROR, LIGHT_ERROR_CONTAINER, LIGHT_ON_ERROR_CONTAINER);
    }

    // ------------------------------------------------------------- helpers

    /** Mixes two opaque colours; {@code t} is the weight of {@code b}. */
    private static int mix(int a, int b, float t) {
        int red = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int green = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int blue = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(red, green, blue);
    }

    /** Rotates the accent into the next hue family and lifts its saturation. */
    private static int shiftHue(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + 55f) % 360f;
        hsv[1] = Math.min(1f, hsv[1] * 1.35f + 0.08f);
        hsv[2] = Math.min(1f, hsv[2] + 0.06f);
        return Color.HSVToColor(hsv);
    }

    /** Near-black or white, whichever keeps enough contrast on {@code background}. */
    private static int readableOn(int background) {
        double luminance = (0.2126 * Color.red(background)
                + 0.7152 * Color.green(background)
                + 0.0722 * Color.blue(background)) / 255.0;
        return luminance > 0.55 ? 0xFF1A1A1A : 0xFFFFFFFF;
    }
}
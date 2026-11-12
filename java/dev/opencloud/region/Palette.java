package dev.opencloud.region;

import android.content.Context;
import android.os.Build;

/**
 * Material 3 colour roles.
 *
 * On Android 12+ the values come from the system tonal palettes
 * (android.R.color.system_*), which are generated from the wallpaper — so the
 * screen picks up the user's Material You colours.  Older devices, or a
 * failed lookup, fall back to the M3 baseline scheme.
 *
 * The user can pick an accent colour for the mod's own screens.  That choice
 * only replaces the four accent roles; the neutral surface roles stay as they
 * are, so the result still follows the light/dark setting.
 */
final class Palette {
    final int surface;
    final int onSurface;
    final int onSurfaceVariant;
    final int surfaceContainer;
    final int surfaceContainerHigh;
    final int primary;
    final int onPrimary;
    final int primaryContainer;
    final int onPrimaryContainer;
    final int outline;

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

    private Palette(int surface, int onSurface, int onSurfaceVariant,
                    int surfaceContainer, int surfaceContainerHigh,
                    int primary, int onPrimary, int primaryContainer,
                    int onPrimaryContainer, int outline) {
        this.surface = surface;
        this.onSurface = onSurface;
        this.onSurfaceVariant = onSurfaceVariant;
        this.surfaceContainer = surfaceContainer;
        this.surfaceContainerHigh = surfaceContainerHigh;
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.primaryContainer = primaryContainer;
        this.onPrimaryContainer = onPrimaryContainer;
        this.outline = outline;
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

    /** Colour of the swatch for {@code key}, independent of the stored choice. */
    static int accentPreview(Context context, String key, boolean dark) {
        int index = indexOf(key);
        if (index <= 0) {
            return base(context, dark).primary;
        }
        return (dark ? ACCENT_DARK : ACCENT_LIGHT)[index][0];
    }

    private Palette withAccent(int primary, int onPrimary, int container, int onContainer) {
        return new Palette(surface, onSurface, onSurfaceVariant,
                surfaceContainer, surfaceContainerHigh,
                primary, onPrimary, container, onContainer, outline);
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
                    context.getColor(android.R.color.system_neutral1_800),
                    context.getColor(android.R.color.system_neutral1_700),
                    context.getColor(android.R.color.system_accent1_200),
                    context.getColor(android.R.color.system_accent1_900),
                    context.getColor(android.R.color.system_accent1_700),
                    context.getColor(android.R.color.system_accent1_100),
                    context.getColor(android.R.color.system_neutral2_400));
        }
        return new Palette(
                context.getColor(android.R.color.system_neutral1_50),
                context.getColor(android.R.color.system_neutral1_900),
                context.getColor(android.R.color.system_neutral2_700),
                context.getColor(android.R.color.system_neutral1_100),
                context.getColor(android.R.color.system_neutral1_200),
                context.getColor(android.R.color.system_accent1_600),
                context.getColor(android.R.color.system_neutral1_50),
                context.getColor(android.R.color.system_accent1_100),
                context.getColor(android.R.color.system_accent1_900),
                context.getColor(android.R.color.system_neutral2_500));
    }

    private static Palette baseline(boolean dark) {
        if (dark) {
            return new Palette(
                    0xFF141218, 0xFFE6E0E9, 0xFFCAC4D0,
                    0xFF211F26, 0xFF2B2930,
                    0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF,
                    0xFF938F99);
        }
        return new Palette(
                0xFFFEF7FF, 0xFF1D1B20, 0xFF49454F,
                0xFFF3EDF7, 0xFFECE6F0,
                0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D,
                0xFF79747E);
    }
}

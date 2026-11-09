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

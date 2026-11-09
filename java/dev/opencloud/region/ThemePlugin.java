package dev.opencloud.region;

import android.content.Context;

/**
 * Dark theme plugin.
 *
 * TikTok keeps the selected appearance in the private preference file
 * "sp_dark_mode", key "dark_mode" (0 = follow system, 1 = light, 2 = dark,
 * 3 = scheduled), and applies it through the static obfuscated method
 * X.005C.LJJIII(int) in the first dex.  The patcher wraps that method with
 * {@link #mode(int)}; when the plugin is on, every requested mode is replaced
 * by {@link #DARK}.  {@link #refresh()} re-applies the mode immediately after
 * the user flips the switch, without restarting TikTok.
 */
final class ThemePlugin {
    private static final String CONTROLLER = "X.005C";
    private static final String APPLY = "LJJIII";
    private static final String TIKTOK_PREFS = "sp_dark_mode";
    private static final String TIKTOK_KEY = "dark_mode";

    static final int FOLLOW_SYSTEM = 0;
    static final int LIGHT = 1;
    static final int DARK = 2;

    private ThemePlugin() {
    }

    /** Called from the patched X.005C.LJJIII(int) body. */
    static int mode(int requested) {
        Context context = App.context();
        if (context != null && Plugins.darkTheme(context)) {
            return DARK;
        }
        return requested;
    }

    /** Re-apply the theme after the switch changed. */
    static void refresh() {
        Context context = App.context();
        if (context == null) {
            return;
        }
        int mode = Plugins.darkTheme(context) ? DARK : storedMode(context);
        try {
            Class<?> owner = Class.forName(CONTROLLER);
            owner.getMethod(APPLY, int.class).invoke(null, mode);
        } catch (Throwable ignored) {
            // The next cold start still applies the plugin through mode().
        }
    }

    private static int storedMode(Context context) {
        try {
            return context.getSharedPreferences(TIKTOK_PREFS, Context.MODE_PRIVATE)
                    .getInt(TIKTOK_KEY, LIGHT);
        } catch (Throwable ignored) {
            return LIGHT;
        }
    }
}

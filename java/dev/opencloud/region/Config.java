package dev.opencloud.region;

import android.content.Context;
import android.content.SharedPreferences;

final class Config {
    private Config() {
    }

    /**
     * Returns the profile that hooks should answer with, or null when the mod
     * is disabled.  A missing application context is treated as the built-in
     * default rather than silently disabling the patch.
     */
    static Profile currentProfile() {
        Context context = App.context();
        if (context == null) {
            return Atlas.defaultProfile();
        }
        SharedPreferences prefs = Prefs.open(context);
        if (!prefs.getBoolean(Prefs.ENABLED, true)) {
            return null;
        }
        return resolve(prefs);
    }

    private static Profile resolve(SharedPreferences prefs) {
        String iso = prefs.getString(Prefs.COUNTRY, Atlas.DEFAULT_ISO);
        String numeric = prefs.getString(Prefs.OPERATOR, null);
        Profile chosen = Atlas.find(iso, numeric);
        return chosen == null ? Atlas.defaultProfile() : chosen;
    }

    static boolean isOn(Context ctx) {
        return Prefs.open(ctx).getBoolean(Prefs.ENABLED, true);
    }

    static void setEnabled(Context context, boolean on) {
        Prefs.open(context).edit().putBoolean(Prefs.ENABLED, on).apply();
    }

    static String selectedIso(Context context) {
        Profile profile = resolve(Prefs.open(context));
        return profile == null ? Atlas.DEFAULT_ISO : profile.code();
    }

    static String selectedOperator(Context context) {
        Profile profile = resolve(Prefs.open(context));
        return profile == null ? "" : profile.numeric();
    }

    static void store(Context context, boolean on, String iso, String numeric) {
        Profile chosen = Atlas.find(iso, numeric);
        if (chosen == null) {
            chosen = Atlas.defaultProfile();
        }
        Prefs.open(context).edit()
                .putBoolean(Prefs.ENABLED, on)
                .putString(Prefs.COUNTRY, chosen.code())
                .putString(Prefs.OPERATOR, chosen.numeric())
                .apply();
    }
}

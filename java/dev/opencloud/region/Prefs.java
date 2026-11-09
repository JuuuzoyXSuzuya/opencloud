package dev.opencloud.region;

import android.content.SharedPreferences;

final class Prefs {
    static final String FILE = "opencloud_region";
    static final String ENABLED = "enabled";
    static final String COUNTRY = "country";
    static final String OPERATOR = "operator";

    private Prefs() {
    }

    static SharedPreferences open(android.content.Context context) {
        return context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE);
    }
}

package dev.opencloud.region;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Registry of OpenCloud modules.
 *
 * A plugin is either runtime-switchable (its state is stored in OpenCloud's
 * preferences and consulted by the bridge) or built into the APK at patch time
 * (shown in the list for reference).  Adding a runtime plugin means:
 *
 *   1. add a descriptor below with runtime = true;
 *   2. store its id in {@link #isEnabled(Context, String)} consumers;
 *   3. gate the patched behaviour in Java or in a patcher method override.
 */
final class Plugins {
    static final String DARK_THEME = "dark_theme";
    static final String FONT = "custom_font";
    static final String NO_ADS = "no_ads";
    static final String NO_LIVE = "no_live";
    private static final String PREFIX = "plugin_";

    static final class Descriptor {
        final String id;
        final String title;
        final String description;
        final boolean runtime;
        final boolean defaultOn;

        Descriptor(String id, String title, String description,
                   boolean runtime, boolean defaultOn) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.runtime = runtime;
            this.defaultOn = defaultOn;
        }
    }

    private static final Descriptor[] ALL = {
            new Descriptor(
                    DARK_THEME,
                    "Тёмная тема",
                    "Всегда тёмное оформление TikTok, даже если в системе включена светлая тема.",
                    true, true),
            new Descriptor(
                    FONT,
                    "Свой шрифт",
                    "Заменяет шрифт TikTok выбранным системным или импортированным файлом.",
                    true, false),
            new Descriptor(
                    NO_ADS,
                    "Без рекламы",
                    "Вырезает рекламные видео из ленты. Включается сразу, "
                            + "следующая загрузка ленты будет уже без рекламы.",
                    true, false),
            new Descriptor(
                    NO_LIVE,
                    "Без эфиров",
                    "Убирает вкладку «Эфиры» в верхней панели и живые трансляции "
                            + "из ленты. После переключения перезапустите TikTok.",
                    true, false),
            new Descriptor(
                    "browser_google",
                    "Google через браузер",
                    "Вход Google открывается в браузере (AppAuth + Custom Tabs). Включено при сборке.",
                    false, true),
            new Descriptor(
                    "settings_entry",
                    "Пункт в настройках TikTok",
                    "Строка «Выбор региона» в настройках TikTok. Включено при сборке.",
                    false, true),
    };

    private Plugins() {
    }

    static Descriptor[] all() {
        Descriptor[] copy = new Descriptor[ALL.length];
        System.arraycopy(ALL, 0, copy, 0, ALL.length);
        return copy;
    }

    static boolean isEnabled(Context context, String id) {
        if (context == null) {
            return defaultFor(id);
        }
        return Prefs.open(context).getBoolean(PREFIX + id, defaultFor(id));
    }

    static void setEnabled(Context context, String id, boolean enabled) {
        SharedPreferences.Editor editor = Prefs.open(context).edit();
        editor.putBoolean(PREFIX + id, enabled);
        editor.apply();
    }

    static boolean darkTheme(Context context) {
        return isEnabled(context, DARK_THEME);
    }

    private static boolean defaultFor(String id) {
        for (Descriptor descriptor : ALL) {
            if (descriptor.id.equals(id)) {
                return descriptor.defaultOn;
            }
        }
        return false;
    }
}

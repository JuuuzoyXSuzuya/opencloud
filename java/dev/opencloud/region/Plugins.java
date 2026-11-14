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
    static final String NO_PHOTOS = "no_photos";
    static final String TAG_FILTER = "tag_filter";
    static final String NO_WATERMARK = "no_watermark";
    static final String UNMUTE = "unmute";
    static final String SEEKBAR = "seekbar";
    static final String HIGH_FPS = "high_fps";
    static final String TIKTOK_ACCENT = "tiktok_accent";
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
                    NO_PHOTOS,
                    "Без фото-постов",
                    "Убирает из ленты слайд-шоу из фотографий, оставляя видео. "
                            + "Действует на следующую загрузку списка.",
                    true, false),
            new Descriptor(
                    TAG_FILTER,
                    "Фильтр хэштегов",
                    "Прячет посты, в подписи которых есть хэштег из списка ниже. "
                            + "Совпадение целое: «кот» не скрывает «#котлеты».",
                    true, false),
            new Descriptor(
                    NO_WATERMARK,
                    "Скачивание без вотермарки",
                    "Сохраняет видео по чистой ссылке TikTok и снимает запрет "
                            + "на скачивание с поста и аккаунта. Перезапустите TikTok.",
                    true, true),
            new Descriptor(
                    UNMUTE,
                    "Размутить звук",
                    "Включает звук у видео, где TikTok заглушил его из-за авторских прав. "
                            + "Действует на следующую загрузку.",
                    true, false),
            new Descriptor(
                    SEEKBAR,
                    "Полоса перемотки",
                    "Показывает полосу перемотки на каждом видео, а не только там, "
                            + "где TikTok решил её нарисовать.",
                    true, false),
            new Descriptor(
                    HIGH_FPS,
                    "120 Гц",
                    "Просит систему рисовать ленту на максимальной частоте вместо 60 Гц. "
                            + "Действует на следующий запуск.",
                    true, false),
            new Descriptor(
                    TIKTOK_ACCENT,
                    "Перекрасить TikTok",
                    "Перекрашивает фирменный розовый TikTok в выбранный акцент. "
                            + "Следующее открытие экрана уже будет в новом цвете.",
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

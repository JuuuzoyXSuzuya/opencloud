package dev.opencloud.region;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The only screen OpenCloud adds.  It is built in code so the patcher never
 * has to add or rewrite a single resource in TikTok's resource table.
 *
 * The screen follows the same dark/light choice as the dark-theme plugin:
 * when the plugin is on the whole OpenCloud UI is dark, otherwise it uses the
 * system night mode.
 */
public final class SettingsActivity extends Activity {
    private static final int REQUEST_PLUGIN = 41;
    private static final int REQUEST_EXPORT = 42;
    private static final int REQUEST_FONT = 43;

    private static final String KEY_PAGE = "opencloud_page";
    private static final String PAGE_APPEARANCE = "appearance";
    private static final String PAGE_FEED = "feed";
    private static final String PAGE_REGION = "region";
    private static final String PAGE_PLUGINS = "plugins";
    private static final String PAGE_SERVICE = "service";

    private CompoundButton toggle;
    private TextView summary;
    /** null on the category list, otherwise the open category. */
    private String page;

    private boolean dark;
    private int colorBg;
    private int colorFg;
    private int colorMuted;
    private int colorCard;
    private int colorCardButton;
    private int colorStroke;
    private int colorAccent;
    private int colorOnAccent;
    private int colorOnCardButton;

    @Override
    protected void onCreate(Bundle saved) {
        dark = detectDark();
        setTheme(dark ? android.R.style.Theme_DeviceDefault
                : android.R.style.Theme_DeviceDefault_Light);
        super.onCreate(saved);
        setTitle("Мод OpenCloud");
        applyPalette();
        page = saved == null ? null : saved.getString(KEY_PAGE);

        int edge = pixels(20);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(1); // vertical
        column.setPadding(edge, edge, edge, edge);
        column.setBackgroundColor(colorBg);

        if (PAGE_APPEARANCE.equals(page)) {
            addBack(column);
            column.addView(category("Внешний вид", "Оформление TikTok, акцент мода и шрифт"));
            buildAppearance(column);
        } else if (PAGE_FEED.equals(page)) {
            addBack(column);
            column.addView(category("Лента", "Что показывать в рекомендациях"));
            buildFeed(column);
        } else if (PAGE_REGION.equals(page)) {
            addBack(column);
            column.addView(category("Регион", "Страна и оператор, которые видит TikTok"));
            buildRegion(column);
        } else if (PAGE_PLUGINS.equals(page)) {
            addBack(column);
            column.addView(category("Плагины .oc", "Пресеты настроек, которые можно импортировать"));
            buildPlugins(column);
        } else if (PAGE_SERVICE.equals(page)) {
            addBack(column);
            column.addView(category("Служебное", "Логи, состояние сборки и сброс настроек"));
            buildService(column);
        } else {
            page = null;
            addHeader(column);
            addCategoryRows(column);
        }

        ScrollView frame = new ScrollView(this);
        frame.setBackgroundColor(colorBg);
        frame.addView(column);
        setContentView(frame);
        applyInsets(frame, column, edge);
        applySystemBars();
        Fonts.applyTo(frame);
        updateSummary();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (page != null) {
            out.putString(KEY_PAGE, page);
        }
    }

    @Override
    public void onBackPressed() {
        if (page != null) {
            closePage();
            return;
        }
        super.onBackPressed();
    }

    private void openPage(String id) {
        page = id;
        recreate();
    }

    private void closePage() {
        page = null;
        recreate();
    }

    /** Heading, mod badge and the one-paragraph introduction. */
    private void addHeader(LinearLayout column) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Мод OpenCloud");
        title.setTextSize(24);
        title.setLetterSpacing(-0.01f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(colorFg);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView badge = new TextView(this);
        badge.setText("MOD");
        badge.setTextSize(11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setLetterSpacing(0.14f);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(colorOnAccent);
        badge.setPadding(pixels(9), pixels(4), pixels(9), pixels(4));
        badge.setBackground(shape(colorAccent, 20, 0, 0));
        header.addView(badge);
        column.addView(header);

        TextView intro = new TextView(this);
        intro.setText("Это мод, встроенный в TikTok. Регион, тема, шрифт и плагины "
                + "меняются внутри самого TikTok — отдельного приложения нет. "
                + "Выберите раздел ниже.");
        intro.setTextSize(14);
        intro.setTextColor(colorMuted);
        spacing(intro, pixels(6));
        column.addView(intro);

        TextView built = new TextView(this);
        built.setText("Встроен в TikTok " + tiktokVersion());
        built.setTextSize(12);
        built.setTextColor(colorMuted);
        built.setLetterSpacing(0.04f);
        spacing(built, pixels(4));
        column.addView(built);
    }

    private void addCategoryRows(LinearLayout column) {
        column.addView(categoryRow("Внешний вид", appearanceSummary(), PAGE_APPEARANCE),
                rowParams(pixels(18)));
        column.addView(categoryRow("Лента", feedSummary(), PAGE_FEED),
                rowParams(pixels(10)));
        column.addView(categoryRow("Регион", regionSummary(), PAGE_REGION),
                rowParams(pixels(10)));
        column.addView(categoryRow("Плагины .oc", pluginsSummary(), PAGE_PLUGINS),
                rowParams(pixels(10)));
        column.addView(categoryRow("Служебное", "Логи, модули сборки и сброс", PAGE_SERVICE),
                rowParams(pixels(10)));
    }

    private LinearLayout.LayoutParams rowParams(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        return params;
    }

    private View categoryRow(String title, String hint, final String target) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(pixels(18), pixels(16), pixels(14), pixels(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(colorCard);
        background.setCornerRadius(pixels(20));
        card.setBackground(new RippleDrawable(
                ColorStateList.valueOf(withAlpha(colorOnCardButton, 0.12f)), background, null));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                openPage(target);
            }
        });

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(colorFg);
        text.addView(name);
        TextView sub = new TextView(this);
        sub.setText(hint);
        sub.setTextSize(13);
        sub.setTextColor(colorMuted);
        sub.setPadding(0, pixels(3), 0, 0);
        text.addView(sub);
        card.addView(text, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextSize(26);
        chevron.setTextColor(colorMuted);
        chevron.setPadding(pixels(12), 0, pixels(2), 0);
        card.addView(chevron);
        return card;
    }

    private void addBack(LinearLayout column) {
        TextView back = new TextView(this);
        back.setText("‹  Назад");
        back.setTextSize(15);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setTextColor(colorAccent);
        back.setPadding(pixels(2), pixels(12), pixels(2), pixels(6));
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                closePage();
            }
        });
        column.addView(back);
    }

    private String appearanceSummary() {
        int mode = ThemePlugin.forcedMode(this);
        String theme = mode == ThemePlugin.LIGHT ? "Светлая"
                : mode == ThemePlugin.DARK ? "Тёмная" : "Системная";
        return theme + " · " + Palette.label(Palette.selected(this)) + " · "
                + Fonts.selectedLabel(this);
    }

    private String feedSummary() {
        boolean ads = Plugins.isEnabled(this, Plugins.NO_ADS);
        boolean live = Plugins.isEnabled(this, Plugins.NO_LIVE);
        if (ads && live) {
            return "Без рекламы и эфиров";
        }
        if (ads) {
            return "Без рекламы";
        }
        if (live) {
            return "Без эфиров";
        }
        return "Фильтры выключены";
    }

    private String regionSummary() {
        Profile profile = currentProfile();
        if (profile == null) {
            return "Не выбран";
        }
        return profile.country() + " — " + profile.operator() + " (" + profile.numeric() + ")"
                + (Config.isOn(this) ? "" : " · выкл.");
    }

    private String pluginsSummary() {
        int count = PluginStore.installed(this).size();
        return count == 0 ? "Пресетов нет" : "Импортировано: " + count;
    }

    /* ------------------------------ Внешний вид ------------------------------ */
    private void buildAppearance(LinearLayout column) {
        LinearLayout appearance = group();
        column.addView(appearance);

        appearance.addView(groupLabel("Тема TikTok"));

        TextView themeIntro = new TextView(this);
        themeIntro.setText("Принудительный режим оформления TikTok. «Системная» — как в Android.");
        themeIntro.setTextSize(13);
        themeIntro.setTextColor(colorMuted);
        spacing(themeIntro, pixels(2));
        appearance.addView(themeIntro);

        int mode = ThemePlugin.forcedMode(this);
        if (mode == ThemePlugin.AUTO) {
            mode = ThemePlugin.isDark(this) ? ThemePlugin.DARK : ThemePlugin.FOLLOW_SYSTEM;
        }
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        spacing(themeRow, pixels(10));
        themeRow.addView(themeSegment("Системная", ThemePlugin.FOLLOW_SYSTEM, mode),
                segmentParams(0));
        themeRow.addView(themeSegment("Светлая", ThemePlugin.LIGHT, mode),
                segmentParams(8));
        themeRow.addView(themeSegment("Тёмная", ThemePlugin.DARK, mode),
                segmentParams(8));
        appearance.addView(themeRow);

        appearance.addView(groupLabel("Акцентная тема"));

        TextView accentIntro = new TextView(this);
        accentIntro.setText("Цвет разделов, переключателей и кнопок этого экрана. "
                + "На оформление самого TikTok не влияет.");
        accentIntro.setTextSize(13);
        accentIntro.setTextColor(colorMuted);
        spacing(accentIntro, pixels(2));
        appearance.addView(accentIntro);

        appearance.addView(accentPicker());

        TextView accentValue = new TextView(this);
        accentValue.setText("Выбрано: " + Palette.label(Palette.selected(this)));
        accentValue.setTextSize(13);
        accentValue.setTextColor(colorMuted);
        spacing(accentValue, pixels(8));
        appearance.addView(accentValue);

        appearance.addView(groupLabel("Шрифт"));

        TextView fontIntro = new TextView(this);
        fontIntro.setText("Системный шрифт или свой файл .ttf/.otf. Мод применяет его сразу, "
                + "TikTok — при включённом переключателе и после перезапуска.");
        fontIntro.setTextSize(13);
        fontIntro.setTextColor(colorMuted);
        spacing(fontIntro, pixels(2));
        appearance.addView(fontIntro);

        TextView fontValue = new TextView(this);
        fontValue.setText(Fonts.selectedLabel(this));
        fontValue.setTextSize(16);
        fontValue.setTextColor(colorFg);
        fontValue.setPadding(pixels(2), pixels(10), 0, 0);
        appearance.addView(fontValue);

        TextView chooseFont = accentButton("Выбрать шрифт");
        spacing(chooseFont, pixels(10));
        chooseFont.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                showFontDialog();
            }
        });
        appearance.addView(chooseFont);

        int[][] states = {{android.R.attr.state_checked}, {}};
        Switch fontSwitch = new Switch(this);
        fontSwitch.setText("Применять шрифт в TikTok");
        fontSwitch.setTextSize(16);
        fontSwitch.setTextColor(colorFg);
        fontSwitch.setChecked(Plugins.isEnabled(this, Plugins.FONT) && Fonts.isCustom(this));
        fontSwitch.setEnabled(Fonts.isCustom(this));
        fontSwitch.setThumbTintList(new ColorStateList(states,
                new int[]{colorAccent, colorStroke}));
        fontSwitch.setTrackTintList(new ColorStateList(states,
                new int[]{withAlpha(colorAccent, 0.55f), colorCard}));
        fontSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Plugins.setEnabled(SettingsActivity.this, Plugins.FONT, checked);
                Toast.makeText(SettingsActivity.this,
                        checked ? "Шрифт применится после перезапуска TikTok"
                                : "Шрифт TikTok отключён",
                        Toast.LENGTH_SHORT).show();
            }
        });
        spacing(fontSwitch, pixels(12));
        appearance.addView(fontSwitch);
    }

    /* --------------------------------- Лента --------------------------------- */
    private void buildFeed(LinearLayout column) {
        LinearLayout feedGroup = group();
        column.addView(feedGroup);

        TextView feedIntro = new TextView(this);
        feedIntro.setText("Фильтры применяются сразу, без пересборки APK. "
                + "Следующая загрузка ленты будет уже без лишнего.");
        feedIntro.setTextSize(13);
        feedIntro.setTextColor(colorMuted);
        spacing(feedIntro, pixels(2));
        feedGroup.addView(feedIntro);

        Plugins.Descriptor[] plugins = Plugins.all();
        for (int i = 0; i < plugins.length; i++) {
            if (!plugins[i].runtime
                    || Plugins.DARK_THEME.equals(plugins[i].id)
                    || Plugins.FONT.equals(plugins[i].id)) {
                continue; // handled by the theme, accent and font selectors
            }
            feedGroup.addView(pluginRow(plugins[i]));
        }
    }

    /* --------------------------------- Регион -------------------------------- */
    private void buildRegion(LinearLayout column) {
        LinearLayout regionGroup = group();
        column.addView(regionGroup);

        int[][] states = {{android.R.attr.state_checked}, {}};
        Switch toggleSwitch = new Switch(this);
        toggleSwitch.setText("Использовать профиль региона");
        toggleSwitch.setTextSize(16);
        toggleSwitch.setTextColor(colorFg);
        toggleSwitch.setChecked(Config.isOn(this));
        toggleSwitch.setThumbTintList(new ColorStateList(states,
                new int[]{colorAccent, colorStroke}));
        toggleSwitch.setTrackTintList(new ColorStateList(states,
                new int[]{withAlpha(colorAccent, 0.55f), colorCard}));
        toggle = toggleSwitch;
        spacing(toggle, pixels(12));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Config.setEnabled(SettingsActivity.this, checked);
                updateSummary();
            }
        });
        regionGroup.addView(toggle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(1);
        card.setPadding(pixels(16), pixels(16), pixels(16), pixels(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(colorCardButton);
        background.setCornerRadius(pixels(24));
        card.setBackground(new RippleDrawable(
                ColorStateList.valueOf(withAlpha(colorOnCardButton, 0.12f)), background, null));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                showRegionDialog();
            }
        });
        spacing(card, pixels(12));
        regionGroup.addView(card);

        TextView cardTitle = new TextView(this);
        cardTitle.setText("Выбор региона");
        cardTitle.setTextSize(20);
        cardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        cardTitle.setTextColor(colorOnCardButton);
        card.addView(cardTitle);

        summary = new TextView(this);
        summary.setTextSize(14);
        summary.setTextColor(colorOnCardButton);
        summary.setPadding(0, pixels(6), 0, 0);
        card.addView(summary);

        TextView cardHint = new TextView(this);
        cardHint.setText("Нажмите, чтобы выбрать страну и оператора.");
        cardHint.setTextSize(13);
        cardHint.setTextColor(withAlpha(colorOnCardButton, 0.75f));
        cardHint.setPadding(0, pixels(8), 0, 0);
        card.addView(cardHint);
    }

    /* ------------------------------ Плагины .oc ------------------------------ */
    private void buildPlugins(LinearLayout column) {
        LinearLayout pluginGroup = group();
        column.addView(pluginGroup);

        TextView customIntro = new TextView(this);
        customIntro.setText("Файлы .oc: зашифрованные пресеты. Импорт добавляет их в список, "
                + "применение переключает темы и регион. Формат — docs/plugins.md.");
        customIntro.setTextSize(13);
        customIntro.setTextColor(colorMuted);
        spacing(customIntro, pixels(2));
        pluginGroup.addView(customIntro);

        TextView importButton = accentButton("Импортировать .oc");
        spacing(importButton, pixels(14));
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, REQUEST_PLUGIN);
            }
        });
        pluginGroup.addView(importButton);

        TextView exportButton = outlineButton("Экспортировать настройки в .oc");
        spacing(exportButton, pixels(10));
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_TITLE, exportName());
                startActivityForResult(intent, REQUEST_EXPORT);
            }
        });
        pluginGroup.addView(exportButton);

        List<PluginStore.Plugin> custom = PluginStore.installed(this);
        if (custom.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Пока ничего не импортировано.");
            empty.setTextSize(13);
            empty.setTextColor(colorMuted);
            spacing(empty, pixels(14));
            pluginGroup.addView(empty);
        } else {
            for (int i = 0; i < custom.size(); i++) {
                pluginGroup.addView(customPluginRow(custom.get(i)));
            }
        }
    }

    /* -------------------------------- Служебное ------------------------------- */
    private void buildService(LinearLayout column) {
        LinearLayout serviceGroup = group();
        column.addView(serviceGroup);

        TextView note = new TextView(this);
        note.setText("Профиль читается при каждом вызове, поэтому следующая проверка телефонии уже "
                + "использует новые значения. Если TikTok показывает старое — закройте и откройте его.");
        note.setTextSize(13);
        note.setTextColor(colorMuted);
        spacing(note, pixels(14));
        serviceGroup.addView(note);

        TextView showLogs = outlineButton("Открыть логи");
        spacing(showLogs, pixels(14));
        showLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                showLogsDialog();
            }
        });
        serviceGroup.addView(showLogs);

        Plugins.Descriptor[] plugins = Plugins.all();
        for (int i = 0; i < plugins.length; i++) {
            if (plugins[i].runtime) {
                continue;
            }
            serviceGroup.addView(pluginRow(plugins[i]));
        }

        TextView reset = outlineButton("Сбросить настройки мода");
        spacing(reset, pixels(16));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                confirmReset();
            }
        });
        serviceGroup.addView(reset);
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(colorBg);
        getWindow().setNavigationBarColor(colorBg);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (dark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private boolean detectDark() {
        try {
            return ThemePlugin.isDark(this);
        } catch (Throwable ignored) {
            // Fall through to the system setting.
        }
        int mode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyPalette() {
        Palette palette = Palette.of(this, dark);
        colorBg = palette.surface;
        colorFg = palette.onSurface;
        colorMuted = palette.onSurfaceVariant;
        colorCard = palette.surfaceContainerHigh;
        colorCardButton = palette.primaryContainer;
        colorStroke = palette.outline;
        colorAccent = palette.primary;
        colorOnAccent = palette.onPrimary;
        colorOnCardButton = palette.onPrimaryContainer;
    }

    private int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(alpha * 255f) << 24);
    }

    /** Big category heading with a one-line explanation underneath. */
    private LinearLayout category(String label, String hint) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(pixels(4), 0, pixels(4), 0);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(colorFg);
        head.addView(title);

        TextView sub = new TextView(this);
        sub.setText(hint);
        sub.setTextSize(13);
        sub.setTextColor(colorMuted);
        sub.setPadding(0, pixels(2), 0, 0);
        head.addView(sub);

        spacing(head, pixels(24));
        return head;
    }

    /** Rounded card that groups the settings of one category. */
    private LinearLayout group() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(pixels(16), pixels(4), pixels(16), pixels(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(colorCard);
        background.setCornerRadius(pixels(24));
        card.setBackground(background);
        spacing(card, pixels(10));
        return card;
    }

    private TextView groupLabel(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(colorFg);
        view.setLetterSpacing(0.01f);
        spacing(view, pixels(16));
        return view;
    }

    /** Five swatches per row, so no horizontal scrolling is needed on phones. */
    private LinearLayout accentPicker() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        spacing(block, pixels(12));

        String current = Palette.selected(this);
        String[] keys = Palette.accents();
        LinearLayout row = null;
        for (int i = 0; i < keys.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                if (i > 0) {
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    params.topMargin = pixels(10);
                    row.setLayoutParams(params);
                }
                block.addView(row);
            }
            row.addView(accentSwatch(keys[i], keys[i].equals(current)));
        }
        return block;
    }

    private View accentSwatch(final String key, boolean selected) {
        int size = pixels(46);
        FrameLayout holder = new FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.rightMargin = pixels(10);
        holder.setLayoutParams(params);
        holder.setClickable(true);
        holder.setFocusable(true);

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x00000000);
        ring.setStroke(pixels(selected ? 3 : 1),
                selected ? colorFg : withAlpha(colorStroke, 0.45f));
        View ringView = new View(this);
        ringView.setBackground(ring);
        holder.addView(ringView, new FrameLayout.LayoutParams(size, size));

        GradientDrawable fill = new GradientDrawable();
        fill.setShape(GradientDrawable.OVAL);
        fill.setColor(Palette.accentPreview(this, key, dark));
        View dot = new View(this);
        int inset = pixels(7);
        FrameLayout.LayoutParams dotParams =
                new FrameLayout.LayoutParams(size - inset * 2, size - inset * 2);
        dotParams.leftMargin = inset;
        dotParams.topMargin = inset;
        dot.setBackground(fill);
        holder.addView(dot, dotParams);

        holder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                Palette.select(SettingsActivity.this, key);
                recreate();
            }
        });
        return holder;
    }

    private View pluginRow(final Plugins.Descriptor plugin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(1);
        row.setPadding(0, pixels(10), 0, pixels(4));

        if (plugin.runtime) {
            Switch box = new Switch(this);
            box.setText(plugin.title);
            box.setTextSize(16);
            box.setTextColor(colorFg);
            box.setChecked(Plugins.isEnabled(this, plugin.id));
            int[][] states = {{android.R.attr.state_checked}, {}};
            box.setThumbTintList(new ColorStateList(states,
                    new int[]{colorAccent, colorStroke}));
            box.setTrackTintList(new ColorStateList(states,
                    new int[]{withAlpha(colorAccent, 0.55f), colorCard}));
            box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    Plugins.setEnabled(SettingsActivity.this, plugin.id, checked);
                    if (Plugins.DARK_THEME.equals(plugin.id)) {
                        ThemePlugin.refresh();
                    }
                    recreate();
                }
            });
            row.addView(box);
        } else {
            TextView label = new TextView(this);
            label.setText(plugin.title + "  ✓");
            label.setTextSize(16);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextColor(colorFg);
            row.addView(label);
        }

        TextView description = new TextView(this);
        description.setText(plugin.description);
        description.setTextSize(13);
        description.setTextColor(colorMuted);
        description.setPadding(pixels(2), 0, 0, 0);
        row.addView(description);
        return row;
    }

    private View customPluginRow(final PluginStore.Plugin plugin) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(1);
        card.setPadding(pixels(14), pixels(14), pixels(14), pixels(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(colorCard);
        background.setCornerRadius(pixels(20));
        card.setBackground(background);
        spacing(card, pixels(12));

        TextView name = new TextView(this);
        name.setText(plugin.name + "   v" + plugin.version);
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(colorFg);
        card.addView(name);

        if (plugin.author.length() > 0) {
            TextView author = new TextView(this);
            author.setText(plugin.author);
            author.setTextSize(12);
            author.setTextColor(colorAccent);
            author.setPadding(0, pixels(2), 0, 0);
            card.addView(author);
        }

        TextView description = new TextView(this);
        description.setText(plugin.description);
        description.setTextSize(13);
        description.setTextColor(colorMuted);
        description.setPadding(0, pixels(6), 0, 0);
        card.addView(description);

        TextView meta = new TextView(this);
        meta.setText("id: " + plugin.id + " • действий: " + plugin.actions.length());
        meta.setTextSize(11);
        meta.setTextColor(colorMuted);
        meta.setPadding(0, pixels(6), 0, 0);
        card.addView(meta);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        spacing(buttons, pixels(12));

        TextView apply = accentButton("Применить");
        apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                int applied = PluginStore.apply(SettingsActivity.this, plugin);
                Toast.makeText(SettingsActivity.this,
                        "Применено действий: " + applied, Toast.LENGTH_SHORT).show();
                recreate();
            }
        });
        buttons.addView(apply, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView delete = outlineButton("Удалить");
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        deleteParams.leftMargin = pixels(10);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                PluginStore.delete(SettingsActivity.this, plugin);
                Toast.makeText(SettingsActivity.this,
                        "Плагин удалён", Toast.LENGTH_SHORT).show();
                recreate();
            }
        });
        buttons.addView(delete, deleteParams);
        card.addView(buttons);
        return card;
    }

    private LinearLayout.LayoutParams segmentParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        params.leftMargin = pixels(leftMargin);
        return params;
    }

    private TextView themeSegment(String label, final int value, int current) {
        boolean selected = value == current;
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(pixels(8), pixels(12), pixels(8), pixels(12));
        if (selected) {
            button.setTextColor(colorOnCardButton);
            button.setBackground(ripple(colorCardButton, 20, 0, 0,
                    withAlpha(colorOnCardButton, 0.20f)));
        } else {
            button.setTextColor(colorFg);
            button.setBackground(ripple(0x00000000, 20, 1, colorStroke,
                    withAlpha(colorAccent, 0.12f)));
        }
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                ThemePlugin.setForcedMode(SettingsActivity.this, value);
                ThemePlugin.refresh();
                recreate();
            }
        });
        return button;
    }

    private GradientDrawable shape(int fill, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(pixels(radius));
        if (strokeWidth > 0) {
            drawable.setStroke(pixels(strokeWidth), strokeColor);
        }
        return drawable;
    }

    private RippleDrawable ripple(int fill, int radius, int strokeWidth, int strokeColor,
                                  int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                shape(fill, radius, strokeWidth, strokeColor),
                shape(0xFFFFFFFF, radius, 0, 0));
    }

    private TextView accentButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(pixels(24), pixels(14), pixels(24), pixels(14));
        button.setTextColor(colorOnAccent);
        button.setBackground(ripple(colorAccent, 20, 0, 0, withAlpha(colorOnAccent, 0.20f)));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView outlineButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(pixels(24), pixels(14), pixels(24), pixels(14));
        button.setTextColor(colorOnCardButton);
        button.setBackground(ripple(colorCardButton, 20, 0, 0,
                withAlpha(colorOnCardButton, 0.20f)));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQUEST_PLUGIN) {
            handleImport(data);
        } else if (requestCode == REQUEST_EXPORT) {
            handleExport(data);
        } else if (requestCode == REQUEST_FONT && data.getData() != null) {
            handleFont(data.getData());
        }
    }

    private void handleImport(Intent data) {
        int imported = 0;
        String lastError = null;
        android.content.ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                try {
                    importUri(uri);
                    imported++;
                } catch (Throwable error) {
                    lastError = describe(error);
                }
            }
        } else if (data.getData() != null) {
            try {
                importUri(data.getData());
                imported = 1;
            } catch (Throwable error) {
                lastError = describe(error);
            }
        }
        if (imported == 0) {
            Toast.makeText(this, "Не удалось импортировать .oc: "
                    + (lastError == null ? "файл не выбран" : lastError),
                    Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, imported == 1 ? "Плагин импортирован"
                : "Импортировано плагинов: " + imported, Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void importUri(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IllegalStateException("cannot open the selected file");
        }
        try {
            PluginStore.importFrom(this, input);
        } finally {
            try {
                input.close();
            } catch (Throwable ignored) {
                // Nothing useful to do here.
            }
        }
    }

    private void handleExport(Intent data) {
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        java.io.OutputStream output = null;
        try {
            byte[] bytes = PluginCrypto.encode(buildExportJson().toString(2));
            output = getContentResolver().openOutputStream(uri);
            if (output == null) {
                throw new IllegalStateException("cannot open the destination");
            }
            output.write(bytes);
            output.flush();
            Toast.makeText(this, "Настройки сохранены в .oc", Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, "Не удалось сохранить .oc: " + describe(error),
                    Toast.LENGTH_LONG).show();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                    // Nothing useful to do here.
                }
            }
        }
    }

    private String exportName() {
        Profile profile = currentProfile();
        String suffix = profile == null ? "preset" : profile.code() + "-" + profile.numeric();
        return "opencloud-" + suffix + ".oc";
    }

    private org.json.JSONObject buildExportJson() throws Exception {
        Profile profile = currentProfile();
        org.json.JSONObject root = new org.json.JSONObject();
        root.put("id", "export-" + (profile == null ? "preset" : profile.code() + "-" + profile.numeric()));
        root.put("name", profile == null ? "OpenCloud preset"
                : profile.country() + " — " + profile.operator());
        root.put("version", 1);
        root.put("author", "OpenCloud");
        root.put("description", "Экспорт настроек OpenCloud.");
        org.json.JSONArray actions = new org.json.JSONArray();
        org.json.JSONObject theme = new org.json.JSONObject();
        theme.put("type", "dark_theme");
        theme.put("enabled", ThemePlugin.isDark(this));
        actions.put(theme);
        if (profile != null) {
            org.json.JSONObject region = new org.json.JSONObject();
            region.put("type", "region");
            region.put("country", profile.code());
            region.put("operator", profile.numeric());
            actions.put(region);
        }
        root.put("actions", actions);
        return root;
    }

    private Profile currentProfile() {
        Profile profile = Atlas.find(Config.selectedIso(this), Config.selectedOperator(this));
        return profile == null ? Atlas.defaultProfile() : profile;
    }

    private String describe(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.length() == 0) {
            message = error.getClass().getSimpleName();
        }
        return message;
    }

    private void confirmReset() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this,
                dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                        : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setTitle("Сбросить настройки?");
        builder.setMessage("Будут удалены выбранный регион, тема, акцент и все импортированные .oc плагины.");
        builder.setPositiveButton("Сбросить", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Prefs.open(SettingsActivity.this).edit().clear().apply();
                PluginStore.deleteAll(SettingsActivity.this);
                Fonts.deleteImported(SettingsActivity.this);
                ThemePlugin.refresh();
                Toast.makeText(SettingsActivity.this, "Настройки сброшены",
                        Toast.LENGTH_SHORT).show();
                recreate();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    /**
     * Android 15 draws apps edge-to-edge even if they use a plain framework
     * theme, so the title and the region card need the status/navigation bar
     * insets as extra padding.
     */
    private void applyInsets(final ScrollView frame, final LinearLayout column,
                             final int edge) {
        frame.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int top = insets.getSystemWindowInsetTop();
                int bottom = insets.getSystemWindowInsetBottom();
                column.setPadding(edge, edge + top + pixels(8), edge, edge + bottom);
                return insets;
            }
        });
        frame.requestApplyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SettingsEntry.refreshCaption();
    }

    private String tiktokVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable error) {
            return "";
        }
    }

    private void updateSummary() {
        Profile profile = Atlas.find(Config.selectedIso(this), Config.selectedOperator(this));
        if (profile == null) {
            profile = Atlas.defaultProfile();
        }
        if (profile == null) {
            summary.setText("Не выбран");
            return;
        }
        summary.setText(profile.country() + " — " + profile.operator()
                + " (" + profile.numeric() + ")");
    }

    private void showRegionDialog() {
        final List<Profile> countries = Atlas.countries();
        if (countries.isEmpty()) {
            return;
        }
        final List<Profile> filtered = new ArrayList<Profile>(countries);
        final List<String> countryLabels = new ArrayList<String>();
        int countryIndex = 0;
        String currentIso = Config.selectedIso(this);
        for (int i = 0; i < countries.size(); i++) {
            Profile country = countries.get(i);
            countryLabels.add(country.country());
            if (country.code().equals(currentIso)) {
                countryIndex = i;
            }
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(1);
        content.setPadding(pixels(20), pixels(4), pixels(20), 0);

        TextView countryLabel = new TextView(this);
        countryLabel.setText("Страна");
        countryLabel.setTextColor(colorFg);
        countryLabel.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(countryLabel);

        final EditText search = new EditText(this);
        search.setHint("Поиск страны");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(colorFg);
        search.setHintTextColor(colorMuted);
        if (search.getBackground() != null) {
            search.getBackground().setTint(colorAccent);
        }
        spacing(search, pixels(8));
        content.addView(search);

        final Spinner countryPicker = new Spinner(this);
        final ArrayAdapter<String> countryAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, countryLabels);
        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        countryPicker.setAdapter(countryAdapter);
        countryPicker.setSelection(countryIndex);
        spacing(countryPicker, pixels(6));
        content.addView(countryPicker);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                String selectedIso = Config.selectedIso(SettingsActivity.this);
                filtered.clear();
                countryLabels.clear();
                int selected = 0;
                for (int i = 0; i < countries.size(); i++) {
                    Profile country = countries.get(i);
                    boolean matches = query.length() == 0
                            || country.country().toLowerCase(Locale.ROOT).contains(query)
                            || country.code().toLowerCase(Locale.ROOT).contains(query);
                    if (!matches) {
                        continue;
                    }
                    if (country.code().equals(selectedIso)) {
                        selected = filtered.size();
                    }
                    filtered.add(country);
                    countryLabels.add(country.country());
                }
                countryAdapter.notifyDataSetChanged();
                if (selected < countryLabels.size()) {
                    countryPicker.setSelection(selected);
                }
            }
        });

        TextView operatorLabel = new TextView(this);
        operatorLabel.setText("Оператор");
        operatorLabel.setTextColor(colorFg);
        operatorLabel.setTypeface(Typeface.DEFAULT_BOLD);
        spacing(operatorLabel, pixels(18));
        content.addView(operatorLabel);

        final List<String> operatorLabels = new ArrayList<String>();
        final ArrayAdapter<String> operatorAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, operatorLabels);
        operatorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        final Spinner operatorPicker = new Spinner(this);
        operatorPicker.setAdapter(operatorAdapter);
        spacing(operatorPicker, pixels(6));
        content.addView(operatorPicker);

        final Runnable refreshOperators = new Runnable() {
            @Override
            public void run() {
                int at = countryPicker.getSelectedItemPosition();
                if (at < 0 || at >= filtered.size()) {
                    return;
                }
                Profile country = filtered.get(at);
                List<Profile> operators = Atlas.profilesFor(country.code());
                operatorLabels.clear();
                int selected = 0;
                String currentOperator = Config.selectedOperator(SettingsActivity.this);
                boolean sameCountry = country.code().equals(Config.selectedIso(SettingsActivity.this));
                for (int i = 0; i < operators.size(); i++) {
                    Profile operator = operators.get(i);
                    operatorLabels.add(operator.operator() + "  (" + operator.numeric() + ")");
                    if (sameCountry && operator.numeric().equals(currentOperator)) {
                        selected = i;
                    }
                }
                operatorAdapter.notifyDataSetChanged();
                if (selected < operatorLabels.size()) {
                    operatorPicker.setSelection(selected);
                }
            }
        };

        countryPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshOperators.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        refreshOperators.run();
        Fonts.applyTo(content);

        AlertDialog.Builder builder = new AlertDialog.Builder(this,
                dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                        : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setTitle("Выбор региона");
        builder.setView(content);
        builder.setPositiveButton("Сохранить", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int at = countryPicker.getSelectedItemPosition();
                if (at < 0 || at >= filtered.size()) {
                    return;
                }
                Profile country = filtered.get(at);
                List<Profile> operators = Atlas.profilesFor(country.code());
                int selected = operatorPicker.getSelectedItemPosition();
                if (selected < 0 || selected >= operators.size()) {
                    selected = 0;
                }
                if (operators.isEmpty()) {
                    return;
                }
                Profile operator = operators.get(selected);
                Config.store(SettingsActivity.this, toggle.isChecked(),
                        country.code(), operator.numeric());
                updateSummary();
                Toast.makeText(SettingsActivity.this, "Регион сохранён",
                        Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showLogsDialog() {
        final String text = Logs.read();
        TextView body = new TextView(this);
        body.setText(text.length() == 0 ? "Пока пусто." : text);
        body.setTextSize(11);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextColor(colorFg);
        body.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(pixels(16), pixels(8), pixels(16), pixels(8));
        scroll.addView(body);

        new AlertDialog.Builder(this,
                dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                        : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Логи")
                .setView(scroll)
                .setPositiveButton("Копировать", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager clipboard =
                                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard == null) {
                            return;
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("OpenCloud", text));
                        Toast.makeText(SettingsActivity.this, "Логи скопированы",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Очистить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Logs.clear();
                        Toast.makeText(SettingsActivity.this, "Логи очищены",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showFontDialog() {
        final AlertDialog[] holder = new AlertDialog[1];
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(1);
        content.setPadding(pixels(20), pixels(4), pixels(20), 0);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(1);
        addFontRow(list, "Системный (по умолчанию)", "", holder);
        List<Fonts.Option> fonts = Fonts.systemFonts();
        for (int i = 0; i < fonts.size(); i++) {
            Fonts.Option option = fonts.get(i);
            addFontRow(list, option.label, option.path, holder);
        }
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, pixels(360)));

        TextView load = outlineButton("Загрузить .ttf / .otf");
        spacing(load, pixels(10));
        load.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
                pickFontFile();
            }
        });
        content.addView(load);

        if (Fonts.isCustom(this)) {
            TextView reset = outlineButton("Сбросить на системный шрифт");
            spacing(reset, pixels(8));
            reset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View clicked) {
                    Fonts.useSystem(SettingsActivity.this);
                    Plugins.setEnabled(SettingsActivity.this, Plugins.FONT, false);
                    if (holder[0] != null) {
                        holder[0].dismiss();
                    }
                    recreate();
                }
            });
            content.addView(reset);
        }

        AlertDialog dialog = new AlertDialog.Builder(this,
                dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                        : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Шрифт")
                .setView(content)
                .setNegativeButton("Отмена", null)
                .create();
        holder[0] = dialog;
        Fonts.applyTo(content);
        dialog.show();
    }

    private void addFontRow(LinearLayout list, final String label, final String path,
                            final AlertDialog[] holder) {
        boolean selected = path.length() == 0
                ? !Fonts.isCustom(this)
                : path.equals(Fonts.selectedPath(this));
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextSize(15);
        row.setPadding(pixels(14), pixels(12), pixels(14), pixels(12));
        if (selected) {
            row.setTextColor(colorOnCardButton);
            row.setBackground(ripple(colorCardButton, 16, 0, 0,
                    withAlpha(colorOnCardButton, 0.20f)));
        } else {
            row.setTextColor(colorFg);
            row.setBackground(ripple(0x00000000, 16, 0, 0,
                    withAlpha(colorAccent, 0.10f)));
        }
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                if (path.length() == 0) {
                    Fonts.useSystem(SettingsActivity.this);
                    Plugins.setEnabled(SettingsActivity.this, Plugins.FONT, false);
                } else {
                    Fonts.use(SettingsActivity.this, path, label);
                    Plugins.setEnabled(SettingsActivity.this, Plugins.FONT, true);
                }
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
                recreate();
            }
        });
        list.addView(row);
    }

    private void pickFontFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "font/ttf", "font/otf", "application/x-font-ttf",
                "application/x-font-opentype", "application/octet-stream"});
        startActivityForResult(intent, REQUEST_FONT);
    }

    private void handleFont(Uri uri) {
        InputStream input = null;
        try {
            input = getContentResolver().openInputStream(uri);
            if (input == null) {
                throw new IllegalStateException("cannot open the selected file");
            }
            String label = Fonts.importFont(this, input, displayName(uri));
            Plugins.setEnabled(this, Plugins.FONT, true);
            Toast.makeText(this, "Шрифт: " + label, Toast.LENGTH_SHORT).show();
            recreate();
        } catch (Throwable error) {
            Toast.makeText(this, "Не удалось загрузить шрифт: " + describe(error),
                    Toast.LENGTH_LONG).show();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                    // Nothing useful to do here.
                }
            }
        }
    }

    private String displayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && cursor.getString(index) != null) {
                    return cursor.getString(index);
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the URI segment.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? "custom.ttf" : last;
    }

    private void spacing(View child, int top) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.topMargin = top;
        child.setLayoutParams(layout);
    }

    private int pixels(int points) {
        float scale = getResources().getDisplayMetrics().density;
        return Math.round(points * scale);
    }
}

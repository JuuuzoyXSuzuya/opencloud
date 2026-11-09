package dev.opencloud.region;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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

    private CompoundButton toggle;
    private TextView summary;

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
        setTitle("Настройки OpenCloud");
        applyPalette();

        int edge = pixels(20);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(1); // vertical
        column.setPadding(edge, edge, edge, edge);
        column.setBackgroundColor(colorBg);

        TextView title = new TextView(this);
        title.setText("Настройки OpenCloud");
        title.setTextSize(24);
        title.setLetterSpacing(-0.01f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(colorFg);
        column.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Регион, тема и дополнительные функции для TikTok.");
        intro.setTextSize(14);
        intro.setTextColor(colorMuted);
        spacing(intro, pixels(6));
        column.addView(intro);

        TextView regionTitle = section("Регион");
        column.addView(regionTitle);

        Switch toggleSwitch = new Switch(this);
        toggleSwitch.setText("Использовать профиль региона");
        toggleSwitch.setTextSize(16);
        toggleSwitch.setTextColor(colorFg);
        toggleSwitch.setChecked(Config.isOn(this));
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggleSwitch.setThumbTintList(new ColorStateList(states,
                new int[]{colorAccent, colorStroke}));
        toggleSwitch.setTrackTintList(new ColorStateList(states,
                new int[]{withAlpha(colorAccent, 0.55f), colorCard}));
        toggle = toggleSwitch;
        spacing(toggle, pixels(10));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Config.setEnabled(SettingsActivity.this, checked);
                updateSummary();
            }
        });
        column.addView(toggle);

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
        column.addView(card);

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

        column.addView(section("Функции"));

        TextView pluginIntro = new TextView(this);
        pluginIntro.setText("Дополнительные функции. Переключатели применяются сразу, "
                + "без пересборки APK.");
        pluginIntro.setTextSize(13);
        pluginIntro.setTextColor(colorMuted);
        spacing(pluginIntro, pixels(2));
        column.addView(pluginIntro);

        Plugins.Descriptor[] plugins = Plugins.all();
        for (int i = 0; i < plugins.length; i++) {
            column.addView(pluginRow(plugins[i]));
        }

        column.addView(section("Кастомные плагины"));

        TextView customIntro = new TextView(this);
        customIntro.setText("Файлы .oc: зашифрованные пресеты. Импорт добавляет их в список, "
                + "применение переключает темы и регион. Формат — docs/plugins.md.");
        customIntro.setTextSize(13);
        customIntro.setTextColor(colorMuted);
        spacing(customIntro, pixels(2));
        column.addView(customIntro);

        TextView importButton = accentButton("Импортировать .oc");
        spacing(importButton, pixels(12));
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_PLUGIN);
            }
        });
        column.addView(importButton);

        List<PluginStore.Plugin> custom = PluginStore.installed(this);
        if (custom.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Пока ничего не импортировано.");
            empty.setTextSize(13);
            empty.setTextColor(colorMuted);
            spacing(empty, pixels(10));
            column.addView(empty);
        } else {
            for (int i = 0; i < custom.size(); i++) {
                column.addView(customPluginRow(custom.get(i)));
            }
        }

        TextView note = new TextView(this);
        note.setText("Профиль читается при каждом вызове, поэтому следующая проверка телефонии уже "
                + "использует новые значения. Если TikTok показывает старое — закройте и откройте его.");
        note.setTextSize(13);
        note.setTextColor(colorMuted);
        spacing(note, pixels(20));
        column.addView(note);

        ScrollView frame = new ScrollView(this);
        frame.setBackgroundColor(colorBg);
        frame.addView(column);
        setContentView(frame);
        applyInsets(frame, column, edge);
        updateSummary();
    }

    private boolean detectDark() {
        try {
            if (Plugins.darkTheme(this)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall through to the system setting.
        }
        int mode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyPalette() {
        if (dark) {
            // Material 3 baseline dark scheme.
            colorBg = 0xFF141218;
            colorFg = 0xFFE6E0E9;
            colorMuted = 0xFFCAC4D0;
            colorCard = 0xFF2B2930;
            colorCardButton = 0xFF4F378B;
            colorStroke = 0xFF938F99;
            colorAccent = 0xFFD0BCFF;
            colorOnAccent = 0xFF381E72;
            colorOnCardButton = 0xFFEADDFF;
        } else {
            // Material 3 baseline light scheme.
            colorBg = 0xFFFEF7FF;
            colorFg = 0xFF1D1B20;
            colorMuted = 0xFF49454F;
            colorCard = 0xFFECE6F0;
            colorCardButton = 0xFFEADDFF;
            colorStroke = 0xFF79747E;
            colorAccent = 0xFF6750A4;
            colorOnAccent = 0xFFFFFFFF;
            colorOnCardButton = 0xFF21005D;
        }
    }

    private int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(alpha * 255f) << 24);
    }

    private TextView section(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(12);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(colorAccent);
        view.setAllCaps(true);
        view.setLetterSpacing(0.12f);
        spacing(view, pixels(26));
        return view;
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
        if (requestCode != REQUEST_PLUGIN || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        InputStream input = null;
        try {
            input = getContentResolver().openInputStream(uri);
            if (input == null) {
                throw new IllegalStateException("cannot open the selected file");
            }
            PluginStore.Plugin plugin = PluginStore.importFrom(this, input);
            Toast.makeText(this, "Импортирован: " + plugin.name,
                    Toast.LENGTH_SHORT).show();
            recreate();
        } catch (Throwable error) {
            String message = error.getMessage();
            if (message == null || message.length() == 0) {
                message = error.getClass().getSimpleName();
            }
            Toast.makeText(this, "Не удалось импортировать .oc: " + message,
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

        final Spinner countryPicker = new Spinner(this);
        ArrayAdapter<String> countryAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, countryLabels);
        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        countryPicker.setAdapter(countryAdapter);
        countryPicker.setSelection(countryIndex);
        spacing(countryPicker, pixels(6));
        content.addView(countryPicker);

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
                if (at < 0 || at >= countries.size()) {
                    return;
                }
                Profile country = countries.get(at);
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this,
                dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                        : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setTitle("Выбор региона");
        builder.setView(content);
        builder.setPositiveButton("Сохранить", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int at = countryPicker.getSelectedItemPosition();
                if (at < 0 || at >= countries.size()) {
                    return;
                }
                Profile country = countries.get(at);
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

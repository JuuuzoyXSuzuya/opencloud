package dev.opencloud.region;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The only screen OpenCloud adds.  It is built in code so the patcher never
 * has to add or rewrite a single resource in TikTok's resource table.
 */
public final class SettingsActivity extends Activity {
    private CheckBox toggle;
    private TextView summary;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setTheme(android.R.style.Theme_DeviceDefault_Light);
        setTitle("OpenCloud");

        int edge = pixels(20);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(1); // vertical
        column.setPadding(edge, edge, edge, edge);

        TextView title = new TextView(this);
        title.setText("OpenCloud");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        column.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Регион и оператор, которые будет видеть TikTok.");
        intro.setTextSize(14);
        spacing(intro, pixels(6));
        column.addView(intro);

        toggle = new CheckBox(this);
        toggle.setText("Использовать профиль региона");
        toggle.setChecked(Config.isOn(this));
        spacing(toggle, pixels(18));
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
        background.setColor(0xFFF0F1F3);
        background.setCornerRadius(pixels(12));
        background.setStroke(pixels(1), 0xFFD0D3D8);
        card.setBackground(background);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                showRegionDialog();
            }
        });
        spacing(card, pixels(18));
        column.addView(card);

        TextView cardTitle = new TextView(this);
        cardTitle.setText("Выбор региона");
        cardTitle.setTextSize(18);
        cardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(cardTitle);

        summary = new TextView(this);
        summary.setTextSize(14);
        summary.setPadding(0, pixels(6), 0, 0);
        card.addView(summary);

        TextView cardHint = new TextView(this);
        cardHint.setText("Нажмите, чтобы выбрать страну и оператора.");
        cardHint.setTextSize(12);
        cardHint.setPadding(0, pixels(6), 0, 0);
        card.addView(cardHint);

        TextView note = new TextView(this);
        note.setText("Профиль читается при каждом вызове, поэтому следующая проверка телефонии уже "
                + "использует новые значения. Если TikTok показывает старое — закройте и откройте его.");
        note.setTextSize(13);
        spacing(note, pixels(18));
        column.addView(note);

        ScrollView frame = new ScrollView(this);
        frame.addView(column);
        setContentView(frame);
        applyInsets(frame, column, edge);
        updateSummary();
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

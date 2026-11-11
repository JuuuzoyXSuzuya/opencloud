package dev.opencloud.region;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Font selection.
 *
 * The user can keep the system font or pick one of the fonts shipped in
 * {@code /system/fonts}, or import a custom {@code .ttf}/{@code .otf} file.
 * The choice is stored in OpenCloud's preferences and applied to both the
 * OpenCloud screen and (when the plugin is on) to TikTok through the call
 * sites rewritten by the patcher.
 */
final class Fonts {
    static final String PATH = "font_path";
    static final String LABEL = "font_label";

    /** Paths that must never be replaced: math fonts and icon/symbol fonts. */
    private static final String[] SKIP = {"jlm_", "combo_font", "emoji", "icon"};

    private static volatile String cachedPath;
    private static volatile Typeface cachedBase;

    static final class Option {
        final String label;
        final String path;

        Option(String label, String path) {
            this.label = label;
            this.path = path;
        }
    }

    private Fonts() {
    }

    static String selectedPath(Context context) {
        if (context == null) {
            return "";
        }
        return Prefs.open(context).getString(PATH, "");
    }

    static String selectedLabel(Context context) {
        String path = selectedPath(context);
        if (path.length() == 0) {
            return "Системный (по умолчанию)";
        }
        return Prefs.open(context).getString(LABEL, prettyName(path));
    }

    static boolean isCustom(Context context) {
        return selectedPath(context).length() > 0;
    }

    static void useSystem(Context context) {
        if (context == null) {
            return;
        }
        Prefs.open(context).edit().remove(PATH).remove(LABEL).apply();
        clear();
    }

    static void use(Context context, String path, String label) {
        if (context == null) {
            return;
        }
        Prefs.open(context).edit()
                .putString(PATH, path)
                .putString(LABEL, label)
                .apply();
        clear();
    }

    static void clear() {
        cachedPath = null;
        cachedBase = null;
    }

    /** Called from the patched Typeface call sites. */
    static Typeface create(String family, int style) {
        Typeface custom = current();
        if (custom != null) {
            return Typeface.create(custom, style);
        }
        return Typeface.create(family, style);
    }

    static Typeface create(Typeface base, int style) {
        Typeface custom = current();
        if (custom != null) {
            return Typeface.create(custom, style);
        }
        return Typeface.create(base, style);
    }

    static Typeface defaultStyle(int style) {
        Typeface custom = current();
        if (custom != null) {
            return Typeface.create(custom, style);
        }
        return Typeface.defaultFromStyle(style);
    }

    static Typeface fromAsset(android.content.res.AssetManager assets, String path) {
        Typeface custom = current();
        if (custom != null && !skip(path)) {
            return Typeface.create(custom, styleOf(path));
        }
        return Typeface.createFromAsset(assets, path);
    }

    static Typeface fromFile(String path) {
        Typeface custom = current();
        if (custom != null && !skip(path)) {
            return Typeface.create(custom, styleOf(path));
        }
        return Typeface.createFromFile(path);
    }

    /** Applies the selected font to every TextView below {@code root}. */
    static void applyTo(View root) {
        try {
            Typeface custom = loaded(App.context());
            if (custom == null || root == null) {
                return;
            }
            apply(root, custom);
        } catch (Throwable ignored) {
            // Keep the default typeface if the selected font cannot be applied.
        }
    }

    private static void apply(View view, Typeface custom) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            Typeface existing = text.getTypeface();
            int style = existing == null ? Typeface.NORMAL : existing.getStyle();
            text.setTypeface(custom, style);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                apply(group.getChildAt(i), custom);
            }
        }
    }

    /** Fonts installed in the system image, sorted by display name. */
    static List<Option> systemFonts() {
        List<Option> fonts = new ArrayList<Option>();
        File[] files = new File("/system/fonts").listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".ttf") && !name.endsWith(".otf")) {
                    continue;
                }
                if (name.contains("emoji") || name.contains("color")) {
                    continue;
                }
                fonts.add(new Option(prettyName(file.getName()), file.getAbsolutePath()));
            }
        }
        Collections.sort(fonts, new Comparator<Option>() {
            @Override
            public int compare(Option left, Option right) {
                return left.label.compareToIgnoreCase(right.label);
            }
        });
        return fonts;
    }

    /** Copies an imported font into app-private storage and validates it. */
    static String importFont(Context context, InputStream input, String displayName)
            throws Exception {
        File directory = new File(context.getFilesDir(), "fonts");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("cannot create the fonts directory");
        }
        File[] old = directory.listFiles();
        if (old != null) {
            for (File file : old) {
                file.delete();
            }
        }
        String lower = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        String extension = lower.endsWith(".otf") ? ".otf" : ".ttf";
        File target = new File(directory, "custom" + extension);
        FileOutputStream output = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            try {
                output.close();
            } catch (Throwable ignored) {
                // Nothing useful to do here.
            }
        }
        try {
            if (Typeface.createFromFile(target) == null) {
                throw new IllegalStateException("это не файл шрифта");
            }
        } catch (Throwable error) {
            target.delete();
            throw new IllegalStateException("это не файл шрифта");
        }
        String label = displayName == null || displayName.length() == 0
                ? target.getName() : displayName;
        use(context, target.getAbsolutePath(), label);
        return label;
    }

    static void deleteImported(Context context) {
        File directory = new File(context.getFilesDir(), "fonts");
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        clear();
    }

    private static Typeface current() {
        Context context = App.context();
        if (context == null || !Plugins.isEnabled(context, Plugins.FONT)) {
            return null;
        }
        return loaded(context);
    }

    private static Typeface loaded(Context context) {
        if (context == null) {
            return null;
        }
        String path = selectedPath(context);
        if (path.length() == 0) {
            return null;
        }
        Typeface base = cachedBase;
        if (path.equals(cachedPath) && base != null) {
            return base;
        }
        try {
            base = Typeface.createFromFile(path);
        } catch (Throwable error) {
            base = null;
        }
        cachedPath = path;
        cachedBase = base;
        return base;
    }

    private static boolean skip(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        for (int i = 0; i < SKIP.length; i++) {
            if (lower.contains(SKIP[i])) {
                return true;
            }
        }
        return false;
    }

    private static int styleOf(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        int style = Typeface.NORMAL;
        if (lower.contains("bold") || lower.contains("black") || lower.contains("heavy")) {
            style |= Typeface.BOLD;
        }
        if (lower.contains("italic") || lower.contains("oblique")) {
            style |= Typeface.ITALIC;
        }
        return style;
    }

    private static String prettyName(String path) {
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replace('_', ' ').replace('-', ' ').trim();
        if (name.length() == 0) {
            return "Шрифт";
        }
        return name;
    }
}

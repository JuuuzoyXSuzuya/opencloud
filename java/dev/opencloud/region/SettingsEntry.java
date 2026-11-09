package dev.opencloud.region;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Adds an OpenCloud entry to TikTok's own settings screen.
 *
 * <p>The settings screen is a Compose fragment whose root view is a
 * {@code ComposeView}.  Instead of trying to inject a cell into the Compose
 * tree (which is version specific), the patcher wraps that view into a
 * vertical container and puts a normal clickable row above it.  The row opens
 * {@link SettingsActivity}.  Only {@code android.R} and framework widgets are
 * used, so TikTok's resource table stays untouched.</p>
 */
public final class SettingsEntry {
    private static WeakReference<TextView> lastSubtitle = new WeakReference<TextView>(null);

    private SettingsEntry() {
    }

    /**
     * Called from the patched {@code onCreateView} of TikTok's settings
     * fragment.  Returns the view that the fragment should use as its root.
     *
     * @param compose  the ComposeView created by TikTok
     * @param fragment the fragment instance (typed as Object so the mod does
     *                 not need androidx on its compile classpath)
     */
    public static View decorate(View compose, Object fragment) {
        if (compose == null) {
            return null;
        }
        Activity activity = activityOf(fragment, compose.getContext());
        if (activity == null) {
            return compose;
        }
        Context context = compose.getContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        applyWindowInsets(root, compose);

        root.addView(buildRow(context, activity),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(isDark(context) ? 0xFF2A2C34 : 0xFFE6E7EA);
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 1))));

        LinearLayout.LayoutParams content = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        compose.setLayoutParams(content);
        root.addView(compose);
        return root;
    }

    private static void applyWindowInsets(final LinearLayout root, final View compose) {
        // The settings screen is edge-to-edge on Android 15+.  The wrapper is
        // now the fragment root, so it must take the system bars itself and
        // stop the ComposeView inside from applying them a second time.
        compose.setFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                view.setPadding(insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop() + dp(view.getContext(), 8),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return WindowInsets.CONSUMED;
                }
                return insets.consumeSystemWindowInsets();
            }
        });
        root.requestApplyInsets();
    }

    private static View buildRow(final Context context, final Activity activity) {
        boolean dark = isDark(context);
        int background = dark ? 0xFF121218 : 0xFFFFFFFF;
        int titleColor = dark ? 0xFFF2F3F7 : 0xFF161823;
        int subtitleColor = dark ? 0xFF9BA1AD : 0xFF8A8B91;
        int arrowColor = dark ? 0xFF7C8494 : 0xFFB0B2B8;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 20), dp(context, 14), dp(context, 16), dp(context, 14));
        row.setBackgroundColor(background);
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView title = new TextView(context);
        title.setText("Настройки OpenCloud");
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(titleColor);
        labels.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setTextSize(13);
        subtitle.setTextColor(subtitleColor);
        subtitle.setPadding(0, dp(context, 2), 0, 0);
        subtitle.setText(caption(activity));
        labels.addView(subtitle);
        lastSubtitle = new WeakReference<TextView>(subtitle);

        TextView arrow = new TextView(context);
        arrow.setText("\u203A");
        arrow.setTextSize(26);
        arrow.setTextColor(arrowColor);
        row.addView(arrow);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                Intent intent = new Intent(activity, SettingsActivity.class);
                activity.startActivity(intent);
            }
        });
        return row;
    }

    static void refreshCaption() {
        TextView subtitle = lastSubtitle.get();
        if (subtitle == null) {
            return;
        }
        subtitle.setText(caption(subtitle.getContext()));
    }

    private static String caption(Context context) {
        Profile profile = Atlas.find(Config.selectedIso(context), Config.selectedOperator(context));
        if (profile == null) {
            profile = Atlas.defaultProfile();
        }
        if (profile == null) {
            return "Не выбран";
        }
        return profile.country() + " — " + profile.operator() + " (" + profile.numeric() + ")";
    }

    private static Activity activityOf(Object fragment, Context context) {
        if (fragment != null) {
            try {
                Object value = fragment.getClass().getMethod("getActivity").invoke(fragment);
                if (value instanceof Activity) {
                    return (Activity) value;
                }
            } catch (Throwable ignored) {
                // Fall back to the context below.
            }
        }
        Context current = context;
        while (current != null) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            if (!(current instanceof android.content.ContextWrapper)) {
                break;
            }
            current = ((android.content.ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private static int dp(Context context, int value) {
        float scale = context.getResources().getDisplayMetrics().density;
        return Math.round(value * scale);
    }

    private static boolean isDark(Context context) {
        try {
            if (Plugins.darkTheme(context)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall through to the system setting.
        }
        int mode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}

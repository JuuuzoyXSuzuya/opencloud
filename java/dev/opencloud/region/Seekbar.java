package dev.opencloud.region;

import java.lang.reflect.Method;

/**
 * Scrubbing-bar bridge.
 *
 * <p>TikTok decides per video whether to draw a seek bar and hands the verdict
 * to a view as an int through {@code setSeekBarShowType}.  The method name is
 * real but the class that owns it is obfuscated, so the build matches the name
 * and shape on any owner and hands the receiver through as a plain Object.
 * While the switch is on the view is asked to always show the bar.</p>
 */
public final class Seekbar {
    /** The value the view understands as "always show". */
    private static final int SHOW = 1;

    private Seekbar() {
    }

    public static void seek(Object view, int type) {
        int requested = Plugins.isEnabled(App.context(), Plugins.SEEKBAR) ? SHOW : type;
        try {
            Method method = view.getClass().getMethod("setSeekBarShowType", int.class);
            method.invoke(view, Integer.valueOf(requested));
        } catch (Throwable ignored) {
            // A renamed view simply keeps TikTok's own decision.
        }
    }
}

package dev.opencloud.region;

import android.view.Surface;

/**
 * Frame-rate bridge.
 *
 * <p>TikTok asks its window for a rate of its own and a 120&nbsp;Hz phone can
 * end up running the feed at 60.  With the switch on, the requested rate is
 * replaced by a higher target; the system still clamps it to what the display
 * actually supports, so asking high is safe on any panel.</p>
 */
public final class Rate {
    private static final float TARGET = 120f;

    private Rate() {
    }

    public static void rate(Surface surface, float frameRate, int compatibility) {
        surface.setFrameRate(enabled() ? TARGET : frameRate, compatibility);
    }

    public static void rateStrat(Surface surface, float frameRate, int compatibility,
                                 int strategy) {
        surface.setFrameRate(enabled() ? TARGET : frameRate, compatibility, strategy);
    }

    private static boolean enabled() {
        return Plugins.isEnabled(App.context(), Plugins.HIGH_FPS);
    }
}

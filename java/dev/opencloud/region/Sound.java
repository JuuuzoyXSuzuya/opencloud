package dev.opencloud.region;

import com.ss.android.ugc.aweme.music.model.Music;

/**
 * Sound-unmute bridge.
 *
 * <p>When a track is pulled for copyright the video stays but its sound is
 * switched off through four answers on the sound's own model.  The build
 * redirects those four readers here; with the switch on they report the sound
 * as fine, and with it off every one forwards the real answer.</p>
 */
public final class Sound {
    private Sound() {
    }

    public static boolean available(Music music) {
        if (enabled()) {
            return true;
        }
        try {
            return music.available();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int status(Music music) {
        if (enabled()) {
            return 0;
        }
        try {
            return music.getMusicStatus();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean muteShare(Music music) {
        if (enabled()) {
            return false;
        }
        try {
            return music.isMuteShare();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int muteType(Music music) {
        if (enabled()) {
            return 0;
        }
        try {
            return music.getMuteType();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean enabled() {
        return Plugins.isEnabled(App.context(), Plugins.UNMUTE);
    }
}

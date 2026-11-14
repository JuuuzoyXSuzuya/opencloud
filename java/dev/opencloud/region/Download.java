package dev.opencloud.region;

import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.Video;
import com.ss.android.ugc.aweme.profile.model.User;

/**
 * Watermark-free download bridge.
 *
 * <p>TikTok's own model carries both addresses for the same file: the stamped
 * one the save button uses and the clean one beside it.  The build redirects
 * {@code Video.getDownloadAddr()} here so the clean address wins while the
 * switch is on; the per-post and per-account download bans are answered with
 * "allowed" the same way.  Every method falls straight back to TikTok's answer
 * when the switch is off, so the patch stays reversible.</p>
 */
public final class Download {
    private Download() {
    }

    public static UrlModel downloadAddr(Video video) {
        if (!enabled()) {
            return video.getDownloadAddr();
        }
        try {
            UrlModel clean = video.getDownloadNoWatermarkAddr();
            return clean != null ? clean : video.getDownloadAddr();
        } catch (Throwable ignored) {
            return video.getDownloadAddr();
        }
    }

    public static boolean awemePrevent(Aweme aweme) {
        if (enabled()) {
            return false;
        }
        try {
            return aweme.isPreventDownload();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean userPrevent(User user) {
        if (enabled()) {
            return false;
        }
        try {
            return user.isPreventDownload();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean enabled() {
        return Plugins.isEnabled(App.context(), Plugins.NO_WATERMARK);
    }
}

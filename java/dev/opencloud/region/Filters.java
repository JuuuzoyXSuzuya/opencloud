package dev.opencloud.region;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime content filters, called from TikTok code rewritten by the build.
 *
 * {@link #filterFeed(Object)} is prepended to
 * {@code FeedItemList.getItems()}: it drops advertising, live-stream and
 * photo posts and anything whose caption names a blocked hashtag.
 * {@link #hideLiveTab()} is prepended to {@code LiveTabProtocol.enable()}: it
 * makes the "LIVE" top tab disappear.
 *
 * Everything here is reflective and wrapped in {@code catch (Throwable)} on
 * purpose.  A filter must never be able to crash the app, and TikTok renames
 * or moves its internals between releases; an unsupported build then simply
 * keeps the original behaviour.
 */
public final class Filters {
    /** TikTok's own number for a live room in the feed. */
    private static final int TYPE_LIVE = 101;
    /** TikTok's own flag for a post the server marked as an advert. */
    private static final String AD_FLAG = "_isAd";

    private static Field itemsField;
    private static Class<?> itemsOwner;

    private static Method isAdMethod;
    private static Class<?> isAdOwner;

    private static Field isAdField;
    private static Class<?> isAdFieldOwner;

    private static Method awemeTypeMethod;
    private static Class<?> awemeTypeOwner;

    private static Method photoMethod;
    private static Class<?> photoOwner;

    /** The list instance that has already been checked and needs no rescan. */
    private static Object lastInput;
    /** The filtered list installed into the model, cached to avoid rework. */
    private static Object lastOutput;
    private static int lastSize = -1;

    private Filters() {
    }

    /**
     * Removes unwanted posts from a {@code FeedItemList}.
     *
     * The list is edited where possible, so callers that already hold the model
     * keep working.  The toggles are read on every call, hence a switch takes
     * effect on the next feed refresh.
     */
    public static void filterFeed(Object feedItemList) {
        try {
            if (feedItemList == null) {
                return;
            }
            android.content.Context context = App.context();
            boolean ads = Plugins.isEnabled(context, Plugins.NO_ADS);
            boolean live = Plugins.isEnabled(context, Plugins.NO_LIVE);
            boolean photos = Plugins.isEnabled(context, Plugins.NO_PHOTOS);
            List<String> tags = Plugins.isEnabled(context, Plugins.TAG_FILTER)
                    ? Tags.all(context) : null;
            if (!ads && !live && !photos && (tags == null || tags.isEmpty())) {
                return;
            }
            Field field = itemsField(feedItemList.getClass());
            if (field == null) {
                return;
            }
            Object value = field.get(feedItemList);
            if (!(value instanceof List)) {
                return;
            }
            List<?> items = (List<?>) value;
            if (items == lastInput) {
                return;
            }
            if (items == lastOutput && items.size() == lastSize) {
                return;
            }
            int total = items.size();
            ArrayList<Object> kept = new ArrayList<Object>(total);
            for (int index = 0; index < total; index++) {
                Object item = items.get(index);
                if (!drop(item, ads, live, photos, tags)) {
                    kept.add(item);
                }
            }
            if (kept.size() == total) {
                lastInput = items;
                return;
            }
            int removed = total - kept.size();
            /*
             * Edit in place: the model may hand this very list to other
             * components, and swapping in a plain ArrayList breaks code that
             * relies on the original list type.  If the list rejects removal
             * the filtered copy is installed instead.
             */
            List raw = items;
            try {
                for (int index = raw.size() - 1; index >= 0; index--) {
                    if (drop(raw.get(index), ads, live, photos, tags)) {
                        raw.remove(index);
                    }
                }
            } catch (Throwable unmodifiable) {
                field.set(feedItemList, kept);
            }
            lastOutput = items;
            lastSize = items.size();
            Logs.write("filter", "feed: removed " + removed + " of " + total);
        } catch (Throwable ignored) {
            // Never break a feed request because a filter could not run.
        }
    }

    /** True when the user asked to hide the "LIVE" top tab. */
    public static boolean hideLiveTab() {
        try {
            return Plugins.isEnabled(App.context(), Plugins.NO_LIVE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean drop(Object item, boolean ads, boolean live, boolean photos,
                                List<String> tags) {
        if (item == null) {
            return true;
        }
        if (live && isLive(item)) {
            return true; // a room is neither an advert nor a photo
        }
        if (ads && isAd(item)) {
            return true;
        }
        if (photos && isPhoto(item)) {
            return true;
        }
        return tags != null && !tags.isEmpty() && Tags.blocks(item, tags);
    }

    private static Field itemsField(Class<?> type) {
        if (itemsField != null && itemsOwner == type) {
            return itemsField;
        }
        try {
            Field field = type.getField("items");
            field.setAccessible(true);
            itemsField = field;
            itemsOwner = type;
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Whether the server marked this post as an advert.
     *
     * {@code isAd()} alone is not enough: while the advert payload is loading
     * TikTok leaves it false, and the explicit flag on the model still says
     * the post is sponsored.
     */
    private static boolean isAd(Object item) {
        try {
            Class<?> type = item.getClass();
            Method method = isAdMethod;
            if (method == null || isAdOwner != type) {
                method = type.getMethod("isAd");
                method.setAccessible(true);
                isAdMethod = method;
                isAdOwner = type;
            }
            if (Boolean.TRUE.equals(method.invoke(item))) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall through to the server flag.
        }
        try {
            Class<?> type = item.getClass();
            Field field = isAdField;
            if (field == null || isAdFieldOwner != type) {
                field = type.getField(AD_FLAG);
                field.setAccessible(true);
                isAdField = field;
                isAdFieldOwner = type;
            }
            return Boolean.TRUE.equals(field.get(item));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isLive(Object item) {
        return awemeType(item) == TYPE_LIVE;
    }

    private static boolean isPhoto(Object item) {
        try {
            Class<?> type = item.getClass();
            Method method = photoMethod;
            if (method == null || photoOwner != type) {
                method = type.getMethod("getPhotoModeImageInfo");
                method.setAccessible(true);
                photoMethod = method;
                photoOwner = type;
            }
            return method.invoke(item) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int awemeType(Object item) {
        try {
            Class<?> type = item.getClass();
            Method method = awemeTypeMethod;
            if (method == null || awemeTypeOwner != type) {
                method = type.getMethod("getAwemeType");
                method.setAccessible(true);
                awemeTypeMethod = method;
                awemeTypeOwner = type;
            }
            Object value = method.invoke(item);
            if (value instanceof Integer) {
                return ((Integer) value).intValue();
            }
        } catch (Throwable ignored) {
            // Unknown model; treat it as ordinary content.
        }
        return 0;
    }
}

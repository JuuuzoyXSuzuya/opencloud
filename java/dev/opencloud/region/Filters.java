package dev.opencloud.region;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime content filters, called from TikTok code rewritten by the build.
 *
 * {@link #filterFeed(Object)} is prepended to
 * {@code FeedItemList.getItems()}: it drops advertising and live-stream items
 * from the list before the feed ever sees them.  {@link #hideLiveTab()} is
 * prepended to {@code LiveTabProtocol.enable()}: it makes the "LIVE" top tab
 * disappear.
 *
 * Everything here is reflective and wrapped in {@code catch (Throwable)} on
 * purpose.  A filter must never be able to crash the app, and TikTok renames
 * or moves its internals between releases; an unsupported build then simply
 * keeps the original behaviour.
 */
public final class Filters {
    /** Aweme type of a live-stream card. */
    private static final int TYPE_LIVE = 100;
    /** Aweme type of an advertising item. */
    private static final int TYPE_AD = 101;

    private static Field itemsField;
    private static Class<?> itemsOwner;

    private static Method isAdMethod;
    private static Class<?> isAdOwner;

    private static Method awemeTypeMethod;
    private static Class<?> awemeTypeOwner;

    /** The list instance that has already been checked and needs no rescan. */
    private static Object lastInput;
    /** The filtered list installed into the model, cached to avoid rework. */
    private static Object lastOutput;
    private static int lastSize = -1;

    private Filters() {
    }

    /**
     * Removes ads and live cards from a {@code FeedItemList}.
     *
     * The field is rewritten in place where possible, so callers that already
     * hold the model keep working.  The toggle is read on every call, hence a
     * switch takes effect on the next feed refresh.
     */
    public static void filterFeed(Object feedItemList) {
        try {
            if (feedItemList == null) {
                return;
            }
            boolean ads = Plugins.isEnabled(App.context(), Plugins.NO_ADS);
            boolean live = Plugins.isEnabled(App.context(), Plugins.NO_LIVE);
            if (!ads && !live) {
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
            ArrayList<Object> kept = new ArrayList<Object>(items.size());
            boolean dropped = false;
            for (int index = 0; index < items.size(); index++) {
                Object item = items.get(index);
                if (item == null) {
                    continue;
                }
                if ((ads && isAd(item)) || (live && isLive(item))) {
                    dropped = true;
                } else {
                    kept.add(item);
                }
            }
            if (!dropped) {
                lastInput = items;
                return;
            }
            field.set(feedItemList, kept);
            lastOutput = kept;
            lastSize = kept.size();
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

    private static boolean isAd(Object item) {
        try {
            Class<?> type = item.getClass();
            Method method = isAdMethod;
            if (method == null || isAdOwner != type) {
                method = type.getMethod("isAd");
                isAdMethod = method;
                isAdOwner = type;
            }
            if (Boolean.TRUE.equals(method.invoke(item))) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall back to the aweme type below.
        }
        return awemeType(item) == TYPE_AD;
    }

    private static boolean isLive(Object item) {
        return awemeType(item) == TYPE_LIVE;
    }

    private static int awemeType(Object item) {
        try {
            Class<?> type = item.getClass();
            Method method = awemeTypeMethod;
            if (method == null || awemeTypeOwner != type) {
                method = type.getMethod("getAwemeType");
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

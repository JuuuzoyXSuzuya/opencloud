package dev.opencloud.region;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Hashtags the person does not want to be recommended.
 *
 * <p>The caption is the one place a post always names its own hashtags, so the
 * filter reads the text and matches whole tags: blocking {@code cat} leaves
 * {@code #category} alone.  The list lives in the mod's preferences and is
 * capped, because the check runs on every post of every page.</p>
 */
final class Tags {
    /** Enough tags to describe an interest without slowing the feed down. */
    static final int MAX = 40;

    private static final String KEY = "blocked_tags";

    /** Only this app writes the list, so it is cached until the next edit. */
    private static volatile List<String> cached;

    private Tags() {
    }

    static List<String> all(Context context) {
        List<String> known = cached;
        if (known != null) {
            return known;
        }
        List<String> tags = new ArrayList<String>();
        if (context == null) {
            return tags;
        }
        try {
            SharedPreferences prefs = Prefs.open(context);
            for (String part : prefs.getString(KEY, "").split("\n")) {
                String tag = tidy(part);
                if (tag.length() > 0 && !tags.contains(tag)) {
                    tags.add(tag);
                }
            }
        } catch (Throwable ignored) {
            // An unreadable list simply filters nothing.
        }
        cached = Collections.unmodifiableList(tags);
        return cached;
    }

    /** Adds a tag unless it is already there or the list is full. */
    static boolean add(Context context, String tag) {
        String clean = tidy(tag);
        if (context == null || clean.length() == 0) {
            return false;
        }
        List<String> tags = new ArrayList<String>(all(context));
        if (tags.contains(clean) || tags.size() >= MAX) {
            return false;
        }
        tags.add(clean);
        keep(context, tags);
        return true;
    }

    static void remove(Context context, String tag) {
        if (context == null) {
            return;
        }
        List<String> tags = new ArrayList<String>(all(context));
        if (tags.remove(tidy(tag))) {
            keep(context, tags);
        }
    }

    private static void keep(Context context, List<String> tags) {
        cached = Collections.unmodifiableList(new ArrayList<String>(tags));
        try {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) {
                    out.append('\n');
                }
                out.append(tags.get(i));
            }
            Prefs.open(context).edit().putString(KEY, out.toString()).apply();
        } catch (Throwable ignored) {
            // The in-memory list still works for this run.
        }
    }

    /** A tag as it is compared: lower case, no hash, no spaces. */
    static String tidy(String tag) {
        if (tag == null) {
            return "";
        }
        String out = tag.trim().toLowerCase(Locale.ROOT);
        while (out.startsWith("#")) {
            out = out.substring(1);
        }
        return out.replace(" ", "");
    }

    /** Whether the caption names one of the blocked tags. */
    static boolean blocks(Object post, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        String caption = caption(post);
        if (caption.length() == 0) {
            return false;
        }
        String text = caption.toLowerCase(Locale.ROOT);
        for (int i = 0; i < tags.size(); i++) {
            if (carries(text, tags.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** The caption of a post, read without depending on its exact class. */
    private static String caption(Object post) {
        try {
            Method method = captionMethod(post.getClass());
            Object value = method.invoke(post);
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Method captionMethod;
    private static Class<?> captionOwner;

    private static Method captionMethod(Class<?> type) throws NoSuchMethodException {
        Method known = captionMethod;
        if (known != null && captionOwner == type) {
            return known;
        }
        Method method = type.getMethod("getDesc");
        method.setAccessible(true);
        captionMethod = method;
        captionOwner = type;
        return method;
    }

    /** `#tag` as a whole word: "cat" must not take away "#category". */
    private static boolean carries(String text, String tag) {
        int at = 0;
        while ((at = text.indexOf('#', at)) >= 0) {
            at++;
            int end = at;
            while (end < text.length() && tagChar(text.charAt(end))) {
                end++;
            }
            if (end - at == tag.length() && text.regionMatches(at, tag, 0, tag.length())) {
                return true;
            }
            at = end;
        }
        return false;
    }

    private static boolean tagChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}

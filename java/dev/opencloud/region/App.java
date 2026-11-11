package dev.opencloud.region;

import android.content.Context;

/**
 * Finds the process-wide application object without linking against hidden
 * framework classes.  The lookups are attempted in the order that has proven
 * most portable; any failure simply falls back to the next one.
 */
final class App {
    private static volatile Context cached;

    private App() {
    }

    static Context context() {
        Context probe = cached;
        if (probe != null) {
            return probe;
        }
        synchronized (App.class) {
            if (cached == null) {
                cached = discover();
                if (cached != null) {
                    Logs.install(cached);
                }
            }
            return cached;
        }
    }

    private static Context discover() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            try {
                Object value = activityThread.getMethod("currentApplication").invoke(null);
                if (value instanceof Context) {
                    return (Context) value;
                }
            } catch (Throwable ignored) {
                // Try the longer route below.
            }
            try {
                Object thread = activityThread.getMethod("currentActivityThread").invoke(null);
                if (thread != null) {
                    Object value = activityThread.getMethod("getApplication").invoke(thread);
                    if (value instanceof Context) {
                        return (Context) value;
                    }
                }
            } catch (Throwable ignored) {
                // Fall through to AppGlobals.
            }
        } catch (Throwable ignored) {
            // Fall through to AppGlobals.
        }
        try {
            Class<?> globals = Class.forName("android.app.AppGlobals");
            Object value = globals.getMethod("getInitialApplication").invoke(null);
            if (value instanceof Context) {
                return (Context) value;
            }
        } catch (Throwable ignored) {
            // No context is available; defaults still keep the mod usable.
        }
        return null;
    }
}

package dev.opencloud.region;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Small persistent log for the mod.
 *
 * <p>Everything is written to a single file in the app's private storage and
 * shown from the settings screen. The file is capped so a misbehaving hook
 * cannot fill the disk. Logging never throws: diagnostics must not break the
 * app they are meant to diagnose.</p>
 */
final class Logs {
    private static final String NAME = "opencloud.log";
    private static final long LIMIT = 256 * 1024;
    private static final Object LOCK = new Object();

    private static volatile File file;
    private static volatile boolean handlerInstalled;
    private static volatile Context appContext;

    private Logs() {
    }

    /** Registers a handler that records uncaught exceptions before the crash. */
    static void install(Context context) {
        if (context == null || handlerInstalled) {
            return;
        }
        handlerInstalled = true;
        Context application = context.getApplicationContext();
        appContext = application != null ? application : context;
        file = new File(context.getFilesDir(), NAME);
        write("app", "mod started");
        try {
            final Thread.UncaughtExceptionHandler previous =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable error) {
                    write("crash/" + thread.getName(), error);
                    export();
                    if (previous != null) {
                        previous.uncaughtException(thread, error);
                    }
                }
            });
        } catch (Throwable ignored) {
            // Without a handler the mod still works.
        }
    }

    /**
     * Copies the log to {@code Download/OpenCloud/opencloud.log}.
     *
     * <p>The private file cannot be pulled over FTP and adb is not always
     * available, so a crash exports itself where a file manager can reach it.
     * Returns the public path on success, {@code null} otherwise.</p>
     */
    static String export() {
        Context context = appContext;
        File target = file;
        if (context == null || target == null || !target.isFile()) {
            return null;
        }
        try {
            byte[] data = readAll(target);
            return Downloads.put(context, NAME, data);
        } catch (Throwable error) {
            return null;
        }
    }

    /** Kept apart so older ART never has to verify the Android 10 API. */
    private static final class Downloads {
        static String put(Context context, String name, byte[] data) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return putLegacy(name, data);
            }
            OutputStream output = null;
            try {
                ContentResolver resolver = context.getContentResolver();
                String folder = Environment.DIRECTORY_DOWNLOADS + "/OpenCloud/";
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
                try {
                    resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                                    + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                            new String[]{name, folder});
                } catch (Throwable ignored) {
                    // Duplicates are harmless.
                }
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return null;
                }
                output = resolver.openOutputStream(uri, "wt");
                if (output == null) {
                    return null;
                }
                output.write(data);
                output.flush();
                return folder + name;
            } catch (Throwable error) {
                return null;
            } finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (Throwable ignored) {
                        // Nothing useful to do here.
                    }
                }
            }
        }

        @SuppressWarnings("deprecation")
        private static String putLegacy(String name, byte[] data) {
            FileOutputStream output = null;
            try {
                File folder = new File(
                        Environment.getExternalStorageDirectory(), "Download/OpenCloud");
                if (!folder.isDirectory() && !folder.mkdirs()) {
                    return null;
                }
                File target = new File(folder, name);
                output = new FileOutputStream(target, false);
                output.write(data);
                output.flush();
                return target.getAbsolutePath();
            } catch (Throwable error) {
                return null;
            } finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (Throwable ignored) {
                        // Nothing useful to do here.
                    }
                }
            }
        }
    }


    static void write(String tag, String message) {
        append(tag + ": " + message);
    }

    static void write(String tag, Throwable error) {
        StringWriter buffer = new StringWriter();
        PrintWriter printer = new PrintWriter(buffer);
        try {
            error.printStackTrace(printer);
            printer.flush();
        } catch (Throwable ignored) {
            // Use whatever was written.
        }
        append(tag + ":\n" + buffer.toString());
    }

    static String read() {
        File target = file;
        if (target == null || !target.isFile()) {
            return "";
        }
        try {
            byte[] data = readAll(target);
            return new String(data, "UTF-8");
        } catch (Throwable error) {
            return "Не удалось прочитать лог: " + error;
        }
    }

    static void clear() {
        File target = file;
        if (target != null) {
            try {
                target.delete();
            } catch (Throwable ignored) {
                // Nothing useful to do here.
            }
        }
    }

    private static void append(String text) {
        File target = file;
        if (target == null) {
            return;
        }
        synchronized (LOCK) {
            FileOutputStream output = null;
            try {
                if (target.length() > LIMIT) {
                    target.delete();
                }
                output = new FileOutputStream(target, true);
                String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
                        .format(new Date()) + "  " + text + "\n";
                output.write(line.getBytes("UTF-8"));
                output.flush();
            } catch (Throwable ignored) {
                // Diagnostics are best effort.
            } finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (Throwable ignored) {
                        // Nothing useful to do here.
                    }
                }
            }
        }
    }

    private static byte[] readAll(File target) throws Exception {
        java.io.FileInputStream input = new java.io.FileInputStream(target);
        try {
            int size = (int) Math.min(target.length(), LIMIT);
            byte[] data = new byte[size];
            int offset = 0;
            while (offset < size) {
                int read = input.read(data, offset, size - offset);
                if (read <= 0) {
                    break;
                }
                offset += read;
            }
            if (offset == size) {
                return data;
            }
            byte[] shorter = new byte[offset];
            System.arraycopy(data, 0, shorter, 0, offset);
            return shorter;
        } finally {
            try {
                input.close();
            } catch (Throwable ignored) {
                // Nothing useful to do here.
            }
        }
    }
}

package dev.opencloud.region;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Installed .oc plugins.  A plugin is a small JSON preset, not executable
 * code: it can switch the built-in runtime plugins and set the region.  The
 * file is decrypted on import and the resulting JSON is stored in the app's
 * private files directory, so only this process can read it.
 */
final class PluginStore {
    private static final String TAG = "OpenCloudPlugin";
    private static final String DIR = "opencloud/plugins";

    static final class Plugin {
        final String id;
        final String name;
        final int version;
        final String author;
        final String description;
        final JSONArray actions;
        final File file;

        Plugin(String id, String name, int version, String author,
               String description, JSONArray actions, File file) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.actions = actions;
            this.file = file;
        }
    }

    private PluginStore() {
    }

    static Plugin importFrom(Context context, InputStream input) throws Exception {
        String json = PluginCrypto.decode(input);
        JSONObject root = new JSONObject(json);
        String id = root.optString("id", "").trim();
        String name = root.optString("name", "").trim();
        if (!id.matches("[a-zA-Z0-9._-]{1,48}")) {
            throw new IllegalArgumentException("invalid plugin id");
        }
        if (name.length() == 0) {
            throw new IllegalArgumentException("plugin name is empty");
        }
        JSONArray actions = root.optJSONArray("actions");
        if (actions == null) {
            actions = new JSONArray();
        }
        int version = root.optInt("version", 1);
        String author = root.optString("author", "");
        String description = root.optString("description", "");

        File dir = directory(context);
        File target = new File(dir, id + ".json");
        FileOutputStream stream = new FileOutputStream(target);
        Writer writer = new OutputStreamWriter(stream, "UTF-8");
        try {
            writer.write(root.toString(2));
        } finally {
            writer.close();
        }
        return new Plugin(id, name, version, author, description, actions, target);
    }

    static List<Plugin> installed(Context context) {
        List<Plugin> result = new ArrayList<Plugin>();
        File[] files = directory(context).listFiles();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (!file.getName().endsWith(".json")) {
                continue;
            }
            try {
                Plugin plugin = read(file);
                if (plugin != null) {
                    result.add(plugin);
                }
            } catch (Throwable error) {
                Log.w(TAG, "cannot read " + file.getName(), error);
            }
        }
        Collections.sort(result, new Comparator<Plugin>() {
            @Override
            public int compare(Plugin left, Plugin right) {
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        return result;
    }

    static boolean delete(Context context, Plugin plugin) {
        return plugin.file.delete();
    }

    static void deleteAll(Context context) {
        File[] files = directory(context).listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.getName().endsWith(".json")) {
                file.delete();
            }
        }
    }

    /** Applies every supported action; returns how many ran. */
    static int apply(Context context, Plugin plugin) {
        int applied = 0;
        for (int i = 0; i < plugin.actions.length(); i++) {
            JSONObject action = plugin.actions.optJSONObject(i);
            if (action == null) {
                continue;
            }
            String type = action.optString("type", "");
            if ("dark_theme".equals(type)) {
                boolean enabled = action.optBoolean("enabled", true);
                ThemePlugin.setForcedMode(context,
                        enabled ? ThemePlugin.DARK : ThemePlugin.FOLLOW_SYSTEM);
                ThemePlugin.refresh();
                applied++;
            } else if ("plugin".equals(type)) {
                String id = action.optString("id", "");
                if (id.length() > 0) {
                    Plugins.setEnabled(context, id, action.optBoolean("enabled", true));
                    applied++;
                }
            } else if ("log".equals(type)) {
                String message = action.optString("message", "");
                if (message.length() > 0) {
                    Logs.write(action.optString("tag", "plugin"), message);
                    applied++;
                }
            } else if ("region".equals(type)) {
                String country = action.optString("country", "");
                String operator = action.optString("operator", "");
                Profile profile = Atlas.find(country, operator);
                if (profile == null) {
                    profile = Atlas.find(country, null);
                }
                if (profile != null) {
                    Config.store(context, action.optBoolean("enabled", true),
                            profile.code(), profile.numeric());
                    applied++;
                }
            }
        }
        return applied;
    }

    private static Plugin read(File file) throws Exception {
        InputStream input = new FileInputStream(file);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        } finally {
            input.close();
        }
        JSONObject root = new JSONObject(new String(buffer.toByteArray(), "UTF-8"));
        String id = root.optString("id", "");
        if (id.length() == 0) {
            return null;
        }
        JSONArray actions = root.optJSONArray("actions");
        return new Plugin(id, root.optString("name", id), root.optInt("version", 1),
                root.optString("author", ""), root.optString("description", ""),
                actions == null ? new JSONArray() : actions, file);
    }

    private static File directory(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "cannot create " + dir);
        }
        return dir;
    }
}

package org.levimc.launcher.core.mods;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Bundles SystemMods.levipack (BedrockTools engine) inside the APK and installs it
 * into the version mods folder. Config is written where the stock .so reads it:
 *   /sdcard/games/BedrockTools/config.json
 * plus a few mirrors so PL configDir layouts still work.
 */
public final class SystemModsInstaller {
    private static final String TAG = "SystemModsInstaller";
    private static final String ASSET = "system_mods/SystemMods.levipack";
    public static final String MOD_ID = "SystemMods";

    private SystemModsInstaller() {}

    public static File modRoot(ModManager modManager) {
        if (modManager == null || modManager.getCurrentVersion() == null
                || modManager.getCurrentVersion().modsDir == null) {
            return null;
        }
        return new File(modManager.getCurrentVersion().modsDir, MOD_ID);
    }

    public static void ensureInstalled(Context context, ModManager modManager) {
        if (context == null || modManager == null) return;
        File modsDir;
        try {
            if (modManager.getCurrentVersion() == null
                    || modManager.getCurrentVersion().modsDir == null) {
                return;
            }
            modsDir = modManager.getCurrentVersion().modsDir;
        } catch (Throwable t) {
            Log.w(TAG, "No modsDir", t);
            return;
        }
        try {
            modsDir.mkdirs();
            File root = new File(modsDir, MOD_ID);
            File entry = new File(root, "libBedrockTools.so");
            if (!(entry.isFile() && entry.length() > 0)) {
                installPack(context.getAssets(), modsDir, root);
            }
            // Always keep SystemMods enabled
            modManager.setModEnabled(MOD_ID, true);
            // Also bundle AirSwing as its own native mod folder
            try {
                installAirSwing(context.getAssets(), modsDir, modManager);
            } catch (Throwable t) {
                Log.w(TAG, "AirSwing install skipped", t);
            }
            modManager.reloadMods();
            // Ensure No Disconnect is always on in config
            setModuleEnabled(context, modManager, "No Disconnect", true);
            ensureConfigDirs(context, modManager);
            Log.i(TAG, "SystemMods ready at " + root.getAbsolutePath());
        } catch (Throwable t) {
            Log.e(TAG, "ensureInstalled failed", t);
        }
    }

    private static void ensureConfigDirs(Context context, ModManager modManager) {
        for (File f : configFiles(context, modManager)) {
            try {
                //noinspection ResultOfMethodCallIgnored
                f.getParentFile().mkdirs();
            } catch (Throwable ignored) {}
        }
    }

    /** All locations the stock SO / PL may read. */
    public static List<File> configFiles(Context context, ModManager modManager) {
        List<File> out = new ArrayList<>();
        // Stock BedrockTools default (confirmed in libBedrockTools.so strings)
        out.add(new File("/sdcard/games/BedrockTools/config.json"));
        try {
            File ext = Environment.getExternalStorageDirectory();
            if (ext != null) {
                out.add(new File(ext, "games/BedrockTools/config.json"));
            }
        } catch (Throwable ignored) {}
        File root = modRoot(modManager);
        if (root != null) {
            out.add(new File(root, "config.json"));
            out.add(new File(root, "config/config.json"));
            out.add(new File(root, "data/config.json"));
        }
        if (context != null) {
            out.add(new File(context.getFilesDir(), "systemclient/config.json"));
            out.add(new File(context.getFilesDir(), "systemmods/config.json"));
            File efd = context.getExternalFilesDir(null);
            if (efd != null) {
                out.add(new File(efd, "systemclient/config.json"));
                out.add(new File(efd, "games/BedrockTools/config.json"));
            }
        }
        return out;
    }

    /** Enable/disable a native module by its BedrockTools display name. */
    public static void setModuleEnabled(Context context, ModManager modManager,
                                        String moduleName, boolean enabled) {
        if (moduleName == null || moduleName.isEmpty()) return;
        try {
            for (File cfg : configFiles(context, modManager)) {
                writeModuleFlag(cfg, moduleName, enabled);
            }
        } catch (Throwable t) {
            Log.e(TAG, "setModuleEnabled failed", t);
        }
    }

    /** Merge a setting key into a module's config object. */
    public static void setModuleSetting(Context context, ModManager modManager,
                                        String moduleName, String key, Object value) {
        if (moduleName == null || key == null) return;
        try {
            for (File cfg : configFiles(context, modManager)) {
                writeModuleSetting(cfg, moduleName, key, value);
            }
        } catch (Throwable t) {
            Log.e(TAG, "setModuleSetting failed", t);
        }
    }

    public static boolean isModuleEnabled(Context context, ModManager modManager, String moduleName) {
        try {
            for (File cfg : configFiles(context, modManager)) {
                if (!cfg.isFile() || cfg.length() == 0) continue;
                String text = new String(java.nio.file.Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
                if (text.isEmpty()) continue;
                JSONObject root = new JSONObject(text);
                JSONObject modules = root.optJSONObject("Modules");
                if (modules == null) continue;
                JSONObject mod = modules.optJSONObject(moduleName);
                if (mod != null && mod.has("masterEnabled")) {
                    return mod.optBoolean("masterEnabled", false);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void writeModuleFlag(File cfg, String moduleName, boolean enabled) throws Exception {
        //noinspection ResultOfMethodCallIgnored
        cfg.getParentFile().mkdirs();
        JSONObject root = readJson(cfg);
        JSONObject modules = root.optJSONObject("Modules");
        if (modules == null) modules = new JSONObject();
        JSONObject mod = modules.optJSONObject(moduleName);
        if (mod == null) mod = new JSONObject();
        mod.put("masterEnabled", enabled);
        mod.put("keybindActive", true);
        modules.put(moduleName, mod);
        root.put("Modules", modules);
        atomicWrite(cfg, root);
    }

    private static void writeModuleSetting(File cfg, String moduleName, String key, Object value)
            throws Exception {
        //noinspection ResultOfMethodCallIgnored
        cfg.getParentFile().mkdirs();
        JSONObject root = readJson(cfg);
        JSONObject modules = root.optJSONObject("Modules");
        if (modules == null) modules = new JSONObject();
        JSONObject mod = modules.optJSONObject(moduleName);
        if (mod == null) mod = new JSONObject();
        if (value instanceof Boolean) mod.put(key, (Boolean) value);
        else if (value instanceof Integer) mod.put(key, (Integer) value);
        else if (value instanceof Long) mod.put(key, (Long) value);
        else if (value instanceof Double) mod.put(key, (Double) value);
        else if (value instanceof Float) mod.put(key, (double) (Float) value);
        else mod.put(key, String.valueOf(value));
        modules.put(moduleName, mod);
        root.put("Modules", modules);
        atomicWrite(cfg, root);
    }

    private static JSONObject readJson(File cfg) throws Exception {
        if (cfg.isFile() && cfg.length() > 0) {
            String text = new String(java.nio.file.Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
            if (!text.isEmpty()) return new JSONObject(text);
        }
        return new JSONObject();
    }

    private static void atomicWrite(File cfg, JSONObject root) throws Exception {
        File tmp = new File(cfg.getAbsolutePath() + ".tmp");
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        //noinspection ResultOfMethodCallIgnored
        if (!tmp.renameTo(cfg)) {
            try (OutputStream os = new FileOutputStream(cfg)) {
                os.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        cfg.setLastModified(System.currentTimeMillis());
    }

    private static void installPack(AssetManager am, File modsDir, File modRoot) throws Exception {
        File tmpZip = new File(modsDir, MOD_ID + ".tmp.levipack");
        try {
            copyAsset(am, ASSET, tmpZip);
            if (modRoot.exists()) deleteRecursively(modRoot);
            //noinspection ResultOfMethodCallIgnored
            modRoot.mkdirs();
            try (ZipFile zf = new ZipFile(tmpZip)) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    File out = new File(modRoot, e.getName());
                    if (e.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        out.mkdirs();
                        continue;
                    }
                    //noinspection ResultOfMethodCallIgnored
                    out.getParentFile().mkdirs();
                    try (InputStream in = zf.getInputStream(e); OutputStream os = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) >= 0) os.write(buf, 0, n);
                    }
                }
            }
            normalizeManifest(modRoot);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmpZip.delete();
        }
    }

    private static void normalizeManifest(File modRoot) {
        File mf = new File(modRoot, "manifest.json");
        try {
            JsonObject obj;
            if (mf.isFile()) {
                String text = new String(java.nio.file.Files.readAllBytes(mf.toPath()), StandardCharsets.UTF_8);
                obj = JsonParser.parseString(text).getAsJsonObject();
            } else {
                obj = new JsonObject();
            }
            obj.addProperty("type", "preload-native");
            obj.addProperty("name", "SystemMods");
            obj.addProperty("author", "SystemClient");
            if (!obj.has("entry") || obj.get("entry").getAsString().isEmpty()) {
                obj.addProperty("entry", "libBedrockTools.so");
            }
            try (OutputStream os = new FileOutputStream(mf)) {
                os.write(obj.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "normalizeManifest: " + e.getMessage());
        }
    }


    private static void installAirSwing(AssetManager am, File modsDir, ModManager modManager)
            throws Exception {
        final String assetName = "libAirSwing.so";
        final String id = "AirSwing";
        File modRoot = new File(modsDir, id);
        File soFile = new File(modRoot, assetName);
        if (!(soFile.isFile() && soFile.length() > 0)) {
            //noinspection ResultOfMethodCallIgnored
            modRoot.mkdirs();
            copyAsset(am, "system_mods/" + assetName, soFile);
            File mf = new File(modRoot, "manifest.json");
            String json = "{"
                    + "\"type\":\"preload-native\","
                    + "\"name\":\"AirSwing\","
                    + "\"author\":\"SystemClient\","
                    + "\"description\":\"Air swing animation while airborne.\","
                    + "\"version\":\"1.0.0\","
                    + "\"entry\":\"libAirSwing.so\""
                    + "}";
            try (OutputStream os = new FileOutputStream(mf)) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            Log.i(TAG, "Installed AirSwing at " + modRoot.getAbsolutePath());
        }
        modManager.setModEnabled(id, true);
    }

    private static void copyAsset(AssetManager am, String assetPath, File dest) throws Exception {
        //noinspection ResultOfMethodCallIgnored
        dest.getParentFile().mkdirs();
        try (InputStream in = am.open(assetPath); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursively(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}

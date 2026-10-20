package org.levimc.launcher.core.mods;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Ships SystemMods.levipack inside the APK and installs it into the current
 * version modsDir so Levi loads it like any native mod — without the user
 * importing a plugin. SystemDLC drives per-module toggles via config.json.
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
            if (entry.isFile() && entry.length() > 0) {
                modManager.setModEnabled(MOD_ID, true);
                return;
            }
            installPack(context.getAssets(), modsDir, root);
            modManager.setModEnabled(MOD_ID, true);
            modManager.reloadMods();
            Log.i(TAG, "SystemMods installed at " + root.getAbsolutePath());
        } catch (Throwable t) {
            Log.e(TAG, "ensureInstalled failed", t);
        }
    }

    /** Write masterEnabled for a native module name (e.g. "Fullbright"). */
    public static void setModuleEnabled(Context context, ModManager modManager,
                                        String moduleDisplayName, boolean enabled) {
        if (moduleDisplayName == null || moduleDisplayName.isEmpty()) return;
        try {
            File root = modRoot(modManager);
            File[] candidates;
            if (root != null) {
                candidates = new File[]{
                        new File(root, "config/config.json"),
                        new File(root, "config.json"),
                        new File(root, "data/config.json")
                };
            } else {
                candidates = new File[0];
            }
            // Always also write app-private mirror
            File mirror = new File(context.getFilesDir(), "systemmods/config.json");
            writeModuleFlag(mirror, moduleDisplayName, enabled);
            for (File cfg : candidates) {
                writeModuleFlag(cfg, moduleDisplayName, enabled);
            }
        } catch (Throwable t) {
            Log.e(TAG, "setModuleEnabled failed", t);
        }
    }

    private static void writeModuleFlag(File cfg, String moduleDisplayName, boolean enabled)
            throws Exception {
        //noinspection ResultOfMethodCallIgnored
        cfg.getParentFile().mkdirs();
        JSONObject root;
        if (cfg.isFile() && cfg.length() > 0) {
            String text = new String(java.nio.file.Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
            root = text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } else {
            root = new JSONObject();
        }
        JSONObject modules = root.optJSONObject("Modules");
        if (modules == null) modules = new JSONObject();
        JSONObject mod = modules.optJSONObject(moduleDisplayName);
        if (mod == null) mod = new JSONObject();
        mod.put("masterEnabled", enabled);
        mod.put("keybindActive", true);
        modules.put(moduleDisplayName, mod);
        root.put("Modules", modules);
        File tmp = new File(cfg.getAbsolutePath() + ".tmp");
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(cfg);
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

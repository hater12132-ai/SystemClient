package org.levimc.launcher.core.mods;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Copies SystemClient bundled .levipack / .so from assets into the version mods
 * directory so they appear in the launcher Mods list and, once loaded by the
 * preloader, as External modules in the in-game mod menu (same path as
 * Quick Drop / FPS Display for native packs that register via PL_REGISTER_MOD).
 */
public final class SystemBundledMods {
    private static final String TAG = "SystemBundledMods";
    private static final String ASSET_DIR = "system_mods";
    private static final String MARKER = ".system_bundled_v1";

    private SystemBundledMods() {}

    public static void ensureInstalled(Context context, ModManager modManager) {
        if (context == null || modManager == null) return;
        File modsDir = null;
        try {
            // ModManager keeps modsDir private; re-derive from current version.
            if (modManager.getCurrentVersion() == null
                    || modManager.getCurrentVersion().modsDir == null) {
                return;
            }
            modsDir = modManager.getCurrentVersion().modsDir;
        } catch (Throwable t) {
            Log.w(TAG, "No current version modsDir", t);
            return;
        }

        try {
            modsDir.mkdirs();
            File marker = new File(modsDir, MARKER);
            // Always re-check missing packs (marker only skips full re-extract of all)
            AssetManager am = context.getAssets();
            String[] names;
            try {
                names = am.list(ASSET_DIR);
            } catch (Exception e) {
                Log.e(TAG, "list assets failed", e);
                return;
            }
            if (names == null || names.length == 0) {
                Log.w(TAG, "No bundled mods in assets/" + ASSET_DIR);
                return;
            }

            boolean any = false;
            for (String name : names) {
                if (name == null || name.startsWith(".")) continue;
                try {
                    if (name.toLowerCase().endsWith(".so")) {
                        any |= installSo(am, modsDir, name, modManager);
                    } else if (name.toLowerCase().endsWith(".levipack")
                            || name.toLowerCase().endsWith(".zip")) {
                        any |= installPack(am, modsDir, name, modManager);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to install bundled mod: " + name, t);
                }
            }

            if (any || !marker.exists()) {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    marker.createNewFile();
                } catch (Exception ignored) {}
                modManager.reloadMods();
            }
            Log.i(TAG, "Bundled mods ensureInstalled done (anyChange=" + any + ")");
        } catch (Throwable t) {
            Log.e(TAG, "ensureInstalled failed", t);
        }
    }

    private static boolean installSo(AssetManager am, File modsDir, String assetName,
                                     ModManager modManager) throws Exception {
        String display = assetName.replaceFirst("^lib", "").replaceFirst("\\.so$", "");
        String id = sanitizeId(display.isEmpty() ? "AirSwing" : display);
        File modRoot = new File(modsDir, id);
        File soFile = new File(modRoot, assetName);
        if (soFile.isFile() && soFile.length() > 0) {
            modManager.setModEnabled(id, true);
            return false;
        }
        if (!modRoot.exists() && !modRoot.mkdirs()) {
            throw new Exception("mkdir failed " + modRoot);
        }
        copyAsset(am, ASSET_DIR + "/" + assetName, soFile);
        writeMinimalManifest(modRoot, display.isEmpty() ? "AirSwing" : display, assetName);
        modManager.setModEnabled(id, true);
        Log.i(TAG, "Installed SO mod " + id);
        return true;
    }

    private static boolean installPack(AssetManager am, File modsDir, String assetName,
                                       ModManager modManager) throws Exception {
        String base = assetName.replaceAll("\\.(levipack|zip)$", "");
        String id = sanitizeId(base);

        File modRoot = new File(modsDir, id);
        File entryProbe = findAnySo(modRoot);
        if (modRoot.isDirectory() && entryProbe != null) {
            modManager.setModEnabled(id, true);
            return false;
        }

        File tmpZip = File.createTempFile("clarity_", ".levipack", modsDir);
        try {
            copyAsset(am, ASSET_DIR + "/" + assetName, tmpZip);
            if (modRoot.exists()) {
                deleteRecursively(modRoot);
            }
            if (!modRoot.mkdirs()) {
                throw new Exception("mkdir failed " + modRoot);
            }
            unzipTo(tmpZip, modRoot);
            normalizeManifest(modRoot);
            // Nested pack (FrameGen style): if entry missing, try nested .levipack
            if (findAnySo(modRoot) == null) {
                File[] nested = modRoot.listFiles((d, n) ->
                        n.toLowerCase().endsWith(".levipack") || n.toLowerCase().endsWith(".zip"));
                if (nested != null && nested.length == 1) {
                    File nestedDir = new File(modRoot, "_nested");
                    nestedDir.mkdirs();
                    unzipTo(nested[0], nestedDir);
                    // Move nested contents up
                    File[] children = nestedDir.listFiles();
                    if (children != null) {
                        for (File c : children) {
                            File dest = new File(modRoot, c.getName());
                            if (!c.renameTo(dest)) {
                                copyFile(c, dest);
                            }
                        }
                    }
                    deleteRecursively(nestedDir);
                    //noinspection ResultOfMethodCallIgnored
                    nested[0].delete();
                    normalizeManifest(modRoot);
                }
            }
            // build/lib*.so layout
            promoteBuildSo(modRoot);
            normalizeManifest(modRoot);
            modManager.setModEnabled(id, true);
            Log.i(TAG, "Installed pack mod " + id);
            return true;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmpZip.delete();
        }
    }

    private static void promoteBuildSo(File modRoot) {
        File build = new File(modRoot, "build");
        if (!build.isDirectory()) return;
        File[] sos = build.listFiles((d, n) -> n.toLowerCase().endsWith(".so"));
        if (sos == null) return;
        for (File so : sos) {
            File dest = new File(modRoot, so.getName());
            if (!dest.exists()) {
                //noinspection ResultOfMethodCallIgnored
                so.renameTo(dest);
            }
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
            if (!obj.has("entry") || obj.get("entry").getAsString().isEmpty()) {
                File so = findAnySo(modRoot);
                if (so != null) obj.addProperty("entry", so.getName());
            }
            if (!obj.has("name") || obj.get("name").getAsString().isEmpty()) {
                obj.addProperty("name", modRoot.getName());
            }
            try (OutputStream os = new FileOutputStream(mf)) {
                os.write(obj.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "normalizeManifest: " + e.getMessage());
        }
    }

    private static void writeMinimalManifest(File modRoot, String name, String entry) throws Exception {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "preload-native");
        obj.addProperty("name", name);
        obj.addProperty("entry", entry);
        obj.addProperty("version", "1.0.0");
        File mf = new File(modRoot, "manifest.json");
        try (OutputStream os = new FileOutputStream(mf)) {
            os.write(obj.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static File findAnySo(File root) {
        if (root == null || !root.isDirectory()) return null;
        File[] files = root.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".so")) return f;
        }
        File build = new File(root, "build");
        if (build.isDirectory()) {
            File[] b = build.listFiles((d, n) -> n.toLowerCase().endsWith(".so"));
            if (b != null && b.length > 0) return b[0];
        }
        return null;
    }

    private static String sanitizeId(String name) {
        String s = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (s.isEmpty()) s = "bundled_mod";
        return s;
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

    private static void unzipTo(File zipFile, File destDir) throws Exception {
        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                File out = new File(destDir, e.getName());
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
    }

    private static void copyFile(File src, File dest) throws Exception {
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
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

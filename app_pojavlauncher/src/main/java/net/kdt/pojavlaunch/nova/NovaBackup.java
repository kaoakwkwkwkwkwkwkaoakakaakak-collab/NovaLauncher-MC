package net.kdt.pojavlaunch.nova;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import net.kdt.pojavlaunch.Tools;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
public final class NovaBackup {

    private static final String TAG = "NovaBackup";

    private static final String[] BACKUP_DIRS = {
            "instances",
            "controlmap",
            "custom_skins"
    };

    private static final String[] BACKUP_FILES = {
            "launcher_profiles.json",
            "custom_env.txt"
    };

    public interface ProgressCallback {
        void onProgress(String currentFile);
    }

    private NovaBackup() {}

    public static void export(Context context, Uri destination, ProgressCallback callback)
            throws IOException {
        File home = new File(Tools.DIR_GAME_HOME);
        OutputStream raw = context.getContentResolver().openOutputStream(destination);
        if (raw == null) throw new IOException("Cannot open destination");
        try (ZipOutputStream zip = new ZipOutputStream(raw)) {
            zip.setLevel(6);
            for (String dirName : BACKUP_DIRS) {
                File dir = new File(home, dirName);
                if (dir.isDirectory()) zipDirectory(zip, dir, dirName, callback);
            }
            for (String fileName : BACKUP_FILES) {
                File file = new File(home, fileName);
                if (file.isFile()) zipFile(zip, file, fileName, callback);
            }
            zipPreferences(zip);
        }
    }

    public static void restore(Context context, Uri source, ProgressCallback callback)
            throws IOException {
        File home = new File(Tools.DIR_GAME_HOME);
        InputStream raw = context.getContentResolver().openInputStream(source);
        if (raw == null) throw new IOException("Cannot open backup");
        String canonicalHome = home.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("nova_preferences.properties")) {
                    restorePreferences(zip);
                    zip.closeEntry();
                    continue;
                }
                File target = new File(home, name);
                if (!target.getCanonicalPath().startsWith(canonicalHome)) {
                    Log.w(TAG, "Skipping unsafe entry " + name);
                    zip.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        throw new IOException("Cannot create " + target);
                    }
                } else {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Cannot create " + parent);
                    }
                    if (callback != null) callback.onProgress(name);
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) out.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static void zipDirectory(ZipOutputStream zip, File dir, String path,
                                     ProgressCallback callback) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String childPath = path + "/" + child.getName();
            if (child.isDirectory()) {
                zipDirectory(zip, child, childPath, callback);
            } else {
                zipFile(zip, child, childPath, callback);
            }
        }
    }

    private static void zipFile(ZipOutputStream zip, File file, String path,
                                ProgressCallback callback) throws IOException {
        if (callback != null) callback.onProgress(path);
        zip.putNextEntry(new ZipEntry(path));
        byte[] buffer = new byte[8192];
        try (FileInputStream in = new FileInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }

    private static void zipPreferences(ZipOutputStream zip) throws IOException {
        android.content.SharedPreferences prefs =
                net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
        if (prefs == null) return;
        java.util.Properties properties = new java.util.Properties();
        for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value != null) properties.setProperty(entry.getKey(), String.valueOf(value));
        }
        properties.remove(NovaPrefs.KEY_API);
        zip.putNextEntry(new ZipEntry("nova_preferences.properties"));
        properties.store(zip, "NovaLauncher settings");
        zip.closeEntry();
    }

    private static void restorePreferences(InputStream in) {
        try {
            java.util.Properties properties = new java.util.Properties();
            properties.load(in);
            android.content.SharedPreferences prefs =
                    net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
            if (prefs == null) return;
            android.content.SharedPreferences.Editor editor = prefs.edit();
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if ("true".equals(value) || "false".equals(value)) {
                    editor.putBoolean(key, Boolean.parseBoolean(value));
                } else {
                    try {
                        editor.putInt(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        editor.putString(key, value);
                    }
                }
            }
            editor.apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to restore preferences", e);
        }
    }

    public static String defaultFileName() {
        return "NovaLauncher-backup-"
                + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                .format(new java.util.Date()) + ".zip";
    }
}

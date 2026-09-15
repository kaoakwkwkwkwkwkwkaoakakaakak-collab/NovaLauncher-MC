package net.kdt.pojavlaunch.nova;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class NovaShizuku {

    private static final String TAG = "NovaShizuku";
    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String SHIZUKU_CLASS = "rikka.shizuku.Shizuku";
    private static final int REQUEST_CODE = 5122;

    private NovaShizuku() {}

    public static final class Result {
        public final boolean ok;
        public final String output;

        Result(boolean ok, String output) {
            this.ok = ok;
            this.output = output == null ? "" : output;
        }
    }

    @Nullable
    private static Class<?> shizukuClass() {
        try {
            return Class.forName(SHIZUKU_CLASS);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static boolean isInstalled(Context context) {
        if (context == null) return false;
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return isBinderAlive();
        }
    }

    public static boolean isBinderAlive() {
        Class<?> shizuku = shizukuClass();
        if (shizuku == null) return false;
        try {
            Method m = shizuku.getMethod("pingBinder");
            Object result = m.invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        if (!isBinderAlive()) return false;
        Class<?> shizuku = shizukuClass();
        if (shizuku == null) return false;
        try {
            Method isPreV11 = shizuku.getMethod("isPreV11");
            if (Boolean.TRUE.equals(isPreV11.invoke(null))) return false;
            Method check = shizuku.getMethod("checkSelfPermission");
            Object code = check.invoke(null);
            return code instanceof Integer && (Integer) code == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            Log.w(TAG, "checkSelfPermission failed", t);
            return false;
        }
    }

    public static void requestPermission() {
        Class<?> shizuku = shizukuClass();
        if (shizuku == null || !isBinderAlive()) return;
        try {
            Method request = shizuku.getMethod("requestPermission", int.class);
            request.invoke(null, REQUEST_CODE);
        } catch (Throwable t) {
            Log.w(TAG, "requestPermission failed", t);
        }
    }

    public static boolean isReady() {
        return NovaPrefs.isOn(NovaPrefs.KEY_SHIZUKU_ENABLE) && hasPermission();
    }

    public static Result exec(String... command) {
        if (!hasPermission()) return new Result(false, "Shizuku unavailable");
        Class<?> shizuku = shizukuClass();
        if (shizuku == null) return new Result(false, "Shizuku unavailable");
        Process process = null;
        try {
            Method newProcess = shizuku.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
            Object raw = newProcess.invoke(null, command, null, null);
            if (!(raw instanceof Process)) return new Result(false, "Unexpected process type");
            process = (Process) raw;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            }
            int exit = process.waitFor();
            return new Result(exit == 0, sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "exec failed: " + java.util.Arrays.toString(command), t);
            return new Result(false, String.valueOf(t.getMessage()));
        } finally {
            if (process != null) process.destroy();
        }
    }

    public static boolean disablePhantomProcessKiller() {
        if (!isReady()) return false;
        boolean ok = true;
        ok &= exec("settings", "put", "global", "settings_enable_monitor_phantom_procs", "false").ok;
        ok &= exec("device_config", "set_sync_disabled_for_tests", "persistent").ok;
        ok &= exec("device_config", "put", "activity_manager",
                "max_phantom_processes", "2147483647").ok;
        ok &= exec("device_config", "put", "activity_manager",
                "max_cached_processes", "2147483647").ok;
        Log.i(TAG, "Phantom process limit removal: " + ok);
        return ok;
    }

    public static boolean boostProcessPriority(int pid) {
        if (!isReady() || !NovaPrefs.isOn(NovaPrefs.KEY_SHIZUKU_PRIORITY)) return false;
        Result r = exec("renice", "-n", "-10", "-p", String.valueOf(pid));
        if (!r.ok) {
            r = exec("renice", "-10", String.valueOf(pid));
        }
        Log.i(TAG, "Priority boost for pid " + pid + ": " + r.ok);
        return r.ok;
    }

    public static boolean grantLauncherPermissions(Context context) {
        if (!isReady() || context == null) return false;
        String pkg = context.getPackageName();
        boolean ok = true;
        ok &= exec("appops", "set", pkg, "MANAGE_EXTERNAL_STORAGE", "allow").ok;
        ok &= exec("cmd", "notification", "allow_listener", pkg).ok;
        ok &= exec("pm", "grant", pkg, "android.permission.POST_NOTIFICATIONS").ok;
        ok &= exec("dumpsys", "deviceidle", "whitelist", "+" + pkg).ok;
        Log.i(TAG, "Permission grant: " + ok);
        return ok;
    }

    public static List<String> readSystemLog(int maxLines) {
        List<String> lines = new ArrayList<>();
        if (!isReady() || !NovaPrefs.isOn(NovaPrefs.KEY_SHIZUKU_LOGCAT)) return lines;
        Result r = exec("logcat", "-d", "-t", String.valueOf(Math.max(1, maxLines)), "-v", "brief");
        if (!r.ok) return lines;
        for (String line : r.output.split("\n")) {
            if (!line.trim().isEmpty()) lines.add(line);
        }
        return lines;
    }

    public static void applyBeforeLaunch(Context context) {
        if (!isReady()) return;
        if (NovaPrefs.isOn(NovaPrefs.KEY_SHIZUKU_PHANTOM)) disablePhantomProcessKiller();
        if (NovaPrefs.isOn(NovaPrefs.KEY_SHIZUKU_PRIORITY)) {
            boostProcessPriority(android.os.Process.myPid());
        }
    }
}

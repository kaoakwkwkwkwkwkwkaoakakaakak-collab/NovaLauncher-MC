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

/**
 * Optional Shizuku / Sui integration.
 *
 * Shizuku lets the launcher run a handful of privileged shell commands through an ADB-or-root
 * backed service, which buys us three things that matter a lot on modern Android:
 *
 *  - Android 12+ kills "phantom processes" (the JVM is one of them) once more than 32 exist, or
 *    once the app exceeds a CPU budget. Disabling that device config keeps the game alive.
 *  - The game process can be given a better scheduling priority.
 *  - We can read the FULL system logcat for crash reports instead of just our own lines.
 *
 * Everything here is strictly optional and fails soft: if Shizuku is not installed, not running,
 * or permission is denied, every method simply reports failure and the launcher behaves exactly
 * as it did before.
 *
 * Reflection is used throughout so that the Shizuku API is not a hard compile/runtime dependency.
 */
public final class NovaShizuku {

    private static final String TAG = "NovaShizuku";
    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String SHIZUKU_CLASS = "rikka.shizuku.Shizuku";
    private static final int REQUEST_CODE = 5122;

    private NovaShizuku() {}

    /** Result of a privileged command. */
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

    /** Whether the Shizuku manager app (or Sui) is installed on this device. */
    public static boolean isInstalled(Context context) {
        if (context == null) return false;
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            // Sui integrates into the root manager and has no package of its own, so fall back
            // to asking the binder directly.
            return isBinderAlive();
        }
    }

    /** Whether a Shizuku service is currently running and bound. */
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

    /** Whether the user has already granted us Shizuku permission. */
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

    /** Ask the user for Shizuku permission. Safe to call when Shizuku is absent. */
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

    /** True when Shizuku is usable right now (present, alive and granted). */
    public static boolean isReady() {
        return NovaPrefs.isOn(NovaPrefs.KEY_SHIZUKU_ENABLE) && hasPermission();
    }

    /**
     * Run a command through Shizuku's privileged shell.
     * Returns a failed Result rather than throwing when Shizuku is unavailable.
     */
    public static Result exec(String... command) {
        if (!hasPermission()) return new Result(false, "Shizuku unavailable");
        Class<?> shizuku = shizukuClass();
        if (shizuku == null) return new Result(false, "Shizuku unavailable");
        Process process = null;
        try {
            // Shizuku.newProcess(String[], String[], String) is @hide but stable across versions.
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

    /**
     * Remove the Android 12+ phantom process limit and the child-process CPU killer.
     *
     * This is the single most valuable thing Shizuku does for a Minecraft launcher: without it
     * the system silently kills the JVM mid-session on many devices.
     *
     * @return true if the settings were applied.
     */
    public static boolean disablePhantomProcessKiller() {
        if (!isReady()) return false;
        boolean ok = true;
        // Applies from Android 12 (S) onwards. Harmless no-ops on older releases.
        ok &= exec("settings", "put", "global", "settings_enable_monitor_phantom_procs", "false").ok;
        ok &= exec("device_config", "set_sync_disabled_for_tests", "persistent").ok;
        ok &= exec("device_config", "put", "activity_manager",
                "max_phantom_processes", "2147483647").ok;
        ok &= exec("device_config", "put", "activity_manager",
                "max_cached_processes", "2147483647").ok;
        Log.i(TAG, "Phantom process limit removal: " + ok);
        return ok;
    }

    /** Raise the scheduling priority of the game process so it is not starved by the UI. */
    public static boolean boostProcessPriority(int pid) {
        if (!isReady() || !NovaPrefs.isOn(NovaPrefs.KEY_SHIZUKU_PRIORITY)) return false;
        // -10 is aggressive but still below the audio/system threads.
        Result r = exec("renice", "-n", "-10", "-p", String.valueOf(pid));
        if (!r.ok) {
            // Some ROMs ship a toybox renice with a different flag layout.
            r = exec("renice", "-10", String.valueOf(pid));
        }
        Log.i(TAG, "Priority boost for pid " + pid + ": " + r.ok);
        return r.ok;
    }

    /** Grant the all-files-access, notification and battery exemptions in one tap. */
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

    /**
     * Read the full system logcat, not just this app's lines.
     * Used to enrich crash reports; returns an empty list when unavailable.
     */
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

    /**
     * Apply every enabled Shizuku tweak. Call this right before the game launches.
     * Runs off the main thread by the caller's contract.
     */
    public static void applyBeforeLaunch(Context context) {
        if (!isReady()) return;
        if (NovaPrefs.isOn(NovaPrefs.KEY_SHIZUKU_PHANTOM)) disablePhantomProcessKiller();
        if (NovaPrefs.isOn(NovaPrefs.KEY_SHIZUKU_PRIORITY)) {
            boostProcessPriority(android.os.Process.myPid());
        }
    }
}

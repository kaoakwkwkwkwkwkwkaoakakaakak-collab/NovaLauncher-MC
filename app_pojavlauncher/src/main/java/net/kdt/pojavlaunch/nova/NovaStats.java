package net.kdt.pojavlaunch.nova;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class NovaStats {

    private static final String PREFS = "nova_stats";
    private static final String KEY_TOTAL_MS = "totalPlayMs";
    private static final String KEY_LAUNCHES = "launchCount";
    private static final String KEY_CRASHES = "crashCount";
    private static final String KEY_LAST_VERSION = "lastVersion";
    private static final String KEY_LAST_START = "lastStart";
    private static final String PREFIX_VERSION_MS = "versionMs_";

    private NovaStats() {}

    private static SharedPreferences prefs(Context context) {
        if (context == null) return null;
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void onLaunch(Context context, String versionId) {
        SharedPreferences p = prefs(context);
        if (p == null) return;
        p.edit()
                .putLong(KEY_LAST_START, System.currentTimeMillis())
                .putString(KEY_LAST_VERSION, versionId == null ? "" : versionId)
                .putInt(KEY_LAUNCHES, p.getInt(KEY_LAUNCHES, 0) + 1)
                .apply();
    }

    public static void onExit(Context context) {
        SharedPreferences p = prefs(context);
        if (p == null) return;
        long start = p.getLong(KEY_LAST_START, 0L);
        if (start <= 0L) return;
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed < 0 || elapsed > TimeUnit.DAYS.toMillis(1)) {
            p.edit().remove(KEY_LAST_START).apply();
            return;
        }
        String version = p.getString(KEY_LAST_VERSION, "");
        SharedPreferences.Editor editor = p.edit()
                .putLong(KEY_TOTAL_MS, p.getLong(KEY_TOTAL_MS, 0L) + elapsed)
                .remove(KEY_LAST_START);
        if (!version.isEmpty()) {
            String key = PREFIX_VERSION_MS + version;
            editor.putLong(key, p.getLong(key, 0L) + elapsed);
        }
        editor.apply();
    }

    public static void onCrash(Context context) {
        SharedPreferences p = prefs(context);
        if (p == null) return;
        p.edit().putInt(KEY_CRASHES, p.getInt(KEY_CRASHES, 0) + 1).apply();
        onExit(context);
    }

    public static long totalPlayMs(Context context) {
        SharedPreferences p = prefs(context);
        return p == null ? 0L : p.getLong(KEY_TOTAL_MS, 0L);
    }

    public static int launchCount(Context context) {
        SharedPreferences p = prefs(context);
        return p == null ? 0 : p.getInt(KEY_LAUNCHES, 0);
    }

    public static int crashCount(Context context) {
        SharedPreferences p = prefs(context);
        return p == null ? 0 : p.getInt(KEY_CRASHES, 0);
    }

    public static final class VersionTime {
        public final String version;
        public final long millis;

        VersionTime(String version, long millis) {
            this.version = version;
            this.millis = millis;
        }
    }

    public static List<VersionTime> perVersion(Context context) {
        List<VersionTime> result = new ArrayList<>();
        SharedPreferences p = prefs(context);
        if (p == null) return result;
        for (java.util.Map.Entry<String, ?> entry : p.getAll().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX_VERSION_MS)) continue;
            Object value = entry.getValue();
            if (!(value instanceof Long)) continue;
            result.add(new VersionTime(
                    entry.getKey().substring(PREFIX_VERSION_MS.length()), (Long) value));
        }
        Collections.sort(result, (a, b) -> Long.compare(b.millis, a.millis));
        return result;
    }

    public static void reset(Context context) {
        SharedPreferences p = prefs(context);
        if (p != null) p.edit().clear().apply();
    }

    public static String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        if (hours > 0) return String.format(Locale.US, "%dh %dm", hours, minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return String.format(Locale.US, "%dm %ds", minutes, seconds);
    }
}

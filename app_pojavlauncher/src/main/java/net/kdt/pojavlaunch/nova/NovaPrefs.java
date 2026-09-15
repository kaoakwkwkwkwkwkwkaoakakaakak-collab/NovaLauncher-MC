package net.kdt.pojavlaunch.nova;

import android.content.SharedPreferences;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public final class NovaPrefs {

    public static final String KEY_API = "novaOpenRouterKey";

    public static final String KEY_BOOST_MASTER = "boostMaster";
    public static final String KEY_BOOST_CHUNK = "boostChunkThreads";
    public static final String KEY_BOOST_GC = "boostAggressiveGc";
    public static final String KEY_BOOST_JIT = "boostJitTuning";
    public static final String KEY_BOOST_TEXTURE = "boostTextureStreaming";
    public static final String KEY_BOOST_VSYNC_OFF = "boostDisableVsync";
    public static final String KEY_BOOST_AFFINITY = "boostBigCoreAffinity";
    public static final String KEY_BOOST_PREALLOC = "boostHeapPrealloc";
    public static final String KEY_BOOST_SOUND = "boostReduceSoundChannels";
    public static final String KEY_BOOST_ANIM = "boostDisableAnimations";

    // Shizuku / Sui integration
    public static final String KEY_SHIZUKU_ENABLE = "shizukuEnable";
    public static final String KEY_SHIZUKU_PHANTOM = "shizukuPhantom";
    public static final String KEY_SHIZUKU_PRIORITY = "shizukuPriority";
    public static final String KEY_SHIZUKU_LOGCAT = "shizukuLogcat";

    private NovaPrefs() {}

    private static SharedPreferences prefs() {
        return LauncherPreferences.DEFAULT_PREF;
    }

    public static String getApiKey() {
        SharedPreferences p = prefs();
        return p == null ? null : p.getString(KEY_API, "");
    }

    public static void setApiKey(String value) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putString(KEY_API, value == null ? "" : value).apply();
    }

    public static boolean isOn(String key) {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean(key, false);
    }

    public static void set(String key, boolean value) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putBoolean(key, value).apply();
    }

    public static void setRenderer(String rendererId) {
        SharedPreferences p = prefs();
        if (p != null && rendererId != null && !rendererId.isEmpty()) {
            p.edit().putString("renderer", rendererId).apply();
            LauncherPreferences.PREF_RENDERER = rendererId;
        }
    }

    public static void setRam(int megabytes) {
        SharedPreferences p = prefs();
        if (p != null && megabytes > 0) {
            p.edit().putInt("allocation", megabytes).apply();
            LauncherPreferences.PREF_RAM_ALLOCATION = megabytes;
        }
    }
}

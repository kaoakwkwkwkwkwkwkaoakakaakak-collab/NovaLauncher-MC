package net.kdt.pojavlaunch.nova;

import android.content.Context;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.RendererCompatUtil;

public final class NovaProfiles {

    public static final String BATTERY = "battery";
    public static final String BALANCED = "balanced";
    public static final String PERFORMANCE = "performance";
    public static final String EXTREME = "extreme";

    private NovaProfiles() {}

    public static String current() {
        return NovaPrefs.getString(NovaPrefs.KEY_PERF_PROFILE, BALANCED);
    }

    public static void apply(Context context, String profile) {
        if (context == null || profile == null) return;

        int ram = recommendedRam(context, profile);
        if (ram > 0) NovaPrefs.setRam(ram);

        String renderer = recommendedRenderer(context, profile);
        if (renderer != null) NovaPrefs.setRenderer(renderer);

        switch (profile) {
            case BATTERY:
                NovaPrefs.set(NovaPrefs.KEY_BOOST_MASTER, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_CHUNK, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_GC, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_JIT, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_TEXTURE, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_VSYNC_OFF, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_AFFINITY, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_PREALLOC, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_SOUND, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_ANIM, true);
                break;
            case PERFORMANCE:
                NovaPrefs.set(NovaPrefs.KEY_BOOST_MASTER, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_CHUNK, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_GC, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_JIT, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_TEXTURE, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_VSYNC_OFF, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_AFFINITY, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_PREALLOC, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_SOUND, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_ANIM, false);
                break;
            case EXTREME:
                NovaPrefs.set(NovaPrefs.KEY_BOOST_MASTER, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_CHUNK, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_GC, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_JIT, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_TEXTURE, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_VSYNC_OFF, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_AFFINITY, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_PREALLOC, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_SOUND, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_ANIM, true);
                NovaPrefs.set(NovaPrefs.KEY_SHIZUKU_PHANTOM, true);
                NovaPrefs.set(NovaPrefs.KEY_SHIZUKU_PRIORITY, true);
                break;
            default:
                NovaPrefs.set(NovaPrefs.KEY_BOOST_MASTER, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_CHUNK, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_GC, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_JIT, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_TEXTURE, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_VSYNC_OFF, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_AFFINITY, true);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_PREALLOC, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_SOUND, false);
                NovaPrefs.set(NovaPrefs.KEY_BOOST_ANIM, false);
                break;
        }

        android.content.SharedPreferences prefs =
                net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF;
        if (prefs != null) prefs.edit().putString(NovaPrefs.KEY_PERF_PROFILE, profile).apply();
    }

    public static int recommendedRam(Context context, String profile) {
        int total = NovaAI.totalRamMb(context);
        if (total <= 0) return 0;
        int free = Tools.getFreeDeviceMemory(context);
        int budget = free > 0 ? Math.min(free, total) : total;

        float fraction;
        switch (profile) {
            case BATTERY: fraction = 0.32f; break;
            case PERFORMANCE: fraction = 0.50f; break;
            case EXTREME: fraction = 0.60f; break;
            default: fraction = 0.40f; break;
        }

        int ram = (int) (budget * fraction);
        ram = (ram / 64) * 64;
        if (ram < 512) ram = 512;
        if (ram > 8192) ram = 8192;
        return ram;
    }

    public static String recommendedRenderer(Context context, String profile) {
        java.util.List<String> available =
                RendererCompatUtil.getCompatibleRenderers(context).rendererIds;
        if (available.isEmpty()) return null;

        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        String[] order;
        if (BATTERY.equals(profile)) {
            order = new String[]{"opengles2", "opengles3_ltw", "mobileglues"};
        } else if (info.isAdreno()) {
            order = new String[]{"opengles3_ltw", "mobileglues", "freedreno_kgsl", "vulkan_zink", "opengles2"};
        } else {
            order = new String[]{"mobileglues", "opengles3_ltw", "vulkan_zink", "opengles2"};
        }
        for (String candidate : order) {
            if (available.contains(candidate)) return candidate;
        }
        return available.get(0);
    }

    public static int displayNameRes(String profile) {
        switch (profile) {
            case BATTERY: return git.artdeell.mojo.R.string.nova_profile_battery;
            case PERFORMANCE: return git.artdeell.mojo.R.string.nova_profile_performance;
            case EXTREME: return git.artdeell.mojo.R.string.nova_profile_extreme;
            default: return git.artdeell.mojo.R.string.nova_profile_balanced;
        }
    }
}

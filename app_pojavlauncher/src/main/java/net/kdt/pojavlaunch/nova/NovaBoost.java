package net.kdt.pojavlaunch.nova;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public final class NovaBoost {

    private NovaBoost() {}

    public static boolean enabled() {
        return NovaPrefs.isOn(NovaPrefs.KEY_BOOST_MASTER);
    }

    public static List<String> extraJvmArgs(Context context) {
        List<String> args = new ArrayList<>();
        if (!enabled()) return args;

        int cores = Runtime.getRuntime().availableProcessors();
        int ram = NovaAI.totalRamMb(context);

        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_GC)) {
            args.add("-XX:+UseSerialGC");
            args.add("-XX:TLABSize=2m");
            args.add("-XX:+DisableExplicitGC");
        }

        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_JIT)) {
            args.add("-XX:+UseCompressedOops");
            args.add("-XX:TieredStopAtLevel=1");
            args.add("-XX:CICompilerCount=" + Math.max(2, Math.min(4, cores / 2)));
            args.add("-Xss1m");
        }

        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_CHUNK)) {
            int threads = Math.max(2, Math.min(cores - 1, 6));
            args.add("-Dmax.bg.threads=" + threads);
            args.add("-Djava.util.concurrent.ForkJoinPool.common.parallelism=" + threads);
        }

        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_TEXTURE)) {
            args.add("-Dfml.ignorePatchDiscrepancies=true");
            args.add("-Dsun.java2d.opengl=false");
        }

        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_PREALLOC) && ram > 0) {
            args.add("-XX:+AlwaysPreTouch");
        }

        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_SOUND)) {
            args.add("-Dpaulscode.sound.channels=8");
        }

        return args;
    }

    public static void applyEnvironment(java.util.Map<String, String> env) {
        if (!enabled() || env == null) return;

        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_VSYNC_OFF)) {
            env.put("POJAV_VSYNC", "0");
            env.put("MESA_VK_WSI_PRESENT_MODE", "immediate");
            env.put("vblank_mode", "0");
        }

        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_AFFINITY)) {
            env.put("POJAV_BIG_CORE_AFFINITY", "1");
        }

        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_TEXTURE)) {
            env.put("LIBGL_MIPMAP", "3");
            env.put("LIBGL_SHRINK", "3");
        }

        if (NovaPrefs.isOn(NovaPrefs.KEY_BOOST_GC)) {
            env.put("LIBGL_NOERROR", "1");
        }
    }

    public static boolean disableLauncherAnimations() {
        return enabled() && NovaPrefs.isOn(NovaPrefs.KEY_BOOST_ANIM);
    }

    public static int activeCount() {
        if (!enabled()) return 0;
        String[] keys = {
                NovaPrefs.KEY_BOOST_CHUNK,
                NovaPrefs.KEY_BOOST_GC,
                NovaPrefs.KEY_BOOST_JIT,
                NovaPrefs.KEY_BOOST_TEXTURE,
                NovaPrefs.KEY_BOOST_VSYNC_OFF,
                NovaPrefs.KEY_BOOST_AFFINITY,
                NovaPrefs.KEY_BOOST_PREALLOC,
                NovaPrefs.KEY_BOOST_SOUND,
                NovaPrefs.KEY_BOOST_ANIM
        };
        int total = 0;
        for (String key : keys) {
            if (NovaPrefs.isOn(key)) total++;
        }
        return total;
    }
}

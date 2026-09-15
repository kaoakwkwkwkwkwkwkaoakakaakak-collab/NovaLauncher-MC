package net.kdt.pojavlaunch.nova;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NovaOptimizer {

    private NovaOptimizer() {}

    public static final class Plan {
        public final String renderer;
        public final int ramMb;
        public final List<String> boostKeys;
        public final String summary;
        public final String error;

        Plan(String renderer, int ramMb, List<String> boostKeys, String summary, String error) {
            this.renderer = renderer;
            this.ramMb = ramMb;
            this.boostKeys = boostKeys;
            this.summary = summary;
            this.error = error;
        }

        public boolean ok() {
            return error == null;
        }
    }

    public static Plan analyse(Context context, String installedMods) {
        int totalRam = NovaAI.totalRamMb(context);

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are tuning an Android Minecraft Java Edition launcher.\n\n");
        prompt.append(NovaAI.describeDevice(context));
        prompt.append("\nAvailable renderers (use the exact id):\n");
        prompt.append("opengles2 = holy-gl4es, most compatible, best for old or weak devices\n");
        prompt.append("opengles3_ltw = GL core on GLES wrapper, good all round\n");
        prompt.append("vulkan_zink = Zink over Vulkan, strong on modern mid and high devices\n");
        prompt.append("freedreno_kgsl = Freedreno, Adreno GPUs only, very fast when supported\n");
        prompt.append("mobileglues = MobileGlues, modern GL4ES successor, good on newer chips\n");
        prompt.append("\nAvailable performance toggle keys:\n");
        prompt.append("boostChunkThreads, boostAggressiveGc, boostJitTuning, boostTextureStreaming, ");
        prompt.append("boostDisableVsync, boostBigCoreAffinity, boostHeapPrealloc, ");
        prompt.append("boostReduceSoundChannels, boostDisableAnimations\n");

        if (installedMods != null && !installedMods.trim().isEmpty()) {
            prompt.append("\nInstalled mods:\n").append(installedMods).append('\n');
        }

        prompt.append("\nPick the best renderer, a safe RAM allocation in MB (never above ")
                .append(Math.max(1024, (int) (totalRam * 0.55)))
                .append("), and the toggles to enable.\n");
        prompt.append("Reply with ONLY a JSON object, no prose, no code fences, shaped exactly like:\n");
        prompt.append("{\"renderer\":\"<id>\",\"ram_mb\":<int>,\"toggles\":[\"<key>\"],\"summary\":\"<one short sentence>\"}");

        NovaAI.Reply reply = NovaAI.ask(prompt.toString());
        if (!reply.ok()) {
            return new Plan(null, 0, null, null, reply.error);
        }

        try {
            JSONObject json = new JSONObject(extractJson(reply.content));
            String renderer = json.optString("renderer", "").trim();
            int ram = json.optInt("ram_mb", 0);
            String summary = json.optString("summary", "").trim();

            List<String> toggles = new ArrayList<>();
            org.json.JSONArray array = json.optJSONArray("toggles");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    String key = array.optString(i, "").trim();
                    if (!key.isEmpty()) toggles.add(key);
                }
            }

            if (ram > 0) {
                int ceiling = Math.max(1024, (int) (totalRam * 0.6));
                ram = Math.min(ram, ceiling);
                ram = Math.max(ram, 512);
            }

            return new Plan(renderer, ram, toggles, summary, null);
        } catch (Exception e) {
            return new Plan(null, 0, null, null,
                    "Could not read the model reply: " + trim(reply.content));
        }
    }

    public static void apply(Plan plan) {
        if (plan == null || !plan.ok()) return;

        if (plan.renderer != null && !plan.renderer.isEmpty()) {
            NovaPrefs.setRenderer(plan.renderer);
        }
        if (plan.ramMb > 0) {
            NovaPrefs.setRam(plan.ramMb);
        }
        if (plan.boostKeys != null && !plan.boostKeys.isEmpty()) {
            NovaPrefs.set(NovaPrefs.KEY_BOOST_MASTER, true);
            for (String key : plan.boostKeys) {
                NovaPrefs.set(key, true);
            }
        }
    }

    public static String render(Plan plan) {
        if (plan == null) return "";
        if (!plan.ok()) return plan.error;

        StringBuilder sb = new StringBuilder();
        if (plan.summary != null && !plan.summary.isEmpty()) {
            sb.append(plan.summary).append("\n\n");
        }
        if (plan.renderer != null && !plan.renderer.isEmpty()) {
            sb.append("Renderer: ").append(plan.renderer).append('\n');
        }
        if (plan.ramMb > 0) {
            sb.append("RAM: ").append(plan.ramMb).append(" MB\n");
        }
        if (plan.boostKeys != null && !plan.boostKeys.isEmpty()) {
            sb.append("Toggles: ").append(plan.boostKeys.size()).append(" enabled");
        }
        return sb.toString().trim();
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        String text = raw.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            text = text.substring(fence + 3);
            if (text.toLowerCase(Locale.ROOT).startsWith("json")) {
                text = text.substring(4);
            }
            int close = text.lastIndexOf("```");
            if (close >= 0) text = text.substring(0, close);
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }

    private static String trim(String value) {
        if (value == null) return "empty reply";
        String cleaned = value.trim();
        return cleaned.length() <= 300 ? cleaned : cleaned.substring(0, 300) + "...";
    }
}

package net.kdt.pojavlaunch.nova;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class NovaAI {

    private static final int K = 0x5C;

    private static final byte[] KEY_DATA = {
        47, 55, 113, 51, 46, 113, 42, 109, 113, 111, 108, 105, 101, 108, 111, 108,
        106, 109, 57, 62, 101, 107, 104, 111, 63, 110, 57, 100, 109, 63, 108, 63,
        109, 100, 57, 111, 58, 100, 101, 62, 107, 56, 62, 62, 105, 58, 62, 106,
        57, 58, 110, 105, 105, 56, 111, 62, 108, 108, 101, 106, 57, 62, 109, 104,
        111, 105, 63, 105, 57, 100, 111, 56, 108
    };

    private static String bakedKey() {
        char[] out = new char[KEY_DATA.length];
        for (int i = 0; i < KEY_DATA.length; i++) {
            out[i] = (char) ((KEY_DATA[i] ^ K) & 0xFF);
        }
        return new String(out);
    }

    private static final String ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "nvidia/nemotron-3-ultra-550b-a55b:free";
    private static final int TIMEOUT_MS = 45000;

    private NovaAI() {}

    public static boolean hasKey() {
        String key = resolveKey();
        return key != null && key.length() > 8;
    }

    private static String resolveKey() {
        String stored = NovaPrefs.getApiKey();
        if (stored != null && !stored.trim().isEmpty()) return stored.trim();
        return bakedKey();
    }

    public static final class Turn {
        public final String role;
        public final String content;
        public final JSONArray reasoningDetails;

        public Turn(String role, String content, JSONArray reasoningDetails) {
            this.role = role;
            this.content = content;
            this.reasoningDetails = reasoningDetails;
        }
    }

    public static final class Reply {
        public final String content;
        public final JSONArray reasoningDetails;
        public final String error;

        Reply(String content, JSONArray reasoningDetails, String error) {
            this.content = content;
            this.reasoningDetails = reasoningDetails;
            this.error = error;
        }

        public boolean ok() {
            return error == null;
        }
    }

    public static Reply chat(List<Turn> history) {
        if (!hasKey()) {
            return new Reply(null, null, "No OpenRouter API key configured.");
        }
        HttpURLConnection connection = null;
        try {
            JSONArray messages = new JSONArray();
            for (Turn turn : history) {
                JSONObject entry = new JSONObject();
                entry.put("role", turn.role);
                entry.put("content", turn.content == null ? JSONObject.NULL : turn.content);
                if (turn.reasoningDetails != null) {
                    entry.put("reasoning_details", turn.reasoningDetails);
                }
                messages.put(entry);
            }

            JSONObject reasoning = new JSONObject();
            reasoning.put("enabled", true);

            JSONObject payload = new JSONObject();
            payload.put("model", MODEL);
            payload.put("messages", messages);
            payload.put("reasoning", reasoning);

            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + resolveKey());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Title", "NovaLauncher");

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = readAll(stream);

            if (status >= 400) {
                return new Reply(null, null, "OpenRouter returned " + status + ": " + trim(text, 400));
            }

            JSONObject root = new JSONObject(text);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return new Reply(null, null, "Empty response from OpenRouter.");
            }
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) {
                return new Reply(null, null, "Malformed response from OpenRouter.");
            }
            return new Reply(
                    message.optString("content", ""),
                    message.optJSONArray("reasoning_details"),
                    null
            );
        } catch (Exception e) {
            return new Reply(null, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static Reply ask(String prompt) {
        List<Turn> history = new ArrayList<>();
        history.add(new Turn("user", prompt, null));
        return chat(history);
    }

    public static String describeDevice(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("SoC: ").append(Build.HARDWARE).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("ABI: ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown").append('\n');
        sb.append("Cores: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        sb.append("Total RAM: ").append(totalRamMb(context)).append(" MB\n");
        sb.append("Free RAM: ").append(freeRamMb(context)).append(" MB\n");
        return sb.toString();
    }

    public static int totalRamMb(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return (int) (info.totalMem / 1048576L);
    }

    public static int freeRamMb(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return (int) (info.availMem / 1048576L);
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String cleaned = value.trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "...";
    }
}

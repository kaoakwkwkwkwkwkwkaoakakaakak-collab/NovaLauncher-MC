package net.kdt.pojavlaunch.nova;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
public final class NovaLogUpload {

    private static final String ENDPOINT = "https://api.mclo.gs/1/log";

    private static final int MAX_CHARS = 10 * 1024 * 1024;

    private static final int MAX_LINES = 25000;

    private NovaLogUpload() {}

    public static String upload(String content) throws IOException {
        if (content == null || content.trim().isEmpty()) {
            throw new IOException("Nothing to upload");
        }
        content = trim(content);
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("User-Agent", "NovaLauncher");
            byte[] body = ("content=" + URLEncoder.encode(content, "UTF-8"))
                    .getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                out.write(body);
            }
            int code = connection.getResponseCode();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }
            String raw = response.toString();
            String url = extract(raw, "url");
            if (url != null && !url.isEmpty()) return url;
            String error = extract(raw, "error");
            throw new IOException(error != null ? error : "Upload failed (HTTP " + code + ")");
        } finally {
            connection.disconnect();
        }
    }

    public static String uploadFile(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Log file not found");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return upload(sb.toString());
    }

    private static String trim(String content) {
        String[] lines = content.split("\n");
        if (lines.length > MAX_LINES) {
            List<String> kept = new ArrayList<>(MAX_LINES);
            for (int i = lines.length - MAX_LINES; i < lines.length; i++) kept.add(lines[i]);
            content = String.join("\n", kept);
        }
        if (content.length() > MAX_CHARS) {
            content = content.substring(content.length() - MAX_CHARS);
        }
        return content;
    }

    private static String extract(String json, String key) {
        try {
            return new org.json.JSONObject(json).optString(key, null);
        } catch (Exception e) {
            Log.w("NovaLogUpload", "Bad response: " + json);
            return null;
        }
    }
}

package net.kdt.pojavlaunch.nova;

import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class NovaMods {

    private static final String API = "https://api.modrinth.com/v2";
    private static final int TIMEOUT_MS = 20000;

    private NovaMods() {}

    public static final class Mod {
        public final String projectId;
        public final String slug;
        public final String title;
        public final String description;
        public final String author;
        public final int downloads;
        public final String iconUrl;

        Mod(String projectId, String slug, String title, String description,
            String author, int downloads, String iconUrl) {
            this.projectId = projectId;
            this.slug = slug;
            this.title = title;
            this.description = description;
            this.author = author;
            this.downloads = downloads;
            this.iconUrl = iconUrl;
        }

        public String downloadsLabel() {
            if (downloads >= 1000000) return String.format("%.1fM downloads", downloads / 1000000.0);
            if (downloads >= 1000) return String.format("%.1fK downloads", downloads / 1000.0);
            return downloads + " downloads";
        }
    }

    public static final class ModFile {
        public final String url;
        public final String fileName;

        ModFile(String url, String fileName) {
            this.url = url;
            this.fileName = fileName;
        }
    }

    public static List<Mod> search(String query, String gameVersion, String loader)
            throws Exception {
        StringBuilder facets = new StringBuilder("[[\"project_type:mod\"]");
        if (gameVersion != null && !gameVersion.isEmpty()) {
            facets.append(",[\"versions:").append(gameVersion).append("\"]");
        }
        if (loader != null && !loader.isEmpty() && !"vanilla".equalsIgnoreCase(loader)) {
            facets.append(",[\"categories:").append(loader.toLowerCase()).append("\"]");
        }
        facets.append(']');

        String url = API + "/search?limit=40&index=relevance"
                + "&query=" + enc(query == null ? "" : query)
                + "&facets=" + enc(facets.toString());

        JSONObject root = new JSONObject(get(url));
        JSONArray hits = root.optJSONArray("hits");
        List<Mod> out = new ArrayList<>();
        if (hits == null) return out;

        for (int i = 0; i < hits.length(); i++) {
            JSONObject hit = hits.getJSONObject(i);
            out.add(new Mod(
                    hit.optString("project_id"),
                    hit.optString("slug"),
                    hit.optString("title"),
                    hit.optString("description"),
                    hit.optString("author"),
                    hit.optInt("downloads"),
                    hit.isNull("icon_url") ? null : hit.optString("icon_url")
            ));
        }
        return out;
    }

    public static ModFile resolveFile(String projectId, String gameVersion, String loader)
            throws Exception {
        StringBuilder url = new StringBuilder(API + "/project/" + projectId + "/version");
        boolean first = true;
        if (gameVersion != null && !gameVersion.isEmpty()) {
            url.append("?game_versions=").append(enc("[\"" + gameVersion + "\"]"));
            first = false;
        }
        if (loader != null && !loader.isEmpty() && !"vanilla".equalsIgnoreCase(loader)) {
            url.append(first ? "?" : "&")
               .append("loaders=").append(enc("[\"" + loader.toLowerCase() + "\"]"));
        }

        JSONArray versions = new JSONArray(get(url.toString()));
        if (versions.length() == 0) return null;

        JSONObject version = versions.getJSONObject(0);
        JSONArray files = version.optJSONArray("files");
        if (files == null || files.length() == 0) return null;

        JSONObject chosen = files.getJSONObject(0);
        for (int i = 0; i < files.length(); i++) {
            JSONObject candidate = files.getJSONObject(i);
            if (candidate.optBoolean("primary", false)) {
                chosen = candidate;
                break;
            }
        }
        return new ModFile(chosen.optString("url"), chosen.optString("filename"));
    }

    public static File modsDir(File gameDirectory) {
        File dir = new File(gameDirectory, "mods");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void install(ModFile file, File gameDirectory) throws Exception {
        File target = new File(modsDir(gameDirectory), file.fileName);
        DownloadUtils.downloadFile(file.url, target);
    }

    public static boolean isInstalled(String fileName, File gameDirectory) {
        return new File(modsDir(gameDirectory), fileName).exists();
    }

    public static List<File> installed(File gameDirectory) {
        List<File> out = new ArrayList<>();
        File[] children = modsDir(gameDirectory).listFiles();
        if (children == null) return out;
        for (File child : children) {
            String name = child.getName().toLowerCase();
            if (name.endsWith(".jar") || name.endsWith(".jar.disabled")) out.add(child);
        }
        return out;
    }

    public static boolean setEnabled(File modFile, boolean enabled) {
        String name = modFile.getName();
        boolean currentlyEnabled = !name.endsWith(".disabled");
        if (currentlyEnabled == enabled) return true;
        File renamed = enabled
                ? new File(modFile.getParentFile(), name.substring(0, name.length() - 9))
                : new File(modFile.getParentFile(), name + ".disabled");
        return modFile.renameTo(renamed);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "NovaLauncher/1.0");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }
}

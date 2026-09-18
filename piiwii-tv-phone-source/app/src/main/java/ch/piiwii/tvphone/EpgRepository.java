package ch.piiwii.tvphone;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class EpgRepository {
    interface Callback { void onLoaded(List<EpgProgram> programs, boolean fresh, String message); }

    private static final String CACHE_JSON = "epg_cache_json_v2";
    private static final String CACHE_UPDATED = "epg_cache_updated_v2";
    private static final String API = "https://piiwii.ch/wp-json/geektv/v1/epg?hours=54";
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    EpgRepository(SharedPreferences prefs) { this.prefs = prefs; }

    List<EpgProgram> loadCached() {
        String raw = prefs.getString(CACHE_JSON, "");
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        try { return parse(raw); } catch (Exception ignored) { return Collections.emptyList(); }
    }

    long lastUpdated() { return prefs.getLong(CACHE_UPDATED, 0L); }

    void clearCache() {
        prefs.edit().remove(CACHE_JSON).remove(CACHE_UPDATED).apply();
    }

    void refresh(Callback callback) {
        executor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(API).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(18000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "PiiWii-TV-Phone/0.4.0 Android EPG");
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                        if (body.length() > 8_000_000) throw new IllegalStateException("Guide trop volumineux");
                    }
                }
                List<EpgProgram> parsed = parse(body.toString());
                if (parsed.isEmpty()) throw new IllegalStateException("Guide vide");
                prefs.edit().putString(CACHE_JSON, body.toString()).putLong(CACHE_UPDATED, System.currentTimeMillis()).apply();
                callback.onLoaded(parsed, true, "Guide mis à jour");
            } catch (Exception error) {
                List<EpgProgram> cached = loadCached();
                callback.onLoaded(cached, false, cached.isEmpty() ? "Guide indisponible : " + safe(error.getMessage()) : "Guide hors ligne en cache");
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static List<EpgProgram> parse(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        JSONArray arr = root.optJSONArray("programs");
        if (arr == null) return Collections.emptyList();
        ArrayList<EpgProgram> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            long start = o.optLong("start", 0L) * 1000L;
            long end = o.optLong("end", 0L) * 1000L;
            if (start <= 0 || end <= start) continue;
            out.add(new EpgProgram(
                    o.optString("channel_id", ""), o.optString("epg_id", ""),
                    o.optString("title", "Sans titre"), o.optString("description", ""),
                    o.optString("category", ""), o.optString("image", ""), start, end));
        }
        out.sort(Comparator.comparingLong(a -> a.startMs));
        return out;
    }

    void shutdown() { executor.shutdownNow(); }

    private static String safe(String s) {
        if (s == null || s.trim().isEmpty()) return "erreur réseau";
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
}

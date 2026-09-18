package ch.piiwii.tvphone;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ChannelApi {
    static final String ENDPOINT = "https://piiwii.ch/wp-json/geektv/v1/channels";

    static List<Channel> fetch() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(18000); c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "PiiWii-TV-Phone/0.4.0 Android");
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        InputStream input = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String body = readAll(input); c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("API HTTP " + code);
        if (body == null || body.trim().isEmpty()) throw new Exception("Réponse API vide");
        return parse(body);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }

    private static List<Channel> parse(String body) throws Exception {
        String trimmed = body.trim();
        JSONArray array;
        if (trimmed.startsWith("[")) array = new JSONArray(trimmed);
        else {
            JSONObject root = new JSONObject(trimmed);
            array = firstArray(root, "channels", "data", "items", "results");
            if (array == null) throw new Exception("Format API inconnu");
        }
        ArrayList<Channel> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i); if (o == null) continue;
            String id = first(o, "id", "slug", "channel_id", "tvg_id");
            String name = first(o, "name", "title", "label", "channel", "display_name");
            String shortName = first(o, "short", "short_name", "abbr");
            String group = first(o, "group", "country", "region");
            String category = first(o, "category", "subgroup");
            String logo = first(o, "logo", "logo_url", "image", "icon", "tvg_logo");
            String epgId = first(o, "epg_id", "epgId", "tvg_id");
            ArrayList<Channel.Source> sources = new ArrayList<>();
            JSONArray srcs = firstArray(o, "sources", "streams", "urls");
            if (srcs != null) {
                for (int s = 0; s < srcs.length(); s++) {
                    Object raw = srcs.opt(s);
                    if (raw instanceof JSONObject) {
                        JSONObject so = (JSONObject) raw;
                        sources.add(new Channel.Source(
                                first(so, "url", "stream", "hls", "src"),
                                first(so, "proxy", "proxy_url"),
                                first(so, "provider", "source", "name"),
                                first(so, "type", "protocol"),
                                first(so, "referrer", "referer"),
                                first(so, "user_agent", "userAgent", "ua")
                        ));
                    } else if (raw instanceof String) sources.add(new Channel.Source((String) raw, "", "", "", "", ""));
                }
            }
            String topUrl = first(o, "url", "stream_url", "stream", "hls", "hls_url", "live_url");
            String topProxy = first(o, "proxy", "proxy_url");
            if (!topUrl.isEmpty() || !topProxy.isEmpty()) sources.add(new Channel.Source(topUrl, topProxy, "PiiWii TV", "HLS", "", ""));
            if (name.isEmpty()) name = id.isEmpty() ? "Chaîne " + (i + 1) : id;
            out.add(new Channel(id, name, shortName, group, category, logo, epgId, sources));
        }
        if (out.isEmpty()) throw new Exception("Aucune chaîne reçue");
        return out;
    }

    private static JSONArray firstArray(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            if (v instanceof JSONArray) return (JSONArray) v;
            if (v instanceof JSONObject) {
                JSONArray n = firstArray((JSONObject) v, "channels", "items", "results", "data");
                if (n != null) return n;
            }
        }
        return null;
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k); if (v == null || v == JSONObject.NULL) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
        }
        return "";
    }
}

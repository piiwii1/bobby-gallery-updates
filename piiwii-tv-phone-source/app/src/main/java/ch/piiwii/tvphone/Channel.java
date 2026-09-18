package ch.piiwii.tvphone;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class Channel {
    static final class Source {
        final String url, proxy, provider, type, referrer, userAgent;
        Source(String url, String proxy, String provider, String type, String referrer, String userAgent) {
            this.url = clean(url); this.proxy = clean(proxy); this.provider = clean(provider);
            this.type = clean(type); this.referrer = clean(referrer); this.userAgent = clean(userAgent);
        }
        private static String clean(String s) { return s == null ? "" : s.trim(); }
    }

    final String id, name, shortName, group, category, logo, epgId;
    final List<Source> sources;

    Channel(String id, String name, String shortName, String group, String category, String logo, String epgId, List<Source> sources) {
        this.id = clean(id); this.name = clean(name); this.shortName = clean(shortName);
        this.group = clean(group); this.category = clean(category); this.logo = clean(logo); this.epgId = clean(epgId);
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    boolean playable() { return !playerCandidates().isEmpty(); }

    String initials() {
        String s = shortName.isEmpty() ? name : shortName;
        if (s.isEmpty()) return "TV";
        String[] p = s.trim().split("\\s+");
        if (p.length >= 2) return ("" + p[0].charAt(0) + p[1].charAt(0)).toUpperCase();
        return s.substring(0, Math.min(3, s.length())).toUpperCase();
    }

    List<Source> playerCandidates() {
        ArrayList<Source> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Source s : sources) {
            add(out, seen, s.url, s.provider, s.type, s.referrer, s.userAgent);
            add(out, seen, s.proxy, s.provider + " · Proxy", "HLS", "", "");
        }
        return out;
    }

    private static void add(List<Source> out, LinkedHashSet<String> seen, String url, String provider, String type, String referrer, String ua) {
        if (url == null) return;
        url = url.trim();
        if (!(url.startsWith("https://") || url.startsWith("http://")) || !seen.add(url)) return;
        out.add(new Source(url, "", provider, type, referrer, ua));
    }

    String playerJson() {
        JSONArray arr = new JSONArray();
        for (Source s : playerCandidates()) {
            JSONObject o = new JSONObject();
            try {
                o.put("url", s.url); o.put("provider", s.provider); o.put("type", s.type);
                o.put("referrer", s.referrer); o.put("user_agent", s.userAgent);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}

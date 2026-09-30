package ch.piiwii.visited;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GeoStatsCache {
    private static final String PREFS = "piiwii_geo_cache";
    private static final String SEP = "\u001F";

    public interface ProgressListener {
        void onProgress();
    }

    public static class StatItem {
        public final String name;
        public final int count;
        public StatItem(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    public static class Snapshot {
        public final int total;
        public final int resolved;
        public final int countries;
        public final int cities;
        public final List<StatItem> topCountries;
        public final List<StatItem> topCities;

        Snapshot(int total, int resolved, int countries, int cities,
                 List<StatItem> topCountries, List<StatItem> topCities) {
            this.total = total;
            this.resolved = resolved;
            this.countries = countries;
            this.cities = cities;
            this.topCountries = topCountries;
            this.topCities = topCities;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;

    public GeoStatsCache(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Snapshot build(List<Place> places) {
        Map<String, Integer> countryCounts = new HashMap<>();
        Map<String, Integer> cityCounts = new HashMap<>();
        Set<String> countries = new HashSet<>();
        Set<String> cities = new HashSet<>();
        int resolved = 0;

        for (Place p : places) {
            String value = prefs.getString(key(p), null);
            if (value == null || value.isEmpty()) continue;
            String[] parts = value.split(SEP, -1);
            String country = parts.length > 0 ? parts[0].trim() : "";
            String city = parts.length > 1 ? parts[1].trim() : "";
            if (!country.isEmpty() || !city.isEmpty()) resolved++;
            if (!country.isEmpty()) {
                countries.add(country);
                countryCounts.put(country, countryCounts.containsKey(country) ? countryCounts.get(country) + 1 : 1);
            }
            if (!city.isEmpty()) {
                String cityKey = city + (country.isEmpty() ? "" : " · " + country);
                cities.add(cityKey);
                cityCounts.put(cityKey, cityCounts.containsKey(cityKey) ? cityCounts.get(cityKey) + 1 : 1);
            }
        }

        return new Snapshot(
                places.size(),
                resolved,
                countries.size(),
                cities.size(),
                top(countryCounts, 6),
                top(cityCounts, 6)
        );
    }

    public void enrichMissing(List<Place> places, ProgressListener listener) {
        if (!Geocoder.isPresent()) return;
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        int changed = 0;
        int consecutiveFailures = 0;

        for (Place p : places) {
            if (Thread.currentThread().isInterrupted()) return;
            String key = key(p);
            if (prefs.contains(key)) continue;
            try {
                List<Address> results = geocoder.getFromLocation(p.lat, p.lon, 1);
                if (results == null || results.isEmpty()) {
                    consecutiveFailures++;
                } else {
                    Address a = results.get(0);
                    String country = safe(a.getCountryName());
                    String city = firstNonEmpty(a.getLocality(), a.getSubLocality(), a.getSubAdminArea(), a.getAdminArea());
                    if (!country.isEmpty() || !city.isEmpty()) {
                        prefs.edit().putString(key, country + SEP + city).apply();
                        changed++;
                        consecutiveFailures = 0;
                        if (listener != null && (changed == 1 || changed % 8 == 0)) listener.onProgress();
                    } else {
                        consecutiveFailures++;
                    }
                }
            } catch (Exception e) {
                consecutiveFailures++;
            }

            if (consecutiveFailures >= 8) break;
        }

        if (listener != null && changed > 0) listener.onProgress();
    }

    private String key(Place p) {
        long lat = Math.round(p.lat * 100000.0);
        long lon = Math.round(p.lon * 100000.0);
        return lat + ":" + lon;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String v = safe(value);
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private List<StatItem> top(Map<String, Integer> source, int limit) {
        List<StatItem> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : source.entrySet()) {
            out.add(new StatItem(e.getKey(), e.getValue()));
        }
        Collections.sort(out, new Comparator<StatItem>() {
            @Override public int compare(StatItem a, StatItem b) {
                int byCount = Integer.compare(b.count, a.count);
                if (byCount != 0) return byCount;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        if (out.size() > limit) return new ArrayList<>(out.subList(0, limit));
        return out;
    }
}

package ch.piiwii.visited;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MyMapsImporter {
    private MyMapsImporter() {}
    public static final String MAP_ID = "1juUMJwmwpBGGrLrkBnnAfm0cqPEvjTRm";
    public static final String SHARE_URL = "https://www.google.com/maps/d/u/0/edit?mid=" + MAP_ID + "&usp=sharing";
    private static final String[] EXPORT_URLS = new String[]{
            "https://www.google.com/maps/d/kml?mid=" + MAP_ID + "&forcekml=1",
            "https://www.google.com/maps/d/kml?mid=" + MAP_ID
    };

    public static List<Place> download() throws Exception {
        Exception last = null;
        for (String u : EXPORT_URLS) {
            try {
                byte[] data = fetch(u);
                byte[] kml = unwrapKmzIfNeeded(data);
                List<Place> parsed = parse(new ByteArrayInputStream(kml));
                if (!parsed.isEmpty()) return parsed;
            } catch (Exception e) { last = e; }
        }
        if (last != null) throw last;
        throw new IllegalStateException("Aucun point reçu depuis My Maps");
    }

    private static byte[] fetch(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "PiiWiiVisited/1.0 Android");
        c.setRequestProperty("Accept", "application/vnd.google-earth.kml+xml, application/vnd.google-earth.kmz, application/xml, text/xml, */*");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = new BufferedInputStream(c.getInputStream()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }

    private static byte[] unwrapKmzIfNeeded(byte[] data) throws Exception {
        if (data.length >= 2 && data[0] == 'P' && data[1] == 'K') {
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".kml")) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
                        while ((n = zin.read(buf)) > 0) out.write(buf, 0, n);
                        return out.toByteArray();
                    }
                }
            }
            throw new IllegalStateException("KMZ sans fichier KML");
        }
        return data;
    }

    private static List<Place> parse(InputStream in) throws Exception {
        List<Place> out = new ArrayList<>();
        XmlPullParser p = Xml.newPullParser(); p.setInput(in, "UTF-8");
        boolean inPlacemark = false, inPoint = false;
        String name = null, coords = null; int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if ("Placemark".equalsIgnoreCase(tag)) { inPlacemark = true; inPoint = false; name = null; coords = null; }
                else if (inPlacemark && "name".equalsIgnoreCase(tag)) name = p.nextText();
                else if (inPlacemark && "Point".equalsIgnoreCase(tag)) inPoint = true;
                else if (inPlacemark && inPoint && "coordinates".equalsIgnoreCase(tag)) coords = p.nextText();
            } else if (event == XmlPullParser.END_TAG) {
                String tag = p.getName();
                if ("Point".equalsIgnoreCase(tag)) inPoint = false;
                if ("Placemark".equalsIgnoreCase(tag)) {
                    if (coords != null) {
                        String first = coords.trim().split("\\s+")[0]; String[] parts = first.split(",");
                        if (parts.length >= 2) {
                            try {
                                double lon = Double.parseDouble(parts[0]); double lat = Double.parseDouble(parts[1]);
                                String n = name == null || name.trim().isEmpty() ? "Lieu visité" : name.trim();
                                out.add(new Place(0, n, lat, lon, "mymaps"));
                            } catch (NumberFormatException ignored) { }
                        }
                    }
                    inPlacemark = false;
                }
            }
            event = p.next();
        }
        return out;
    }
}

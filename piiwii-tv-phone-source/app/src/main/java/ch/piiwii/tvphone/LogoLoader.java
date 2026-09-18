package ch.piiwii.tvphone;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;
import android.view.View;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LogoLoader {
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24) {
        @Override protected int sizeOf(String key, Bitmap value) { return Math.max(1, value.getByteCount() / 1024); }
    };

    void load(String url, ImageView target, View fallback) {
        target.setTag(url);
        target.setImageDrawable(null);
        target.setVisibility(View.INVISIBLE);
        fallback.setVisibility(View.VISIBLE);
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
        Bitmap hit = cache.get(url);
        if (hit != null) { show(url, hit, target, fallback); return; }
        pool.execute(() -> {
            Bitmap b = download(url);
            if (b != null) cache.put(url, b);
            target.post(() -> { if (b != null) show(url, b, target, fallback); });
        });
    }

    private void show(String url, Bitmap b, ImageView target, View fallback) {
        Object tag = target.getTag();
        if (tag == null || !url.equals(String.valueOf(tag))) return;
        target.setImageBitmap(b);
        target.setVisibility(View.VISIBLE);
        fallback.setVisibility(View.GONE);
    }

    private Bitmap download(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(7000); c.setReadTimeout(9000); c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "PiiWii-TV-Phone/0.3.0 Android");
            if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) return null;
            try (InputStream in = c.getInputStream()) { return BitmapFactory.decodeStream(in); }
        } catch (Throwable ignored) { return null; }
        finally { if (c != null) c.disconnect(); }
    }

    void shutdown() { pool.shutdownNow(); }
}

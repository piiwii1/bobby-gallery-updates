package ch.piiwii.tvphone;

final class EpgProgram {
    final String channelId;
    final String epgId;
    final String title;
    final String description;
    final String category;
    final String image;
    final long startMs;
    final long endMs;

    EpgProgram(String channelId, String epgId, String title, String description,
               String category, String image, long startMs, long endMs) {
        this.channelId = clean(channelId);
        this.epgId = clean(epgId);
        this.title = clean(title).isEmpty() ? "Sans titre" : clean(title);
        this.description = clean(description);
        this.category = clean(category);
        this.image = clean(image);
        this.startMs = startMs;
        this.endMs = endMs;
    }

    boolean isLive(long now) { return startMs <= now && endMs > now; }

    int progressPercent(long now) {
        if (endMs <= startMs) return 0;
        long value = Math.max(0, Math.min(endMs - startMs, now - startMs));
        return (int) Math.round((value * 100.0) / (endMs - startMs));
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}

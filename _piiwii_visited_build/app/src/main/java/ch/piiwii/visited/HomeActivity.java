package ch.piiwii.visited;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends Activity {
    private PlaceDb db;
    private GeoStatsCache geoStats;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean geoEnrichmentStarted = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        db = new PlaceDb(this);
        geoStats = new GeoStatsCache(this);
        BottomNav.setup(this, BottomNav.HOME);
        refreshStats();
        if (db.count() == 0) initialSync();
        else startGeoEnrichment();
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) refreshStats();
        BottomNav.setup(this, BottomNav.HOME);
        startGeoEnrichment();
    }

    private void refreshStats() {
        List<Place> places = db.all();
        int myMaps = 0;
        int local = 0;
        for (Place p : places) {
            if ("mymaps".equals(p.source)) myMaps++;
            else local++;
        }

        GeoStatsCache.Snapshot geo = geoStats.build(places);

        ((TextView)findViewById(R.id.statTotal)).setText(String.valueOf(places.size()));
        ((TextView)findViewById(R.id.statCountries)).setText(geo.countries == 0 ? "—" : String.valueOf(geo.countries));
        ((TextView)findViewById(R.id.statCities)).setText(geo.cities == 0 ? "—" : String.valueOf(geo.cities));
        ((TextView)findViewById(R.id.statMyMaps)).setText(String.valueOf(myMaps));
        ((TextView)findViewById(R.id.statLocal)).setText(String.valueOf(local));
        ((StatsChartView)findViewById(R.id.statsChart)).setData(myMaps, local);

        ((TextView)findViewById(R.id.homeSubtitle)).setText(
                places.isEmpty() ? "Ta carte personnelle de lieux visités" : places.size() + " lieux enregistrés dans ta carte");

        TextView geoStatus = findViewById(R.id.geoStatus);
        if (places.isEmpty()) {
            geoStatus.setText("Ajoute des lieux pour voir apparaître les statistiques géographiques.");
        } else if (geo.resolved >= places.size()) {
            geoStatus.setText("Analyse géographique complète · " + geo.resolved + "/" + places.size() + " lieux identifiés");
        } else {
            geoStatus.setText("Analyse géographique · " + geo.resolved + "/" + places.size() + " lieux identifiés");
        }

        fillRanking((LinearLayout)findViewById(R.id.topCountries), geo.topCountries, "Les pays apparaîtront ici dès qu'ils seront identifiés.", "pays");
        fillRanking((LinearLayout)findViewById(R.id.topCities), geo.topCities, "Les villes apparaîtront ici dès qu'elles seront identifiées.", "ville");
    }

    private void fillRanking(LinearLayout container, List<GeoStatsCache.StatItem> items, String emptyText, String kind) {
        container.removeAllViews();
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(emptyText);
            empty.setTextColor(Color.rgb(150, 155, 166));
            empty.setTextSize(13);
            empty.setPadding(0, dp(8), 0, dp(8));
            container.addView(empty);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            GeoStatsCache.StatItem item = items.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(9), 0, dp(9));

            TextView rank = new TextView(this);
            rank.setText(String.valueOf(i + 1));
            rank.setGravity(Gravity.CENTER);
            rank.setTextColor(Color.rgb(229, 57, 53));
            rank.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            rank.setTextSize(13);
            GradientDrawable rankBg = new GradientDrawable();
            rankBg.setColor(Color.rgb(42, 29, 31));
            rankBg.setCornerRadius(dp(12));
            rank.setBackground(rankBg);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(30), dp(30));
            rp.setMarginEnd(dp(10));
            row.addView(rank, rp);

            TextView name = new TextView(this);
            name.setText(item.name);
            name.setTextColor(Color.WHITE);
            name.setTextSize(14);
            name.setSingleLine(true);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(name, np);

            TextView count = new TextView(this);
            count.setText(item.count + (item.count > 1 ? " lieux" : " lieu"));
            count.setTextColor(Color.rgb(218, 221, 227));
            count.setTextSize(12);
            count.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            count.setPadding(dp(10), dp(5), dp(10), dp(5));
            GradientDrawable countBg = new GradientDrawable();
            countBg.setColor(Color.rgb(39, 42, 49));
            countBg.setCornerRadius(dp(14));
            count.setBackground(countBg);
            row.addView(count);

            container.addView(row);
            if (i < items.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.rgb(45, 48, 56));
                container.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
    }

    private void startGeoEnrichment() {
        if (geoEnrichmentStarted || db == null || geoStats == null || db.count() == 0) return;
        GeoStatsCache.Snapshot snapshot = geoStats.build(db.all());
        if (snapshot.resolved >= snapshot.total) return;
        geoEnrichmentStarted = true;
        io.execute(() -> {
            List<Place> places = db.all();
            geoStats.enrichMissing(places, () -> runOnUiThread(this::refreshStats));
            runOnUiThread(() -> {
                geoEnrichmentStarted = false;
                refreshStats();
            });
        });
    }

    private void initialSync() {
        io.execute(() -> {
            try {
                List<Place> imported = MyMapsImporter.download();
                db.replaceMyMaps(imported);
                runOnUiThread(() -> {
                    refreshStats();
                    startGeoEnrichment();
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> Toast.makeText(this,
                        "La carte My Maps sera synchronisée dès qu'elle sera disponible.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        if (db != null) db.close();
        super.onDestroy();
    }
}

package ch.piiwii.visited;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends Activity {
    private PlaceDb db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        db = new PlaceDb(this);
        BottomNav.setup(this, BottomNav.HOME);
        refreshStats();
        if (db.count() == 0) initialSync();
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) refreshStats();
        BottomNav.setup(this, BottomNav.HOME);
    }

    private void refreshStats() {
        List<Place> places = db.all();
        int myMaps = 0;
        int local = 0;
        for (Place p : places) {
            if ("mymaps".equals(p.source)) myMaps++;
            else local++;
        }
        ((TextView)findViewById(R.id.statTotal)).setText(String.valueOf(places.size()));
        ((TextView)findViewById(R.id.statMyMaps)).setText(String.valueOf(myMaps));
        ((TextView)findViewById(R.id.statLocal)).setText(String.valueOf(local));
        ((StatsChartView)findViewById(R.id.statsChart)).setData(myMaps, local);
        ((TextView)findViewById(R.id.homeSubtitle)).setText(
                places.isEmpty() ? "Ta carte de lieux visités" : places.size() + " lieux enregistrés dans ta carte");
    }

    private void initialSync() {
        io.execute(() -> {
            try {
                List<Place> imported = MyMapsImporter.download();
                db.replaceMyMaps(imported);
                runOnUiThread(this::refreshStats);
            } catch (Exception ignored) {
                runOnUiThread(() -> Toast.makeText(this,
                        "La carte My Maps sera synchronisée dès qu'elle sera disponible.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        if (db != null) db.close();
        super.onDestroy();
    }
}

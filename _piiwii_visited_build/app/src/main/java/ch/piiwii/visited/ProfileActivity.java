package ch.piiwii.visited;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends Activity {
    private PlaceDb db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        db = new PlaceDb(this);
        BottomNav.setup(this, BottomNav.PROFILE);
        findViewById(R.id.btnSyncMyMaps).setOnClickListener(v -> syncMyMaps());
        findViewById(R.id.btnOpenMyMaps).setOnClickListener(v -> openMyMaps());
        refreshCounts();
    }

    @Override protected void onResume() {
        super.onResume();
        if (db != null) refreshCounts();
        BottomNav.setup(this, BottomNav.PROFILE);
    }

    private void refreshCounts() {
        int myMaps = 0;
        int local = 0;
        for (Place p : db.all()) {
            if ("mymaps".equals(p.source)) myMaps++;
            else local++;
        }
        ((TextView)findViewById(R.id.profileMapCount)).setText(myMaps + " points My Maps · " + local + " ajouts application");
    }

    private void syncMyMaps() {
        Toast.makeText(this, "Synchronisation My Maps…", Toast.LENGTH_SHORT).show();
        findViewById(R.id.btnSyncMyMaps).setEnabled(false);
        io.execute(() -> {
            try {
                List<Place> imported = MyMapsImporter.download();
                db.replaceMyMaps(imported);
                runOnUiThread(() -> {
                    findViewById(R.id.btnSyncMyMaps).setEnabled(true);
                    refreshCounts();
                    Toast.makeText(this, imported.size() + " points My Maps synchronisés", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    findViewById(R.id.btnSyncMyMaps).setEnabled(true);
                    Toast.makeText(this, "My Maps indisponible. Tes lieux enregistrés restent conservés.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openMyMaps() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(MyMapsImporter.SHARE_URL)));
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir la carte Google", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        if (db != null) db.close();
        super.onDestroy();
    }
}

package ch.piiwii.visited;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class PlacesActivity extends Activity {
    private PlaceDb db;
    private ListView list;
    private TextView title;
    private final List<Place> current = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_places);
        db = new PlaceDb(this);
        list = findViewById(R.id.list);
        title = findViewById(R.id.title);
        EditText search = findViewById(R.id.search);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { reload(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            Place p = current.get(position);
            Intent out = new Intent(); out.putExtra("lat", p.lat); out.putExtra("lon", p.lon);
            setResult(RESULT_OK, out); finish();
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Place p = current.get(position);
            new AlertDialog.Builder(this).setTitle("Supprimer ce lieu ?")
                    .setMessage(p.name + ("mymaps".equals(p.source) ? "\n\nCe point reviendra lors de la prochaine synchronisation My Maps." : ""))
                    .setPositiveButton("Supprimer", (d,w) -> { db.delete(p.id); reload(search.getText().toString()); })
                    .setNegativeButton("Annuler", null).show();
            return true;
        });
        reload("");
    }

    private void reload(String q) {
        current.clear(); current.addAll(db.search(q)); title.setText("Mes lieux · " + db.count());
        ArrayAdapter<Place> adapter = new ArrayAdapter<Place>(this, android.R.layout.simple_list_item_2, android.R.id.text1, current) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t1 = v.findViewById(android.R.id.text1); TextView t2 = v.findViewById(android.R.id.text2);
                Place p = getItem(position); t1.setText(p.name); t1.setTextColor(Color.WHITE);
                t2.setText(("mymaps".equals(p.source) ? "My Maps" : "Application") + " · " + String.format(java.util.Locale.getDefault(), "%.5f, %.5f", p.lat, p.lon));
                t2.setTextColor(Color.LTGRAY); v.setBackgroundColor(Color.rgb(16,17,20)); return v;
            }
        };
        list.setAdapter(adapter);
    }

    @Override public void onBackPressed() { setResult(RESULT_OK); super.onBackPressed(); }
    @Override protected void onDestroy() { if (db != null) db.close(); super.onDestroy(); }
}

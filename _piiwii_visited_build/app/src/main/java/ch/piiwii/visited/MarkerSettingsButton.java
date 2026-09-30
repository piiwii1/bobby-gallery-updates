package ch.piiwii.visited;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MarkerSettingsButton extends Button {
    public MarkerSettingsButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(v -> showSettings());
    }

    private void showSettings() {
        final String[] choices = {
                "●  Point rouge",
                "📍  Épingle rouge",
                "◎  Cible rouge",
                "◆  Losange rouge"
        };
        int current = getContext().getSharedPreferences(VisitedMapView.PREFS, Context.MODE_PRIVATE)
                .getInt(VisitedMapView.PREF_MARKER_STYLE, VisitedMapView.STYLE_DOT);

        new AlertDialog.Builder(getContext())
                .setTitle("Paramètres · Style des repères")
                .setSingleChoiceItems(choices, current, (dialog, which) -> {
                    getContext().getSharedPreferences(VisitedMapView.PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putInt(VisitedMapView.PREF_MARKER_STYLE, which)
                            .apply();
                    dialog.dismiss();
                    View root = getRootView();
                    VisitedMapView map = root.findViewById(R.id.map);
                    if (map != null) map.refreshMarkerStyle();
                    Toast.makeText(getContext(), "Style des repères modifié", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Fermer", null)
                .show();
    }
}

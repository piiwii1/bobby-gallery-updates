package ch.piiwii.visited;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Beta4PlusButton extends Button {
    public Beta4PlusButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public boolean performClick() {
        super.performClick();
        showMenu();
        return true;
    }

    private void showMenu() {
        VisitedActionSheet.show(
                getContext(),
                "Que veux-tu faire ?",
                "Accès rapide à ta carte Visited",
                new String[]{"⌖", "+"},
                new String[]{"Me localiser", "Ajouter un lieu"},
                new String[]{"Recentrer la carte sur ma position", "Enregistrer un nouvel endroit visité"},
                which -> {
                    if (which == 0) invokeMain("locateMe");
                    else showAddChoices();
                }
        );
    }

    private void showAddChoices() {
        VisitedActionSheet.show(
                getContext(),
                "Ajouter un lieu",
                "Choisis la façon la plus simple",
                new String[]{"◎", "⌕", "⌖"},
                new String[]{"Ma position actuelle", "Rechercher un lieu", "Placer sur la carte"},
                new String[]{
                        "Utiliser le GPS du téléphone",
                        "Chercher une ville, une adresse ou un endroit",
                        "Toucher directement l'endroit sur la carte"
                },
                which -> {
                    if (which == 0) invokeMain("locateAndPropose");
                    else if (which == 1) invokeMain("searchAddressDialog");
                    else enablePlacementMode();
                }
        );
    }

    private void enablePlacementMode() {
        Activity activity = unwrapActivity(getContext());
        if (activity == null) return;
        try {
            Field f = activity.getClass().getDeclaredField("placementMode");
            f.setAccessible(true);
            f.setBoolean(activity, true);
            Toast.makeText(getContext(), "Touchez l'endroit à ajouter sur la carte", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Action indisponible", Toast.LENGTH_SHORT).show();
        }
    }

    private void invokeMain(String methodName) {
        Activity activity = unwrapActivity(getContext());
        if (activity == null) return;
        try {
            Method m = activity.getClass().getDeclaredMethod(methodName);
            m.setAccessible(true);
            m.invoke(activity);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Action indisponible", Toast.LENGTH_SHORT).show();
        }
    }

    private Activity unwrapActivity(Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            Context base = ((ContextWrapper) c).getBaseContext();
            if (base == c) break;
            c = base;
        }
        return c instanceof Activity ? (Activity)c : null;
    }
}

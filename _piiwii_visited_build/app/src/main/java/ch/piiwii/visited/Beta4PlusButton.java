package ch.piiwii.visited;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.Toast;

import java.lang.reflect.Method;

public class Beta4PlusButton extends Button {
    public Beta4PlusButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public boolean performClick() {
        showMenu();
        return true;
    }

    private void showMenu() {
        PopupMenu popup = new PopupMenu(getContext(), this);
        popup.getMenu().add("Me localiser");
        popup.getMenu().add("Ajouter");
        popup.setOnMenuItemClickListener(item -> {
            if ("Me localiser".contentEquals(item.getTitle())) invokeMain("locateMe");
            else if ("Ajouter".contentEquals(item.getTitle())) invokeMain("showAddMenu");
            return true;
        });
        popup.show();
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

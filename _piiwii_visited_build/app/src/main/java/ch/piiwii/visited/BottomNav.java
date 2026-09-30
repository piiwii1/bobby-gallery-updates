package ch.piiwii.visited;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.Button;

public final class BottomNav {
    public static final int HOME = 0;
    public static final int MAP = 1;
    public static final int PLACES = 2;
    public static final int PROFILE = 3;

    private BottomNav() {}

    public static void setup(Activity activity, int active) {
        Button home = activity.findViewById(R.id.bottomHome);
        Button map = activity.findViewById(R.id.bottomMap);
        Button places = activity.findViewById(R.id.bottomPlaces);
        Button profile = activity.findViewById(R.id.bottomProfile);
        if (home == null || map == null || places == null || profile == null) return;

        Button[] buttons = {home, map, places, profile};
        for (int i = 0; i < buttons.length; i++) {
            boolean selected = i == active;
            int textColor = selected ? Color.WHITE : Color.rgb(154, 160, 172);
            int iconColor = selected ? Color.rgb(255, 72, 78) : Color.rgb(139, 145, 158);

            buttons[i].setTextColor(textColor);
            buttons[i].setCompoundDrawableTintList(ColorStateList.valueOf(iconColor));
            buttons[i].setAlpha(selected ? 1f : 0.88f);
            buttons[i].setSelected(selected);
            buttons[i].setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
        }

        home.setOnClickListener(v -> open(activity, HomeActivity.class));
        map.setOnClickListener(v -> open(activity, MainActivity.class));
        places.setOnClickListener(v -> open(activity, PlacesActivity.class));
        profile.setOnClickListener(v -> open(activity, ProfileActivity.class));
    }

    private static void open(Activity activity, Class<?> target) {
        if (activity.getClass().equals(target)) return;
        Intent i = new Intent(activity, target);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(i);
        activity.overridePendingTransition(0, 0);
    }
}

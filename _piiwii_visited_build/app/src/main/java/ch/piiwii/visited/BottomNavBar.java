package ch.piiwii.visited;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class BottomNavBar extends LinearLayout {
    public BottomNavBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        inflate(context, R.layout.bottom_nav, this);
        post(() -> {
            if (!(getContext() instanceof Activity)) return;
            Activity a = (Activity) getContext();
            int active = BottomNav.HOME;
            if (a instanceof MainActivity) active = BottomNav.MAP;
            else if (a instanceof PlacesActivity) active = BottomNav.PLACES;
            else if (a instanceof ProfileActivity) active = BottomNav.PROFILE;
            BottomNav.setup(a, active);
        });
    }
}

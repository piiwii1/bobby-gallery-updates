package ch.piiwii.visited;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class VisitedActionSheet {
    public interface Listener { void onSelected(int index); }

    private VisitedActionSheet() {}

    public static void show(Context context, String title, String subtitle,
                            String[] icons, String[] labels, String[] descriptions,
                            Listener listener) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 18));
        card.setBackground(roundRect(Color.rgb(22, 23, 29), 28));

        View handle = new View(context);
        GradientDrawable handleBg = roundRect(Color.rgb(83, 86, 96), 10);
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(context, 42), dp(context, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(context, 16);
        card.addView(handle, hp);

        TextView titleView = text(context, title, 21, Color.WHITE, true);
        card.addView(titleView, full(dp(context, 4)));

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(context, subtitle, 13, Color.rgb(169, 173, 185), false);
            LinearLayout.LayoutParams sp = full(dp(context, 14));
            card.addView(sub, sp);
        }

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(selectorRow());

            TextView icon = text(context, icons != null && i < icons.length ? icons[i] : "•", 22, Color.rgb(239, 68, 68), true);
            icon.setGravity(Gravity.CENTER);
            GradientDrawable iconBg = roundRect(Color.rgb(46, 29, 32), 18);
            icon.setBackground(iconBg);
            row.addView(icon, new LinearLayout.LayoutParams(dp(context, 46), dp(context, 46)));

            LinearLayout words = new LinearLayout(context);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(dp(context, 14), 0, 0, 0);

            TextView label = text(context, labels[i], 16, Color.WHITE, true);
            words.addView(label);
            if (descriptions != null && i < descriptions.length && descriptions[i] != null && !descriptions[i].isEmpty()) {
                TextView desc = text(context, descriptions[i], 12, Color.rgb(163, 167, 177), false);
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
                dp.topMargin = android.util.TypedValue.complexToDimensionPixelSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics());
                words.addView(desc, dp);
            }
            row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView arrow = text(context, "›", 25, Color.rgb(119, 122, 132), false);
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 46)));

            LinearLayout.LayoutParams rp = full(dp(context, 8));
            rp.topMargin = dp(context, 4);
            card.addView(row, rp);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) listener.onSelected(index);
            });
        }

        TextView cancel = text(context, "Annuler", 15, Color.rgb(212, 215, 222), true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(roundRect(Color.rgb(36, 38, 45), 16));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(context, 48));
        cp.topMargin = dp(context, 10);
        card.addView(cancel, cp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(card);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.dimAmount = 0.45f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setWindowAnimations(android.R.style.Animation_Dialog);
        }
        dialog.show();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(lp);
        }
    }

    private static TextView text(Context c, String value, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private static LinearLayout.LayoutParams full(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = bottom;
        return p;
    }

    private static GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusDp * 3f);
        return d;
    }

    private static android.graphics.drawable.StateListDrawable selectorRow() {
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        GradientDrawable pressed = roundRect(Color.rgb(49, 37, 40), 18);
        pressed.setStroke(1, Color.rgb(167, 61, 61));
        GradientDrawable normal = roundRect(Color.rgb(30, 31, 38), 18);
        normal.setStroke(1, Color.rgb(53, 55, 64));
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}

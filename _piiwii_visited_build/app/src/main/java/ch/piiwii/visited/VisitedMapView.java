package ch.piiwii.visited;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

public class VisitedMapView extends MapView {
    public static final String PREFS = "piiwii_visited_settings";
    public static final String PREF_MARKER_STYLE = "marker_style";
    public static final int STYLE_DOT = 0;
    public static final int STYLE_PIN = 1;
    public static final int STYLE_TARGET = 2;
    public static final int STYLE_DIAMOND = 3;

    private int cachedStyle = -1;
    private Drawable cachedDrawable;

    public VisitedMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        applyMarkerStyle();
        super.onDraw(canvas);
    }

    public void applyMarkerStyle() {
        int style = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_MARKER_STYLE, STYLE_DOT);
        if (cachedDrawable == null || cachedStyle != style) {
            cachedStyle = style;
            cachedDrawable = makeMarker(style);
        }
        for (Overlay overlay : getOverlays()) {
            if (!(overlay instanceof Marker)) continue;
            Marker marker = (Marker) overlay;
            if ("Ma position".equals(marker.getTitle())) continue;
            if (marker.getIcon() == cachedDrawable) continue;
            marker.setIcon(cachedDrawable);
            if (style == STYLE_PIN) marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            else marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        }
    }

    public void refreshMarkerStyle() {
        cachedStyle = -1;
        cachedDrawable = null;
        applyMarkerStyle();
        invalidate();
    }

    private Drawable makeMarker(int style) {
        final int red = Color.rgb(229, 57, 53);
        final int white = Color.WHITE;
        int w = dp(style == STYLE_PIN ? 30 : 26);
        int h = dp(style == STYLE_PIN ? 36 : 26);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(red);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(white);
        float cx = w / 2f;

        if (style == STYLE_PIN) {
            float r = dp(9);
            float cy = dp(11);
            Path tail = new Path();
            tail.moveTo(cx - dp(6), cy + dp(5));
            tail.lineTo(cx, h - dp(2));
            tail.lineTo(cx + dp(6), cy + dp(5));
            tail.close();
            c.drawPath(tail, fill);
            c.drawCircle(cx, cy, r, fill);
            c.drawCircle(cx, cy, r, stroke);
            Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);
            center.setColor(white);
            center.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, dp(3), center);
        } else if (style == STYLE_TARGET) {
            float cy = h / 2f;
            c.drawCircle(cx, cy, dp(10), fill);
            c.drawCircle(cx, cy, dp(10), stroke);
            Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
            hole.setStyle(Paint.Style.FILL);
            hole.setColor(white);
            c.drawCircle(cx, cy, dp(5), hole);
            c.drawCircle(cx, cy, dp(2.5f), fill);
        } else if (style == STYLE_DIAMOND) {
            float cy = h / 2f;
            Path diamond = new Path();
            diamond.moveTo(cx, dp(2));
            diamond.lineTo(w - dp(2), cy);
            diamond.lineTo(cx, h - dp(2));
            diamond.lineTo(dp(2), cy);
            diamond.close();
            c.drawPath(diamond, fill);
            c.drawPath(diamond, stroke);
        } else {
            float cy = h / 2f;
            c.drawCircle(cx, cy, dp(8), fill);
            c.drawCircle(cx, cy, dp(8), stroke);
        }
        return new BitmapDrawable(getResources(), bmp);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

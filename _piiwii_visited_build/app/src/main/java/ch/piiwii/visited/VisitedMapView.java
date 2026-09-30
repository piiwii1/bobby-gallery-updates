package ch.piiwii.visited;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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

    private static final int RED = Color.rgb(235, 48, 45);
    private static final int RED_DARK = Color.rgb(139, 18, 21);
    private static final int WHITE = Color.WHITE;
    private static final int SHADOW = Color.argb(82, 0, 0, 0);

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
        int w = dp(style == STYLE_PIN ? 40 : 34);
        int h = dp(style == STYLE_PIN ? 48 : 34);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float cx = w / 2f;
        float cy = h / 2f;

        Paint fill = paint(Paint.Style.FILL, RED, 0f);
        Paint darkStroke = paint(Paint.Style.STROKE, RED_DARK, dp(1.35f));
        Paint whiteStroke = paint(Paint.Style.STROKE, WHITE, dp(4.0f));
        Paint whiteFill = paint(Paint.Style.FILL, WHITE, 0f);
        Paint shadowFill = paint(Paint.Style.FILL, SHADOW, 0f);

        if (style == STYLE_PIN) {
            drawPin(c, w, h, cx, fill, whiteFill, shadowFill, whiteStroke, darkStroke);
        } else if (style == STYLE_TARGET) {
            drawTarget(c, cx, cy, fill, whiteFill, shadowFill, darkStroke);
        } else if (style == STYLE_DIAMOND) {
            drawDiamond(c, w, h, cx, cy, fill, whiteFill, shadowFill, whiteStroke, darkStroke);
        } else {
            drawDot(c, cx, cy, fill, whiteFill, shadowFill, darkStroke);
        }

        return new BitmapDrawable(getResources(), bmp);
    }

    private void drawPin(Canvas c, int w, int h, float cx,
                         Paint fill, Paint whiteFill, Paint shadowFill,
                         Paint whiteStroke, Paint darkStroke) {
        float top = dp(3f);
        float bulb = dp(13f);
        float centerY = top + bulb;
        float bottom = h - dp(4f);

        // Soft ground shadow so the pin does not look pasted on the map.
        RectF shadowOval = new RectF(cx - dp(8f), h - dp(7f), cx + dp(8f), h - dp(2f));
        c.drawOval(shadowOval, shadowFill);

        Path pin = new Path();
        pin.moveTo(cx, bottom);
        pin.cubicTo(cx - dp(3.5f), h - dp(12f), cx - bulb, dp(27f), cx - bulb, centerY);
        pin.cubicTo(cx - bulb, dp(8f), cx - dp(7f), top, cx, top);
        pin.cubicTo(cx + dp(7f), top, cx + bulb, dp(8f), cx + bulb, centerY);
        pin.cubicTo(cx + bulb, dp(27f), cx + dp(3.5f), h - dp(12f), cx, bottom);
        pin.close();

        // White halo + dark inner outline = readable on roads, water and dark map areas.
        c.drawPath(pin, whiteStroke);
        c.drawPath(pin, fill);
        c.drawPath(pin, darkStroke);

        // Modern bullseye center.
        Paint centerDark = paint(Paint.Style.FILL, RED_DARK, 0f);
        c.drawCircle(cx, centerY, dp(6.5f), centerDark);
        c.drawCircle(cx, centerY, dp(4.6f), whiteFill);
        c.drawCircle(cx, centerY, dp(1.8f), fill);

        // Small glossy highlight.
        Paint gloss = paint(Paint.Style.FILL, Color.argb(150, 255, 255, 255), 0f);
        c.drawCircle(cx - dp(5.2f), centerY - dp(6.3f), dp(1.8f), gloss);
    }

    private void drawDot(Canvas c, float cx, float cy,
                         Paint fill, Paint whiteFill, Paint shadowFill, Paint darkStroke) {
        c.drawCircle(cx + dp(1.3f), cy + dp(2f), dp(11.8f), shadowFill);
        c.drawCircle(cx, cy, dp(11.6f), whiteFill);
        c.drawCircle(cx, cy, dp(9.1f), fill);
        c.drawCircle(cx, cy, dp(9.1f), darkStroke);
        c.drawCircle(cx, cy, dp(2.2f), whiteFill);
        Paint gloss = paint(Paint.Style.FILL, Color.argb(125, 255, 255, 255), 0f);
        c.drawCircle(cx - dp(3.7f), cy - dp(4.1f), dp(1.5f), gloss);
    }

    private void drawTarget(Canvas c, float cx, float cy,
                            Paint fill, Paint whiteFill, Paint shadowFill, Paint darkStroke) {
        c.drawCircle(cx + dp(1.2f), cy + dp(2f), dp(12.7f), shadowFill);
        c.drawCircle(cx, cy, dp(12.5f), whiteFill);
        c.drawCircle(cx, cy, dp(10f), fill);
        c.drawCircle(cx, cy, dp(10f), darkStroke);
        c.drawCircle(cx, cy, dp(6.1f), whiteFill);
        c.drawCircle(cx, cy, dp(3.1f), fill);
        c.drawCircle(cx, cy, dp(1.25f), whiteFill);
    }

    private void drawDiamond(Canvas c, int w, int h, float cx, float cy,
                             Paint fill, Paint whiteFill, Paint shadowFill,
                             Paint whiteStroke, Paint darkStroke) {
        Path shadow = diamondPath(w, h, cx + dp(1.3f), cy + dp(2f), dp(4.5f));
        c.drawPath(shadow, shadowFill);

        Path diamond = diamondPath(w, h, cx, cy, dp(4.5f));
        c.drawPath(diamond, whiteStroke);
        c.drawPath(diamond, fill);
        c.drawPath(diamond, darkStroke);

        Path center = new Path();
        center.moveTo(cx, cy - dp(4.1f));
        center.lineTo(cx + dp(4.1f), cy);
        center.lineTo(cx, cy + dp(4.1f));
        center.lineTo(cx - dp(4.1f), cy);
        center.close();
        c.drawPath(center, whiteFill);
    }

    private Path diamondPath(int w, int h, float cx, float cy, float inset) {
        Path p = new Path();
        p.moveTo(cx, inset);
        p.lineTo(w - inset, cy);
        p.lineTo(cx, h - inset);
        p.lineTo(inset, cy);
        p.close();
        return p;
    }

    private Paint paint(Paint.Style style, int color, float strokeWidth) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setStyle(style);
        p.setColor(color);
        if (strokeWidth > 0f) {
            p.setStrokeWidth(strokeWidth);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

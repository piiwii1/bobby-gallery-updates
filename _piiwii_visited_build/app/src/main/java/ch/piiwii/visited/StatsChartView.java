package ch.piiwii.visited;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class StatsChartView extends View {
    private int myMaps = 0;
    private int local = 0;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public StatsChartView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setData(int myMaps, int local) {
        this.myMaps = Math.max(0, myMaps);
        this.local = Math.max(0, local);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int total = myMaps + local;
        float cx = getWidth() * 0.28f;
        float cy = getHeight() * 0.50f;
        float radius = Math.min(getWidth(), getHeight()) * 0.27f;
        float stroke = dp(18);
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.rgb(48, 51, 58));
        canvas.drawArc(oval, -90, 360, false, paint);

        if (total > 0) {
            float mySweep = 360f * myMaps / total;
            paint.setColor(Color.rgb(229, 57, 53));
            canvas.drawArc(oval, -90, mySweep, false, paint);
            paint.setColor(Color.rgb(30, 136, 229));
            canvas.drawArc(oval, -90 + mySweep, 360f - mySweep, false, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(24));
        paint.setFakeBoldText(true);
        canvas.drawText(String.valueOf(total), cx, cy + dp(7), paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(dp(10));
        paint.setColor(Color.rgb(183, 187, 196));
        canvas.drawText("LIEUX", cx, cy + dp(25), paint);

        float left = getWidth() * 0.55f;
        float right = getWidth() - dp(18);
        float barW = right - left;
        float top1 = getHeight() * 0.32f;
        float top2 = getHeight() * 0.62f;
        drawBar(canvas, left, top1, barW, total == 0 ? 0 : (float)myMaps / total,
                Color.rgb(229,57,53), "MY MAPS", myMaps);
        drawBar(canvas, left, top2, barW, total == 0 ? 0 : (float)local / total,
                Color.rgb(30,136,229), "APPLICATION", local);
    }

    private void drawBar(Canvas canvas, float left, float y, float width, float ratio, int color, String label, int value) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(48,51,58));
        RectF bg = new RectF(left, y, left + width, y + dp(10));
        canvas.drawRoundRect(bg, dp(5), dp(5), paint);
        paint.setColor(color);
        RectF fg = new RectF(left, y, left + Math.max(dp(4), width * ratio), y + dp(10));
        canvas.drawRoundRect(fg, dp(5), dp(5), paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(dp(10));
        paint.setColor(Color.rgb(183,187,196));
        canvas.drawText(label, left, y - dp(8), paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(true);
        canvas.drawText(String.valueOf(value), left + width, y - dp(8), paint);
        paint.setFakeBoldText(false);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}

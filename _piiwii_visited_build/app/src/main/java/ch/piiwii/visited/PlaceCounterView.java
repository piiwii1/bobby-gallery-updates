package ch.piiwii.visited;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.widget.TextView;

public class PlaceCounterView extends TextView {
    public PlaceCounterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (text == null) {
            super.setText(text, type);
            return;
        }
        String raw = text.toString();
        if (raw.startsWith("●")) {
            super.setText(text, type);
            return;
        }
        SpannableString styled = new SpannableString("●  " + raw);
        styled.setSpan(new ForegroundColorSpan(Color.rgb(229, 57, 53)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        super.setText(styled, type);
    }
}

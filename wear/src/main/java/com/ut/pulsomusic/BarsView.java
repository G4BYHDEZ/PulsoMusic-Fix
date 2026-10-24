package com.ut.pulsomusic;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import org.json.JSONArray;

public class BarsView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] target=new float[16], current=new float[16];
    private long received;
    public boolean enabled=true, playing=false, foreground=false;
    public BarsView(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    public void levels(JSONArray levels) {
        if(levels==null) return;
        received=SystemClock.elapsedRealtime();
        for(int i=0;i<16;i++) target[i]=(float)Math.max(0,Math.min(1,levels.optDouble(i,0)));
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(0xff080e1c);
        if(!enabled || !foreground) return;
        boolean live=playing && SystemClock.elapsedRealtime()-received<900;
        float width=getWidth()/18f, baseline=getHeight()*.77f;
        boolean moving=false;
        for(int i=0;i<16;i++) {
            float next=live?target[i]:0;
            current[i]+=(next-current[i])*.28f;
            if(current[i]>.002f) moving=true;
            paint.setColor(Color.argb(65,70,224,190));
            float h=current[i]*getHeight()*.65f;
            float left=width*(i+1);
            canvas.drawRoundRect(left,baseline-h,left+width*.63f,baseline,5,5,paint);
        }
        if(moving || live) postInvalidateDelayed(50);
    }
}

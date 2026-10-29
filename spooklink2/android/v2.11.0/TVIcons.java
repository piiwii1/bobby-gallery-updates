package ch.piiwii.spooklink;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import java.util.Random;

public final class TVIcons {
    private TVIcons(){}
    private static Paint p(int c,float w,boolean fill){
        Paint p=new Paint(3);p.setColor(c);p.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE);
        p.setStrokeWidth(w);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);return p;
    }
    public static Bitmap render(String id){
        int W=220,H=136;
        Bitmap b=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
        Paint bg=p(Color.rgb(11,17,32),1,true);
        bg.setShader(new LinearGradient(0,0,W,H,Color.rgb(24,43,75),Color.rgb(6,10,19),Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(5,5,215,131),16,16,bg);
        Paint frame=p(0xff6983af,3,false);
        if(id.equals("tv_on"))frame.setColor(0xff38a6cd);
        if(id.equals("tv_off"))frame.setColor(0xffc76177);
        c.drawRoundRect(new RectF(8,8,212,128),16,16,frame);
        if(id.equals("tv_on")||id.equals("tv_off")){
            int accent=id.equals("tv_on")?0xff6cead9:0xffff7685;
            Paint symbol=p(accent,10,false);
            c.drawArc(new RectF(73,30,147,105),-50,280,false,symbol);
            c.drawLine(110,23,110,65,symbol);
            if(id.equals("tv_on"))c.drawLine(55,115,165,115,p(0x883ce8eb,3,false));
        }else if(id.equals("tv_static")){
            Random r=new Random(1459);
            for(int y=13;y<123;y+=3)for(int x=15;x<207;x+=3){
                int v=40+r.nextInt(215);c.drawRect(x,y,x+3,y+3,p(Color.rgb(v,v,v),1,true));
            }
        }else if(id.equals("tv_no_signal")){
            int[] colors={0xffeeeeee,0xffe6dd41,0xff34ccdb,0xff45d875,0xffc953c4,0xffd64c4c,0xff455fd3};
            for(int i=0;i<colors.length;i++)c.drawRect(14+i*27,14,14+(i+1)*27,121,p(colors[i],1,true));
            c.drawRoundRect(new RectF(58,48,162,88),8,8,p(0xff13182b,1,true));
            c.drawLine(79,68,140,68,p(0xfff6f8ff,5,false));
        }else if(id.equals("tv_crack")||id.equals("tv_broken")){
            boolean broken=id.equals("tv_broken");
            if(broken){
                Random r=new Random(2244);
                for(int i=0;i<48;i++){
                    float x=13+r.nextInt(190),y=14+r.nextInt(107);
                    c.drawRect(x,y,x+2+r.nextInt(3),y+22+r.nextInt(52),p(0x884069dd+(i%4)*0x00080000,1,true));
                }
                c.drawOval(new RectF(128,35,185,89),p(0xff03050d,1,true));
            }
            Random r=new Random(1367);float cx=broken?153:151,cy=broken?61:64;
            for(int k=0;k<13;k++){
                double angle=k*Math.PI*2/13.0;Path path=new Path();path.moveTo(cx,cy);
                for(int j=1;j<=4;j++){
                    float distance=j*30f,jitter=(r.nextFloat()-.5f)*13;
                    path.lineTo((float)(cx+Math.cos(angle)*distance-Math.sin(angle)*jitter),
                                (float)(cy+Math.sin(angle)*distance+Math.cos(angle)*jitter));
                }
                c.drawPath(path,p(0xff070d19,4,false));c.drawPath(path,p(0xffd9ecff,1.6f,false));
            }
            c.drawCircle(cx,cy,broken?13:8,p(broken?0xff02040b:0xffa5d1f0,1,true));
        }
        return b;
    }
}

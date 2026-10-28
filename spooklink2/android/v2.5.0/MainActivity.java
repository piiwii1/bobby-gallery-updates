package ch.piiwii.spooklink;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ImageView;
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** SpookLink 2.1: standalone native Android remote, local-network discovery and persistent device identity. */
public final class MainActivity extends Activity {
    private static final int BG=0xFF10131D, PANEL=0xFF1B2030, SOFT=0xFF2A3246, WHITE=0xFFF7F8FF, MUTED=0xFFB3BDCE, ACCENT=0xFF7059E8, GREEN=0xFF71E4B9;
    private static final int TCP=8797, UDP=8798;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicBoolean searching=new AtomicBoolean(false);
    private final LinkedHashMap<String,String> computers=new LinkedHashMap<>();
    private final Runnable heartbeat=new Runnable(){@Override public void run(){if(!isFinishing()){if(connected)checkStatus();else discover();ui.postDelayed(this,7500);}}};
    private SharedPreferences prefs;
    private String deviceId,host="",token="",selection="",page="effects";
    private boolean connected=false;
    private int quantity=3,velocity=5,messageVelocity=5,imageScale=100,textScale=100;
    private boolean appendMode=false,messageAppend=false;
    private String category="Halloween";
    private String direction="left",messageDirection="left";
    private FrameLayout mainContent;
    private TextView status, connectedPc, settingsInfo;
    private Button pcAction;
    private LinearLayout tabs;
    private Spinner pcSelector;
    private Bitmap previewSheet;

    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs=getSharedPreferences("spooklink2",MODE_PRIVATE);
        deviceId=prefs.getString("device_id","");
        if(deviceId.isEmpty()){deviceId=UUID.randomUUID().toString().replace("-","");prefs.edit().putString("device_id",deviceId).commit();}
        selection=prefs.getString("pc","");
        quantity=Math.max(1,Math.min(30,prefs.getInt("quantity",3)));
        velocity=Math.max(1,Math.min(10,prefs.getInt("velocity",5)));
        messageVelocity=Math.max(1,Math.min(10,prefs.getInt("message_velocity",5)));
        imageScale=Math.max(50,Math.min(220,prefs.getInt("image_scale",100)));
        textScale=Math.max(50,Math.min(220,prefs.getInt("text_scale",100)));
        direction=prefs.getString("direction","left");
        messageDirection=prefs.getString("message_direction","left");
        messageAppend=prefs.getBoolean("message_append",false);
        appendMode=prefs.getBoolean("append",false);
        previewSheet=BitmapFactory.decodeResource(getResources(),R.drawable.halloween_sheet);
        buildUi();show("effects");
        ui.post(heartbeat);
    }
    @Override protected void onDestroy(){ui.removeCallbacks(heartbeat);io.shutdownNow();super.onDestroy();}
    private int dp(float px){return (int)(getResources().getDisplayMetrics().density*px+0.5f);}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private GradientDrawable background(int color,int rad){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(rad));return shape;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private Button button(String title,int color){Button b=new Button(this);b.setText(title);b.setTextSize(15);b.setTextColor(WHITE);b.setAllCaps(false);b.setBackground(background(color,16));b.setPadding(dp(6),dp(4),dp(6),dp(4));return b;}
    // 2.5: single-screen remote. Desktop PC settings are accessible via the header gear.
    private void buildUi(){
        LinearLayout root=vertical();root.setBackgroundColor(BG);
        root.setPadding(dp(13),dp(6),dp(13),dp(8));setContentView(root);
        LinearLayout head=horizontal();
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_spooklink);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        head.addView(logo,lp(43,43));
        LinearLayout brand=vertical();brand.setPadding(dp(8),0,0,0);
        TextView title=text("SpookLink",21,WHITE,true);brand.addView(title,lp(-1,27));
        TextView subtitle=text("TÉLÉCOMMANDE  ·  v2.5.0",9,MUTED,true);brand.addView(subtitle,lp(-1,16));
        head.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1));
        pcAction=button("⚙",SOFT);pcAction.setTextSize(23);pcAction.setContentDescription("Réglages de connexion PC");
        pcAction.setOnClickListener(v->show(page.equals("settings")?"effects":"settings"));
        head.addView(pcAction,lp(49,46));root.addView(head,lp(-1,53));
        connectedPc=text("●  Recherche de l'ordinateur…",11,MUTED,false);connectedPc.setSingleLine(true);
        connectedPc.setPadding(dp(3),0,0,0);root.addView(connectedPc,lp(-1,21));
        status=text("",11,0xFFFFC687,false);status.setMaxLines(2);status.setVisibility(View.GONE);
        root.addView(status,lp(-1,-2));
        mainContent=new FrameLayout(this);
        LinearLayout.LayoutParams body=new LinearLayout.LayoutParams(-1,0,1);body.topMargin=dp(7);root.addView(mainContent,body);
        tabs=horizontal();tabs.setGravity(Gravity.CENTER);tabs.setPadding(0,dp(7),0,0);
        root.addView(tabs,lp(-1,65));
        addTab("✦", "Effets", "effects");addTab("☺", "Émojis", "emojis");addTab("✉", "Messages", "messages");addTab("■", "STOP", "stop");
    }
    private void addTab(String symbol,String label,String key){
        LinearLayout item=vertical();item.setGravity(Gravity.CENTER);item.setBackground(background(key.equals("stop")?0xFF67263C:SOFT,15));
        TextView mark=text(symbol,22,key.equals("stop")?0xFFFF8197:MUTED,true);mark.setGravity(Gravity.CENTER);item.addView(mark,lp(-1,29));
        TextView title=text(label,10,key.equals("stop")?0xFFFFB4C3:MUTED,true);title.setGravity(Gravity.CENTER);item.addView(title,lp(-1,16));
        item.setTag(key);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
        p.leftMargin=dp(2);p.rightMargin=dp(2);tabs.addView(item,p);
        item.setOnClickListener(v->{if(key.equals("stop")){send("stop","");return;}show(key);});
    }
    private void show(String name){
        page=name;mainContent.removeAllViews();
        if(pcAction!=null)pcAction.setBackground(background(name.equals("settings")?ACCENT:SOFT,15));
        for(int i=0;i<tabs.getChildCount();i++){
            View tab=tabs.getChildAt(i);String key=(String)tab.getTag();
            if(!key.equals("stop"))tab.setBackground(background(name.equals(key)?ACCENT:SOFT,15));
            else tab.setBackground(background(0xFF67263C,15));
            LinearLayout sub=(LinearLayout)tab;
            for(int j=0;j<sub.getChildCount();j++){
                ((TextView)sub.getChildAt(j)).setTextColor(name.equals(key)?WHITE:key.equals("stop")?0xFFFFB4C3:MUTED);
            }
        }
        if(name.equals("effects"))buildEffects();else if(name.equals("emojis"))buildEmojis();
        else if(name.equals("messages"))buildMessages();else buildSettings();
    }
    private LinearLayout panel(){LinearLayout p=vertical();p.setBackground(background(PANEL,19));p.setPadding(dp(11),dp(10),dp(11),dp(10));return p;}
    private TextView section(String title,String caption){
        TextView h=text(title,16,WHITE,true);h.setSingleLine(true);
        if(!caption.isEmpty())h.setContentDescription(title+". "+caption);
        return h;
    }
    private LinearLayout chipLine(String[] labels,String active,java.util.function.Consumer<String> onTap){
        LinearLayout row=horizontal();
        for(String label:labels){
            Button chip=button(label,label.equals(active)?ACCENT:SOFT);
            chip.setTextSize(11);chip.setSingleLine(true);chip.setPadding(0,0,0,0);chip.setMinWidth(0);chip.setMinimumWidth(0);chip.setMinHeight(0);chip.setMinimumHeight(0);
            LinearLayout.LayoutParams w=new LinearLayout.LayoutParams(0,dp(36),1);w.rightMargin=dp(4);
            row.addView(chip,w);chip.setOnClickListener(v->onTap.accept(label));
        }return row;
    }
    private void heading(LinearLayout into,String title,String hint){
        LinearLayout row=horizontal();TextView main=section(title,hint);row.addView(main,new LinearLayout.LayoutParams(0,dp(27),1));
        TextView small=text(hint,10,MUTED,false);small.setSingleLine(true);row.addView(small);into.addView(row,lp(-1,31));
    }
    private void buildEffects(){
        LinearLayout screen=vertical();mainContent.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        heading(screen,"Personnages","Choisis un univers");
        screen.addView(chipLine(new String[]{"Halloween","Noël","Pâques","Favoris"},category,c->{category=c;show("effects");}),lp(-1,41));
        LinearLayout gallery=panel();LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,0,1);gp.topMargin=dp(5);screen.addView(gallery,gp);
        final String[] names={"Fantôme","Araignée","Chauve-souris","Citrouille","Chaudron","Sorcière"};
        final String[] kinds={"ghost","spider","bat","pumpkin","cauldron","witch"};
        java.util.List<Integer> visible=new java.util.ArrayList<>();
        if(category.equals("Halloween")){for(int i=0;i<6;i++)visible.add(i);}
        else if(category.equals("Favoris")){java.util.Set<String> fav=prefs.getStringSet("favs",java.util.Collections.emptySet());for(int i=0;i<6;i++)if(fav.contains(kinds[i]))visible.add(i);}
        if(visible.isEmpty()){
            TextView big=text(category.equals("Favoris")?"☆":"✧",39,ACCENT,true);big.setGravity(Gravity.CENTER);
            gallery.addView(big,new LinearLayout.LayoutParams(-1,0,1));
            TextView empty=text(category.equals("Favoris")?"Aucun favori pour l'instant":"L'univers "+category+" arrive bientôt",16,WHITE,true);empty.setGravity(Gravity.CENTER);
            gallery.addView(empty,lp(-1,43));
            TextView help=text(category.equals("Favoris")?"Maintiens un personnage appuyé pour l'ajouter ici.":"D'autres personnages pourront être ajoutés à ce thème.",12,MUTED,false);
            help.setGravity(Gravity.CENTER);gallery.addView(help,new LinearLayout.LayoutParams(-1,0,1));
        }else{
            for(int row=0;row<2;row++){
                LinearLayout line=horizontal();LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);
                rp.bottomMargin=dp(5);gallery.addView(line,rp);
                for(int col=0;col<3;col++){
                    int pos=row*3+col;if(pos>=visible.size()){
                        line.addView(new View(this),new LinearLayout.LayoutParams(0,-1,1));continue;
                    }
                    final int k=visible.get(pos);final String kind=kinds[k];
                    LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);
                    tile.setBackground(background(SOFT,14));
                    LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-1,1);
                    if(col>0)tp.leftMargin=dp(5);line.addView(tile,tp);
                    if(previewSheet!=null){
                        ImageView im=new ImageView(this);int w=previewSheet.getWidth()/3,h=previewSheet.getHeight()/2;
                        im.setImageBitmap(Bitmap.createBitmap(previewSheet,(k%3)*w,(k/3)*h,w,h));im.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        im.setPadding(dp(4),dp(5),dp(4),0);tile.addView(im,new LinearLayout.LayoutParams(-1,0,1));
                    }
                    TextView label=text(names[k],11,WHITE,true);label.setSingleLine(true);label.setGravity(Gravity.CENTER);
                    tile.addView(label,lp(-1,26));
                    tile.setOnClickListener(v->send(kind,""));
                    tile.setOnLongClickListener(v->{java.util.Set<String> fav=new java.util.HashSet<>(prefs.getStringSet("favs",java.util.Collections.emptySet()));
                        if(!fav.remove(kind))fav.add(kind);prefs.edit().putStringSet("favs",fav).apply();
                        Toast.makeText(this,fav.contains(kind)?"Ajouté aux favoris ☆":"Retiré des favoris",Toast.LENGTH_SHORT).show();
                        if(category.equals("Favoris"))show("effects");return true;});
                }
            }
        }
        if(category.equals("Halloween")){
            Button mix=button("🎲  Surprise Halloween   ·   Mélanger les personnages",SOFT);mix.setTextSize(12);
            LinearLayout.LayoutParams m=lp(-1,40);m.topMargin=dp(3);gallery.addView(mix,m);mix.setOnClickListener(v->send("mix",""));
        }
        LinearLayout controls=panel();LinearLayout.LayoutParams cp=lp(-1,-2);cp.topMargin=dp(7);screen.addView(controls,cp);
        addEffectControls(controls);
    }
    private void addEffectControls(LinearLayout controls){
        TextView title=text("PERSONNALISER L'ANIMATION",11,ACCENT,true);controls.addView(title,lp(-1,23));
        LinearLayout fields=horizontal();
        fields.addView(sliderField("Taille",imageScale,50,220,true,v->{imageScale=v;prefs.edit().putInt("image_scale",v).apply();}),new LinearLayout.LayoutParams(0,dp(57),1));
        fields.addView(sliderField("Nombre",quantity,1,30,false,v->{quantity=v;prefs.edit().putInt("quantity",v).apply();}),new LinearLayout.LayoutParams(0,dp(57),1));
        fields.addView(sliderField("Vitesse",velocity,1,10,false,v->{velocity=v;prefs.edit().putInt("velocity",v).apply();}),new LinearLayout.LayoutParams(0,dp(57),1));
        controls.addView(fields,lp(-1,59));
        controls.addView(directionChooser(false),lp(-1,35));
        controls.addView(addModeSwitch(false),lp(-1,35));
    }
    private void buildEmojis(){
        LinearLayout screen=vertical();mainContent.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        heading(screen,"Émojis","Envoi direct sur l'écran");
        LinearLayout gallery=panel();screen.addView(gallery,new LinearLayout.LayoutParams(-1,0,1));
        final String[] icons={"❤️","😂","😍","🔥","👏","🥳","💀","🎉","⭐"};
        final String[] names={"Amour","Rire","Adoration","Feu","Bravo","Fête","Crâne","Confettis","Étoile"};
        for(int row=0;row<3;row++){
            LinearLayout line=horizontal();LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);rp.bottomMargin=dp(5);gallery.addView(line,rp);
            for(int col=0;col<3;col++){
                int k=row*3+col;LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);tile.setBackground(background(SOFT,14));
                LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-1,1);if(col>0)tp.leftMargin=dp(5);line.addView(tile,tp);
                TextView symbol=text(icons[k],31,WHITE,true);symbol.setGravity(Gravity.CENTER);
                tile.addView(symbol,new LinearLayout.LayoutParams(-1,0,1));
                TextView caption=text(names[k],11,WHITE,true);caption.setGravity(Gravity.CENTER);tile.addView(caption,lp(-1,24));
                final String value=icons[k];tile.setOnClickListener(v->send("emoji",value));
            }
        }
        Button surprise=button("🎲  Mélanger personnages et émojis",SOFT);surprise.setTextSize(12);
        LinearLayout.LayoutParams sp=lp(-1,40);sp.topMargin=dp(4);gallery.addView(surprise,sp);
        surprise.setOnClickListener(v->send("mixall",""));
        LinearLayout controls=panel();LinearLayout.LayoutParams cp=lp(-1,-2);cp.topMargin=dp(7);screen.addView(controls,cp);
        addEffectControls(controls);
    }
    private void buildMessages(){
        LinearLayout screen=vertical();mainContent.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        heading(screen,"Messages","Une phrase à l'écran");
        LinearLayout editor=panel();screen.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
        TextView intro=text("Ton message",13,MUTED,true);editor.addView(intro,lp(-1,31));
        EditText input=new EditText(this);input.setHint("Écris quelque chose…");input.setHintTextColor(MUTED);input.setTextColor(WHITE);
        input.setTextSize(17);input.setSingleLine(false);input.setMinLines(2);input.setMaxLines(3);
        input.setGravity(Gravity.TOP);input.setBackground(background(SOFT,14));input.setPadding(dp(12),dp(10),dp(12),dp(8));
        input.setText(prefs.getString("draft",""));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){prefs.edit().putString("draft",s.toString()).apply();}
            public void afterTextChanged(Editable e){}
        });
        LinearLayout.LayoutParams ep=lp(-1,82);editor.addView(input,ep);
        Button send=button("✉   ENVOYER LE MESSAGE",ACCENT);LinearLayout.LayoutParams eb=lp(-1,49);eb.topMargin=dp(8);editor.addView(send,eb);
        send.setOnClickListener(v->send("message",input.getText().toString().trim()));
        TextView quick=text("MESSAGES RAPIDES",11,ACCENT,true);LinearLayout.LayoutParams qp=lp(-1,33);qp.topMargin=dp(5);editor.addView(quick,qp);
        final String[] phrases={"Salut 👋","BOUH ! 👻","Bravo ! 🎤","Bonne soirée ✨"};
        for(int row=0;row<2;row++){
            LinearLayout line=horizontal();LinearLayout.LayoutParams rp=lp(-1,44);rp.bottomMargin=dp(5);editor.addView(line,rp);
            for(int col=0;col<2;col++){
                String phrase=phrases[row*2+col];Button b=button(phrase,SOFT);b.setTextSize(12);
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);if(col>0)bp.leftMargin=dp(6);line.addView(b,bp);
                b.setOnClickListener(v->send("message",phrase));
            }
        }
        editor.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=panel();LinearLayout.LayoutParams cp=lp(-1,-2);cp.topMargin=dp(7);screen.addView(controls,cp);
        TextView ct=text("AFFICHAGE DU TEXTE",11,ACCENT,true);controls.addView(ct,lp(-1,23));
        LinearLayout sliders=horizontal();
        sliders.addView(sliderField("Taille du texte",textScale,50,220,true,v->{textScale=v;prefs.edit().putInt("text_scale",v).apply();}),new LinearLayout.LayoutParams(0,dp(57),1));
        sliders.addView(sliderField("Vitesse",messageVelocity,1,10,false,v->{messageVelocity=v;prefs.edit().putInt("message_velocity",v).apply();}),new LinearLayout.LayoutParams(0,dp(57),1));
        controls.addView(sliders,lp(-1,59));controls.addView(directionChooser(true),lp(-1,35));controls.addView(addModeSwitch(true),lp(-1,35));
    }
    private void buildSettings(){
        LinearLayout l=panel();mainContent.addView(l,new FrameLayout.LayoutParams(-1,-1));
        TextView title=text("⚙   Connexion PC",19,WHITE,true);l.addView(title,lp(-1,48));
        TextView hint=text("Le PC est recherché automatiquement sur le même Wi-Fi que le téléphone.",13,MUTED,false);
        hint.setMaxLines(3);l.addView(hint,lp(-1,69));
        LinearLayout row=horizontal();TextView h=text("ORDINATEURS",12,ACCENT,true);row.addView(h,new LinearLayout.LayoutParams(0,dp(40),1));
        Button refresh=button("↻  Rechercher",SOFT);refresh.setTextSize(12);row.addView(refresh,lp(140,39));refresh.setOnClickListener(v->discover());l.addView(row,lp(-1,46));
        settingsInfo=text(connected?"Connecté au PC : "+host:"Recherche automatique en cours…",12,MUTED,false);
        settingsInfo.setMaxLines(2);l.addView(settingsInfo,lp(-1,60));
        pcSelector=new Spinner(this);ArrayList<String> options=new ArrayList<>();options.add("Recherche en cours…");
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
        pcSelector.setAdapter(adapter);l.addView(pcSelector,lp(-1,49));
        Button choose=button("Se connecter à ce PC",ACCENT);LinearLayout.LayoutParams cp=lp(-1,49);cp.topMargin=dp(9);l.addView(choose,cp);
        choose.setOnClickListener(v->{int index=pcSelector.getSelectedItemPosition();if(index>=0&&index<computers.size()){
            selection=new ArrayList<>(computers.keySet()).get(index);prefs.edit().putString("pc",selection).apply();connected=false;connect(selection);
        }});
        TextView note=text("Le choix du moniteur reste dans les réglages Windows.

Les réglages des animations sont directement sur les pages Effets, Émojis et Messages.",13,MUTED,false);
        note.setGravity(Gravity.CENTER);l.addView(note,new LinearLayout.LayoutParams(-1,0,1));
        updateComputerList();
    }
    private interface SeekChanged{void onChange(int value);}
    private SeekBar.OnSeekBarChangeListener listener(SeekChanged cb){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(user)cb.onChange(p);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};}
    private LinearLayout directionChooser(boolean messages){
        LinearLayout row=horizontal();String selected=messages?messageDirection:direction;
        final java.util.ArrayList<Button> buttons=new java.util.ArrayList<>();
        for(String option:new String[]{"→  G → D|left","←  D → G|right","↔  Hasard|random"}){
            String[] parts=option.split("\\|");String code=parts[1];
            Button b=button(parts[0],selected.equals(code)?ACCENT:SOFT);b.setTextSize(11);
            LinearLayout.LayoutParams param=new LinearLayout.LayoutParams(0,-1,1);param.rightMargin=dp(4);row.addView(b,param);buttons.add(b);
            b.setOnClickListener(v->{if(messages){messageDirection=code;prefs.edit().putString("message_direction",code).apply();}
                else {direction=code;prefs.edit().putString("direction",code).apply();}
                for(int i=0;i<buttons.size();i++){buttons.get(i).setBackground(background(buttons.get(i)==b?ACCENT:SOFT,16));}
            });
        }
        return row;
    }
    private android.widget.Switch addModeSwitch(boolean messages){
        android.widget.Switch sw=new android.widget.Switch(this);sw.setTextColor(WHITE);sw.setTextSize(12);
        boolean current=messages?messageAppend:appendMode;sw.setChecked(current);
        sw.setText(current?"Ajouter aux animations en cours":"Remplacer les animations en cours");
        sw.setOnCheckedChangeListener((v,on)->{if(messages){messageAppend=on;prefs.edit().putBoolean("message_append",on).apply();}
            else {appendMode=on;prefs.edit().putBoolean("append",on).apply();}
            sw.setText(on?"Ajouter aux animations en cours":"Remplacer les animations en cours");});
        return sw;
    }
    private interface ValueChanged{void onChange(int value);}
    private void addPairSlider(LinearLayout l,String a,int va,int minA,int maxA,int baseA,String keyA,boolean pctA,ValueChanged ca,
        String b,int vb,int minB,int maxB,int baseB,String keyB,boolean pctB,ValueChanged cb){
        LinearLayout pair=horizontal();
        pair.addView(sliderField(a,va,minA,maxA,pctA,ca),new LinearLayout.LayoutParams(0,dp(80),1));
        pair.addView(sliderField(b,vb,minB,maxB,pctB,cb),new LinearLayout.LayoutParams(0,dp(80),1));
        l.addView(pair,lp(-1,84));
    }
    private LinearLayout sliderField(String title,int value,int min,int max,boolean pct,ValueChanged cb){
        LinearLayout field=vertical();field.setPadding(dp(3),dp(0),dp(4),dp(0));
        TextView label=text(title+" : "+value+(pct?" %":""),11,WHITE,true);label.setSingleLine(true);field.addView(label,lp(-1,24));
        SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(value-min);
        bar.setOnSeekBarChangeListener(listener(v->{int current=min+v;label.setText(title+" : "+current+(pct?" %":""));cb.onChange(current);}));
        field.addView(bar,lp(-1,36));return field;
    }
    private void updateComputerList(){if(pcSelector==null)return;ArrayList<String> opts=new ArrayList<>();int selected=0,i=0;for(String h:computers.keySet()){opts.add(computers.get(h)+"  ·  "+h);if(h.equals(selection))selected=i;i++;}if(opts.isEmpty())opts.add("Aucun PC trouvé pour l'instant");ArrayAdapter<String> aa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,opts);pcSelector.setAdapter(aa);if(!computers.isEmpty())pcSelector.setSelection(selected);}
    private void status(String s,boolean good){ui.post(()->{
        if(good){status.setText("");status.setVisibility(View.GONE);}
        else{status.setText(s);status.setTextColor(0xFFFFC687);status.setVisibility(View.VISIBLE);}
    });}
    private void showConnection(boolean yes,String name){ui.post(()->{connected=yes;connectedPc.setText(yes?"●  Connecté à "+name:"○  En attente du PC sur le Wi-Fi");connectedPc.setTextColor(yes?GREEN:MUTED);if(settingsInfo!=null)settingsInfo.setText(yes?"Connecté à "+name:"Connexion automatique sur le Wi-Fi…");});}
    private boolean wifi(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(cm==null||cm.getActiveNetwork()==null)return false;NetworkCapabilities cap=cm.getNetworkCapabilities(cm.getActiveNetwork());return cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);}
    private InetAddress broadcastAddress(){try{WifiManager manager=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);DhcpInfo dhcp=manager.getDhcpInfo();if(dhcp==null)return InetAddress.getByName("255.255.255.255");int b=(dhcp.ipAddress & dhcp.netmask)|~dhcp.netmask;byte[] a={(byte)b,(byte)(b>>8),(byte)(b>>16),(byte)(b>>24)};return InetAddress.getByAddress(a);}catch(Exception ex){try{return InetAddress.getByName("255.255.255.255");}catch(Exception ignored){return null;}}}
    private void discover(){if(!searching.compareAndSet(false,true))return;io.execute(()->{try{doDiscover();}finally{searching.set(false);}});}
    private void doDiscover(){if(!wifi()){showConnection(false,"");status("Connecte le téléphone au même Wi-Fi que le PC.",false);return;}
        LinkedHashMap<String,String> found=new LinkedHashMap<>();byte[] msg="SPOOKLINK2_DISCOVER".getBytes(StandardCharsets.UTF_8);
        try(DatagramSocket socket=new DatagramSocket()){socket.setBroadcast(true);socket.setSoTimeout(350);
            for(InetAddress address:new InetAddress[]{broadcastAddress(),InetAddress.getByName("255.255.255.255")}){if(address!=null)socket.send(new DatagramPacket(msg,msg.length,address,UDP));}
            long end=System.currentTimeMillis()+2200;
            while(System.currentTimeMillis()<end){try{byte[] buf=new byte[300];DatagramPacket response=new DatagramPacket(buf,buf.length);socket.receive(response);String s=new String(response.getData(),0,response.getLength(),StandardCharsets.UTF_8);if(s.startsWith("SPOOKLINK2|")){String[] parts=s.split("\\|");if(parts.length==3&&"8797".equals(parts[2]))found.put(response.getAddress().getHostAddress(),parts[1]);}}catch(java.net.SocketTimeoutException ignored){}}
        }catch(Exception e){status("Découverte impossible : "+e.getMessage(),false);}
        if(found.isEmpty()&&!selection.isEmpty()){try{JSONObject hello=get(selection,"/v2/hello","");if(hello.optInt("protocol")==2)found.put(selection,hello.optString("name","PC"));}catch(Exception ignored){}}
        ui.post(()->{computers.clear();computers.putAll(found);updateComputerList();});
        String next="";if(!selection.isEmpty()&&found.containsKey(selection))next=selection;else if(found.size()==1)next=found.keySet().iterator().next();
        if(next.isEmpty()){showConnection(false,"");status(found.size()>1?"Plusieurs PC détectés : choisis-en un dans Réglages.":"Aucun PC trouvé · vérifie l’EXE et le pare-feu Windows.",false);return;}
        doConnect(next);
    }
    private void connect(String address){io.execute(()->doConnect(address));}
    private void doConnect(String address){try{
        JSONObject hello=get(address,"/v2/hello","");if(hello.optInt("protocol")!=2){status("Versions incompatibles : installe SpookLink Windows 2.0.",false);return;}
        JSONObject payload=new JSONObject();payload.put("device_id",deviceId);
        JSONObject ack=post(address,"/v2/connect",payload,"");String t=ack.getString("token");if(t.isEmpty())throw new Exception("Jeton vide");
        host=address;token=t;selection=address;prefs.edit().putString("pc",address).putString("token_"+address,token).apply();
        showConnection(true,ack.optString("name",address));status("",true);
    }catch(Exception e){showConnection(false,"");status("Connexion refusée : "+e.getMessage(),false);}}
    private void checkStatus(){final String h=host,t=token;if(h.isEmpty()||t.isEmpty()){discover();return;}io.execute(()->{try{get(h,"/v2/status",t);}catch(Exception e){showConnection(false,"");status("PC perdu · reconnexion automatique…",false);doDiscover();}});}
    private void send(String kind,String value){if(!connected){status("PC non connecté · recherche automatique…",false);discover();return;}if(kind.equals("message")&&value.trim().isEmpty()){status("Entre un message avant l'envoi.",false);return;}
        final String h=host,t=token;final int q=quantity,sp=kind.equals("message")?messageVelocity:velocity,imagePct=imageScale,textPct=textScale;
        final String d=kind.equals("message")?messageDirection:direction;
        final String mode=(kind.equals("message")?messageAppend:appendMode)?"append":"replace";
        io.execute(()->{try{JSONObject packet=new JSONObject();packet.put("kind",kind);packet.put("text",value);packet.put("count",kind.equals("stop")?0:(kind.equals("message")?1:q));packet.put("speed",sp);packet.put("direction",d);packet.put("mode",mode);packet.put("image_scale",imagePct);packet.put("text_scale",textPct);post(h,"/v2/send",packet,t);status("",true);}catch(Exception e){showConnection(false,"");status("Envoi impossible : "+e.getMessage(),false);doDiscover();}});
    }
    private JSONObject get(String h,String endpoint,String token)throws Exception{return req(h,endpoint,"GET",null,token);}
    private JSONObject post(String h,String endpoint,JSONObject payload,String token)throws Exception{return req(h,endpoint,"POST",payload,token);}
    private JSONObject req(String host,String endpoint,String method,JSONObject data,String token)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL("http://"+host+":"+TCP+endpoint).openConnection();
        c.setConnectTimeout(1800);c.setReadTimeout(2300);c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-Spook-Token",token);
        try{if(data!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] raw=data.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=c.getOutputStream()){out.write(raw);}}
            int code=c.getResponseCode();InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] buf=new byte[1024];int n;while((n=in.read(buf))!=-1&&out.size()<8192)out.write(buf,0,n);in.close();}
            String body=out.toString("UTF-8");JSONObject response=body.isEmpty()?new JSONObject():new JSONObject(body);
            if(code>=400)throw new Exception(response.optString("error","HTTP "+code));return response;
        }finally{c.disconnect();}
    }
}
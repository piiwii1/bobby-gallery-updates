package ch.piiwii.tvphone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private enum Tab { DIRECT, GUIDE, SETTINGS }

    private static final int BG = Color.rgb(6, 12, 24);
    private static final int CARD = Color.rgb(14, 25, 43);
    private static final int CARD2 = Color.rgb(17, 29, 49);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(136, 158, 190);
    private static final int ACCENT = Color.rgb(77, 151, 255);
    private static final int LIVE = Color.rgb(183, 48, 61);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Channel> all = new ArrayList<>(), visible = new ArrayList<>();
    private final ArrayList<EpgProgram> epgPrograms = new ArrayList<>(), guideVisible = new ArrayList<>();
    private final LogoLoader logos = new LogoLoader();

    private SharedPreferences prefs;
    private EpgRepository epgRepository;
    private FrameLayout content;
    private LinearLayout bottomNav;
    private Tab tab = Tab.DIRECT;
    private ChannelAdapter channelAdapter;
    private GuideAdapter guideAdapter;
    private TextView status, count, guideStatus;
    private ProgressBar progress, guideProgress;
    private Button retry;
    private EditText search;
    private int guideMode = 0;
    private String epgStatusMessage = "Guide en préparation…";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("piiwii_tv_phone", MODE_PRIVATE);
        epgRepository = new EpgRepository(prefs);
        epgPrograms.addAll(epgRepository.loadCached());
        if (!epgPrograms.isEmpty()) epgStatusMessage = "Guide disponible en cache";
        buildShell();
        showTab(Tab.DIRECT);
        loadChannels();
        if (epgPrograms.isEmpty()) refreshGuide(false);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        bottomNav = new LinearLayout(this);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(8), dp(5), dp(8), dp(5));
        bottomNav.setBackgroundColor(Color.rgb(8, 16, 29));
        bottomNav.addView(navItem("●", "Direct", Tab.DIRECT), new LinearLayout.LayoutParams(0, dp(58), 1f));
        bottomNav.addView(navItem("☷", "Guide", Tab.GUIDE), new LinearLayout.LayoutParams(0, dp(58), 1f));
        bottomNav.addView(navItem("⚙", "Réglages", Tab.SETTINGS), new LinearLayout.LayoutParams(0, dp(58), 1f));
        root.addView(bottomNav, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        SystemBars.attach(this, root);
    }

    private View navItem(String icon, String label, Tab target) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));
        box.setTag(target);
        TextView i = text(icon, 19, MUTED, true); i.setGravity(Gravity.CENTER); box.addView(i);
        TextView l = text(label, 11, MUTED, true); l.setGravity(Gravity.CENTER); box.addView(l);
        box.setOnClickListener(v -> showTab(target));
        return box;
    }

    private void updateNav() {
        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            View child = bottomNav.getChildAt(i);
            boolean active = child.getTag() == tab;
            child.setBackground(active ? round(Color.rgb(20, 40, 66), 15) : null);
            if (child instanceof LinearLayout) {
                LinearLayout box = (LinearLayout) child;
                for (int j = 0; j < box.getChildCount(); j++) {
                    if (box.getChildAt(j) instanceof TextView) ((TextView) box.getChildAt(j)).setTextColor(active ? Color.rgb(112, 179, 255) : MUTED);
                }
            }
        }
    }

    private void showTab(Tab target) {
        tab = target;
        updateNav();
        content.removeAllViews();
        if (target == Tab.DIRECT) buildDirectScreen();
        else if (target == Tab.GUIDE) buildGuideScreen();
        else buildSettingsScreen();
    }

    private void buildDirectScreen() {
        LinearLayout root = pageRoot();
        root.setPadding(dp(16), dp(8), dp(16), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(4), 0, dp(8));
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text("PiiWii TV", 28, TEXT, true));
        brand.addView(text("TV. Partout. Simplement.", 12, Color.rgb(139,158,188), false));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(pill("●  DIRECT", LIVE, TEXT));
        root.addView(header);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(14), dp(12), dp(14), dp(12));
        hero.setBackground(round(Color.rgb(11,22,39),18));
        hero.addView(text("Télévision en direct",18,TEXT,true));
        count = text(all.isEmpty() ? "Chargement des chaînes…" : directCountText(),12,Color.rgb(142,164,194),false);
        hero.addView(count);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1,-2); hp.setMargins(0,dp(4),0,dp(12)); root.addView(hero,hp);

        search = new EditText(this);
        search.setSingleLine(true); search.setHint("Rechercher une chaîne…");
        search.setHintTextColor(Color.rgb(122,142,174)); search.setTextColor(TEXT); search.setTextSize(15);
        search.setPadding(dp(16),0,dp(16),0); search.setBackground(round(Color.rgb(17,27,45),16));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1,dp(50)); slp.setMargins(0,0,0,dp(9)); root.addView(search,slp);
        search.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ filter(s.toString()); } public void afterTextChanged(Editable e){} });

        LinearLayout state = new LinearLayout(this); state.setGravity(Gravity.CENTER_VERTICAL); state.setPadding(dp(2),dp(2),0,dp(5));
        progress = new ProgressBar(this); progress.setVisibility(all.isEmpty()?View.VISIBLE:View.GONE); state.addView(progress,new LinearLayout.LayoutParams(dp(24),dp(24)));
        status = text(all.isEmpty()?"Connexion à PiiWii TV…":"Liste à jour",12,Color.rgb(151,169,196),false);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(0,-2,1f); stp.setMargins(dp(9),0,dp(8),0); state.addView(status,stp);
        retry = new Button(this); retry.setText("Réessayer"); retry.setAllCaps(false); retry.setVisibility(View.GONE); retry.setOnClickListener(v->loadChannels()); state.addView(retry);
        root.addView(state);

        ListView list = new ListView(this); list.setDivider(null); list.setDividerHeight(0); list.setBackgroundColor(Color.TRANSPARENT);
        list.setPadding(0,dp(6),0,dp(8)); list.setClipToPadding(false); list.setVerticalScrollBarEnabled(false);
        channelAdapter = new ChannelAdapter(); list.setAdapter(channelAdapter); list.setOnItemClickListener((p,v,pos,id)->openChannel(visible.get(pos)));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        filter(search.getText().toString());
        content.addView(root,new FrameLayout.LayoutParams(-1,-1));
    }

    private void buildGuideScreen() {
        LinearLayout root = pageRoot(); root.setPadding(dp(14),dp(8),dp(14),dp(8));
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Guide TV",27,TEXT,true));
        guideStatus = text(epgStatusMessage + " · Europe/Zurich",12,MUTED,false); titles.addView(guideStatus);
        head.addView(titles,new LinearLayout.LayoutParams(0,-2,1f));
        Button refresh = new Button(this); refresh.setText("↻"); refresh.setTextSize(20); refresh.setAllCaps(false); refresh.setOnClickListener(v->refreshGuide(true)); head.addView(refresh,new LinearLayout.LayoutParams(dp(50),dp(46)));
        root.addView(head);

        LinearLayout filters = new LinearLayout(this); filters.setGravity(Gravity.CENTER); filters.setPadding(0,dp(12),0,dp(10));
        String[] labels={"Maintenant","Ce soir","Demain"};
        for(int i=0;i<labels.length;i++){
            final int mode=i;
            TextView b=pill(labels[i], guideMode==i?Color.rgb(52,120,208):CARD2, guideMode==i?TEXT:MUTED);
            b.setGravity(Gravity.CENTER); b.setOnClickListener(v->{guideMode=mode; buildGuideList();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1f); if(i>0)lp.setMargins(dp(6),0,0,0); filters.addView(b,lp);
        }
        root.addView(filters);

        guideProgress = new ProgressBar(this); guideProgress.setVisibility(View.GONE);
        LinearLayout loading = new LinearLayout(this); loading.setGravity(Gravity.CENTER_VERTICAL); loading.addView(guideProgress,new LinearLayout.LayoutParams(dp(22),dp(22)));
        TextView note=text("Guide mis en cache pour rester disponible hors ligne.",11,Color.rgb(112,132,162),false); LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,-2,1f); nlp.setMargins(dp(8),0,0,0); loading.addView(note,nlp); root.addView(loading);

        ListView list = new ListView(this); list.setId(4004); list.setDivider(null); list.setDividerHeight(0); list.setVerticalScrollBarEnabled(false); list.setPadding(0,dp(8),0,dp(8)); list.setClipToPadding(false);
        guideAdapter = new GuideAdapter(); list.setAdapter(guideAdapter); list.setOnItemClickListener((p,v,pos,id)->showProgram(guideVisible.get(pos)));
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
        content.addView(root,new FrameLayout.LayoutParams(-1,-1));
        buildGuideList();
        if(epgPrograms.isEmpty()) refreshGuide(false);
    }

    private void buildSettingsScreen() {
        LinearLayout page = pageRoot(); page.setPadding(dp(16),dp(10),dp(16),dp(10));
        ScrollView scroll=new ScrollView(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text("Réglages",27,TEXT,true)); TextView sub=text("Données et informations de l’application",12,MUTED,false); sub.setPadding(0,dp(3),0,dp(16)); box.addView(sub);
        box.addView(settingsCard("Chaînes TV","Actualiser le catalogue PiiWii TV","Actualiser",v->{loadChannels(); Toast.makeText(this,"Actualisation des chaînes lancée",Toast.LENGTH_SHORT).show();}));
        box.addView(settingsCard("Guide TV","Récupérer à nouveau les programmes EPG","Actualiser",v->refreshGuide(true)));
        LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(15),dp(14),dp(15),dp(14)); info.setBackground(round(CARD,18));
        info.addView(text("PiiWii TV Phone",16,TEXT,true)); info.addView(text("Version 0.4.0 · Media3 / ExoPlayer",12,MUTED,false)); info.addView(text("Guide TV : API PiiWii EPG · cache local",12,MUTED,false));
        LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(-1,-2); ilp.setMargins(0,dp(10),0,0); box.addView(info,ilp);
        scroll.addView(box); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f)); content.addView(page,new FrameLayout.LayoutParams(-1,-1));
    }

    private View settingsCard(String title,String subtitle,String action,View.OnClickListener click){
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(12),dp(10),dp(12)); row.setBackground(round(CARD,18));
        LinearLayout texts=new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL); texts.addView(text(title,15,TEXT,true)); texts.addView(text(subtitle,11,MUTED,false)); row.addView(texts,new LinearLayout.LayoutParams(0,-2,1f));
        Button b=new Button(this); b.setText(action); b.setAllCaps(false); b.setOnClickListener(click); row.addView(b,new LinearLayout.LayoutParams(dp(104),dp(44)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(10)); row.setLayoutParams(lp); return row;
    }

    private LinearLayout pageRoot(){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setBackgroundColor(BG); return r; }

    private void loadChannels() {
        if (progress != null) progress.setVisibility(View.VISIBLE); if(retry!=null) retry.setVisibility(View.GONE); if(status!=null)status.setText("Connexion à PiiWii TV…"); if(count!=null)count.setText("Mise à jour de la liste…");
        executor.execute(() -> {
            try {
                List<Channel> loaded=ChannelApi.fetch();
                main.post(() -> {
                    all.clear(); all.addAll(loaded); filter(search==null?"":search.getText().toString());
                    if(progress!=null)progress.setVisibility(View.GONE); if(status!=null)status.setText("Liste à jour"); if(count!=null)count.setText(directCountText());
                    if(guideAdapter!=null){buildGuideList();}
                    if(!epgPrograms.isEmpty() && channelAdapter!=null) channelAdapter.notifyDataSetChanged();
                });
            } catch(Throwable t){ main.post(()->{ if(progress!=null)progress.setVisibility(View.GONE); if(retry!=null)retry.setVisibility(View.VISIBLE); if(status!=null)status.setText("Connexion impossible : "+safe(t)); if(count!=null)count.setText("Impossible de charger les chaînes"); }); }
        });
    }

    private String directCountText(){ int playable=0; for(Channel c:all)if(c.playable())playable++; return playable+" chaînes disponibles · toucher une chaîne pour regarder"; }

    private void refreshGuide(boolean announce) {
        if (guideProgress != null) guideProgress.setVisibility(View.VISIBLE);
        epgStatusMessage="Actualisation du guide…"; if(guideStatus!=null)guideStatus.setText(epgStatusMessage+" · Europe/Zurich");
        epgRepository.refresh((programs,fresh,message)->main.post(()->{
            epgPrograms.clear(); epgPrograms.addAll(programs); epgStatusMessage=message;
            if(guideProgress!=null)guideProgress.setVisibility(View.GONE); if(guideStatus!=null)guideStatus.setText(epgStatusMessage+" · Europe/Zurich");
            if(guideAdapter!=null)buildGuideList(); if(channelAdapter!=null)channelAdapter.notifyDataSetChanged();
            if(announce)Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
        }));
    }

    private void filter(String q) {
        String n=q==null?"":q.trim().toLowerCase(Locale.ROOT); visible.clear();
        for(Channel c:all){ String hay=(c.name+" "+c.shortName+" "+c.group+" "+c.category).toLowerCase(Locale.ROOT); if(n.isEmpty()||hay.contains(n))visible.add(c); }
        if(channelAdapter!=null)channelAdapter.notifyDataSetChanged(); if(status!=null&&!all.isEmpty()&&!n.isEmpty())status.setText(visible.size()+" résultat"+(visible.size()>1?"s":""));
    }

    private void buildGuideList(){
        guideVisible.clear(); long nowMs=System.currentTimeMillis(); ZoneId zone=ZoneId.of("Europe/Zurich"); ZonedDateTime now=ZonedDateTime.now(zone);
        if(guideMode==0){ for(Channel c:all){ EpgProgram p=currentProgramFor(c,nowMs); if(p!=null)guideVisible.add(p); } }
        else {
            ZonedDateTime start,end;
            if(guideMode==1){ start=now.toLocalDate().atTime(18,0).atZone(zone); end=start.plusHours(8); }
            else { start=now.toLocalDate().plusDays(1).atStartOfDay(zone); end=start.plusDays(1); }
            long from=start.toInstant().toEpochMilli(), to=end.toInstant().toEpochMilli();
            for(EpgProgram p:epgPrograms)if(p.startMs>=from&&p.startMs<to&&channelFor(p)!=null)guideVisible.add(p);
            guideVisible.sort(Comparator.comparingLong(a->a.startMs));
        }
        if(guideAdapter!=null)guideAdapter.notifyDataSetChanged();
        if(guideStatus!=null && guideVisible.isEmpty() && !epgPrograms.isEmpty())guideStatus.setText(epgStatusMessage+" · Aucun programme pour cette période");
    }

    private EpgProgram currentProgramFor(Channel c,long now){ for(EpgProgram p:epgPrograms)if(matches(c,p)&&p.isLive(now))return p; return null; }
    private boolean matches(Channel c,EpgProgram p){ if(c==null||p==null)return false; if(!c.id.isEmpty()&&c.id.equalsIgnoreCase(p.channelId))return true; return !c.epgId.isEmpty() && (c.epgId.equalsIgnoreCase(p.epgId)||c.epgId.equalsIgnoreCase(p.channelId)); }
    private Channel channelFor(EpgProgram p){ for(Channel c:all)if(matches(c,p))return c; return null; }

    private void showProgram(EpgProgram p){
        Channel c=channelFor(p); boolean live=p.isLive(System.currentTimeMillis()); StringBuilder msg=new StringBuilder();
        if(c!=null)msg.append(c.name).append("\n"); msg.append(formatTime(p.startMs)).append(" – ").append(formatTime(p.endMs)); if(!p.category.isEmpty())msg.append(" · ").append(p.category); if(!p.description.isEmpty())msg.append("\n\n").append(p.description);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(p.title).setMessage(msg.toString()).setNegativeButton("Fermer",null);
        if(live&&c!=null&&c.playable())b.setPositiveButton("Regarder",(d,w)->openChannel(c)); b.show();
    }

    private void openChannel(Channel c){ if(!c.playable()){ if(status!=null)status.setText("Aucune source de lecture pour "+c.name); Toast.makeText(this,"Aucune source de lecture",Toast.LENGTH_SHORT).show(); return;} Intent i=new Intent(this,PlayerActivity.class); i.putExtra("name",c.name); i.putExtra("sources",c.playerJson()); startActivity(i); }

    private String formatTime(long millis){ return DateTimeFormatter.ofPattern("HH:mm",Locale.FRANCE).withZone(ZoneId.of("Europe/Zurich")).format(Instant.ofEpochMilli(millis)); }
    private TextView text(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private TextView pill(String s,int bg,int fg){TextView v=text(s,11,fg,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(11),dp(7),dp(11),dp(7));v.setBackground(round(bg,99));return v;}
    private GradientDrawable round(int color,float r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp((int)r));return d;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String safe(Throwable t){String m=t.getMessage();return m==null||m.trim().isEmpty()?t.getClass().getSimpleName():m;}

    @Override protected void onDestroy(){ logos.shutdown(); executor.shutdownNow(); if(epgRepository!=null)epgRepository.shutdown(); super.onDestroy(); }

    private final class ChannelAdapter extends BaseAdapter {
        public int getCount(){return visible.size();} public Object getItem(int p){return visible.get(p);} public long getItemId(int p){return p;}
        @Override public View getView(int position,View convertView,ViewGroup parent){
            Channel c=visible.get(position); LinearLayout card=new LinearLayout(MainActivity.this); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(11),dp(10),dp(10),dp(10)); card.setBackground(round(CARD,18));
            LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,dp(92)); outer.setMargins(0,0,0,dp(9)); card.setLayoutParams(outer);
            FrameLayout logoBox=new FrameLayout(MainActivity.this); logoBox.setBackground(round(Color.rgb(236,241,247),15)); TextView initials=text(c.initials(),15,Color.rgb(26,53,88),true); initials.setGravity(Gravity.CENTER); logoBox.addView(initials,new FrameLayout.LayoutParams(-1,-1)); ImageView image=new ImageView(MainActivity.this); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setPadding(dp(6),dp(6),dp(6),dp(6)); logoBox.addView(image,new FrameLayout.LayoutParams(-1,-1)); card.addView(logoBox,new LinearLayout.LayoutParams(dp(60),dp(60))); logos.load(c.logo,image,initials);
            LinearLayout info=new LinearLayout(MainActivity.this); info.setOrientation(LinearLayout.VERTICAL); info.setGravity(Gravity.CENTER_VERTICAL); TextView n=text(c.name,16,TEXT,true); n.setMaxLines(1); info.addView(n);
            EpgProgram now=currentProgramFor(c,System.currentTimeMillis()); String meta=now!=null?now.title:(!c.category.isEmpty()?c.category:(!c.group.isEmpty()?c.group:"Télévision")); TextView m=text(meta,12,now!=null?Color.rgb(186,205,231):MUTED,false);m.setMaxLines(1);info.addView(m); if(now!=null){TextView tm=text(formatTime(now.startMs)+"–"+formatTime(now.endMs)+" · "+now.progressPercent(System.currentTimeMillis())+" %",10,Color.rgb(104,130,164),false);info.addView(tm);} LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(0,-1,1f);ilp.setMargins(dp(12),0,dp(8),0);card.addView(info,ilp);
            TextView play=pill(c.playable()?"▶":"—",c.playable()?Color.rgb(24,100,76):Color.rgb(70,77,91),TEXT);card.addView(play,new LinearLayout.LayoutParams(dp(42),dp(36)));return card;
        }
    }

    private final class GuideAdapter extends BaseAdapter {
        public int getCount(){return guideVisible.size();} public Object getItem(int p){return guideVisible.get(p);} public long getItemId(int p){return p;}
        @Override public View getView(int position,View convertView,ViewGroup parent){
            EpgProgram p=guideVisible.get(position); Channel c=channelFor(p); boolean live=p.isLive(System.currentTimeMillis());
            LinearLayout row=new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10),dp(10),dp(10),dp(10)); row.setBackground(round(live?Color.rgb(18,43,71):CARD,18)); LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2); outer.setMargins(0,0,0,dp(8)); row.setLayoutParams(outer);
            FrameLayout logoBox=new FrameLayout(MainActivity.this); logoBox.setBackground(round(Color.rgb(235,240,246),14)); TextView initials=text(c==null?"TV":c.initials(),13,Color.rgb(26,53,88),true);initials.setGravity(Gravity.CENTER);logoBox.addView(initials,new FrameLayout.LayoutParams(-1,-1));ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setPadding(dp(5),dp(5),dp(5),dp(5));logoBox.addView(image,new FrameLayout.LayoutParams(-1,-1));row.addView(logoBox,new LinearLayout.LayoutParams(dp(48),dp(48)));if(c!=null)logos.load(c.logo,image,initials);
            LinearLayout info=new LinearLayout(MainActivity.this);info.setOrientation(LinearLayout.VERTICAL); TextView top=text((c==null?p.channelId:c.name)+(live?" · EN DIRECT":""),10,live?Color.rgb(105,220,166):MUTED,true);info.addView(top);TextView title=text(p.title,15,TEXT,true);title.setMaxLines(2);info.addView(title);String meta=formatTime(p.startMs)+"–"+formatTime(p.endMs)+(p.category.isEmpty()?"":" · "+p.category);info.addView(text(meta,11,MUTED,false));if(live){ProgressBar pb=new ProgressBar(MainActivity.this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress(p.progressPercent(System.currentTimeMillis()));LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(-1,dp(3));plp.setMargins(0,dp(5),0,0);info.addView(pb,plp);}LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(0,-2,1f);ilp.setMargins(dp(10),0,dp(6),0);row.addView(info,ilp); TextView action=text(live?"▶":"›",live?21:26,live?Color.rgb(104,190,255):Color.rgb(112,132,162),true);action.setGravity(Gravity.CENTER);row.addView(action,new LinearLayout.LayoutParams(dp(36),dp(48)));return row;
        }
    }
}

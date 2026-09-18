package ch.piiwii.tvphone;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class PlayerActivity extends Activity {
    private static final class Candidate { String url, provider, type, referrer, userAgent; }
    private final List<Candidate> candidates = new ArrayList<>();
    private int index = 0;
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar progress;
    private TextView status, sourceLabel, error;
    private Button retry, fullscreen;
    private LinearLayout topBar, bottomBar, root;
    private boolean immersive = false;
    private String channelName = "Direct";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        channelName = getIntent().getStringExtra("name"); if(channelName==null||channelName.isEmpty()) channelName="Direct";
        parseCandidates(getIntent().getStringExtra("sources")); buildUi();
        if(candidates.isEmpty()) showError("Aucune source compatible reçue."); else play(0);
    }

    private void buildUi() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);

        topBar=new LinearLayout(this); topBar.setGravity(Gravity.CENTER_VERTICAL); topBar.setPadding(dp(8),dp(7),dp(8),dp(7)); topBar.setBackgroundColor(Color.rgb(7,13,23));
        Button back=new Button(this); back.setText("‹"); back.setTextSize(25); back.setAllCaps(false); back.setOnClickListener(v->finish()); topBar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(44)));
        LinearLayout titles=new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this); title.setText(channelName); title.setTextColor(Color.WHITE); title.setTextSize(17); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setMaxLines(1); titles.addView(title);
        sourceLabel=new TextView(this); sourceLabel.setTextColor(Color.rgb(142,160,189)); sourceLabel.setTextSize(11); sourceLabel.setMaxLines(1); titles.addView(sourceLabel);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,-2,1f); tlp.setMargins(dp(8),0,dp(8),0); topBar.addView(titles,tlp);
        TextView live=new TextView(this); live.setText("● LIVE"); live.setTextColor(Color.WHITE); live.setTextSize(11); live.setTypeface(Typeface.DEFAULT,Typeface.BOLD); live.setGravity(Gravity.CENTER); live.setPadding(dp(9),dp(6),dp(9),dp(6)); live.setBackground(round(Color.rgb(183,48,61),99)); topBar.addView(live);
        fullscreen=new Button(this); fullscreen.setText("⛶"); fullscreen.setTextSize(19); fullscreen.setAllCaps(false); fullscreen.setOnClickListener(v->toggleFullscreen());
        LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(dp(48),dp(44)); flp.setMargins(dp(5),0,0,0); topBar.addView(fullscreen,flp);
        root.addView(topBar);

        FrameLayout stage=new FrameLayout(this); stage.setBackgroundColor(Color.BLACK);
        playerView=new PlayerView(this); playerView.setUseController(true); playerView.setKeepContentOnPlayerReset(true); playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        stage.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        progress=new ProgressBar(this); stage.addView(progress,new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.CENTER));
        error=new TextView(this); error.setTextColor(Color.WHITE); error.setTextSize(15); error.setGravity(Gravity.CENTER); error.setPadding(dp(24),dp(20),dp(24),dp(20)); error.setBackground(round(Color.argb(235,22,27,37),18)); error.setVisibility(View.GONE);
        FrameLayout.LayoutParams elp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER); elp.setMargins(dp(24),0,dp(24),0); stage.addView(error,elp);
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1f));

        bottomBar=new LinearLayout(this); bottomBar.setGravity(Gravity.CENTER_VERTICAL); bottomBar.setPadding(dp(12),dp(8),dp(12),dp(8)); bottomBar.setBackgroundColor(Color.rgb(7,13,23));
        status=new TextView(this); status.setTextColor(Color.rgb(170,185,207)); status.setTextSize(12); bottomBar.addView(status,new LinearLayout.LayoutParams(0,-2,1f));
        retry=new Button(this); retry.setText("Réessayer"); retry.setAllCaps(false); retry.setVisibility(View.GONE); retry.setOnClickListener(v->play(0)); bottomBar.addView(retry);
        root.addView(bottomBar);
        setContentView(root);
        SystemBars.attach(this, root);
    }

    private void toggleFullscreen() {
        immersive = !immersive;
        topBar.setVisibility(immersive ? View.GONE : View.VISIBLE);
        bottomBar.setVisibility(immersive ? View.GONE : View.VISIBLE);
        SystemBars.setImmersive(this, immersive);
        fullscreen.setText(immersive ? "↙" : "⛶");
    }

    @Override public void onBackPressed() {
        if (immersive) { toggleFullscreen(); return; }
        super.onBackPressed();
    }

    private void play(int at) {
        if(at<0||at>=candidates.size()){ showError("Aucune source n'a pu être lue."); return; }
        index=at; releasePlayer(); Candidate c=candidates.get(index);
        progress.setVisibility(View.VISIBLE); error.setVisibility(View.GONE); retry.setVisibility(View.GONE);
        status.setText("Connexion au direct…"); sourceLabel.setText((c.provider.isEmpty()?"Source PiiWii":c.provider)+"  •  "+(index+1)+"/"+candidates.size());
        try {
            HashMap<String,String> headers=new HashMap<>();
            if(!c.referrer.isEmpty()){ headers.put("Referer",c.referrer); headers.put("Origin",origin(c.referrer)); }
            DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(12000).setReadTimeoutMs(20000);
            http.setUserAgent(c.userAgent.isEmpty()?"Mozilla/5.0 (Linux; Android) PiiWiiTV/0.4.0":c.userAgent);
            if(!headers.isEmpty()) http.setDefaultRequestProperties(headers);
            player=new ExoPlayer.Builder(this).build(); playerView.setPlayer(player);
            player.addListener(new Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){
                    if(state==Player.STATE_READY){ progress.setVisibility(View.GONE); status.setText("En direct"); }
                    else if(state==Player.STATE_BUFFERING){ progress.setVisibility(View.VISIBLE); status.setText("Chargement…"); }
                }
                @Override public void onPlayerError(PlaybackException e){
                    progress.setVisibility(View.GONE);
                    if(index+1<candidates.size()){ status.setText("Source indisponible, essai du secours…"); playerView.postDelayed(()->play(index+1),450); }
                    else showError("Lecture impossible.\n"+clean(e));
                }
            });
            MediaItem item=new MediaItem.Builder().setUri(c.url).setMimeType(isHls(c)?MimeTypes.APPLICATION_M3U8:null).build();
            if(isHls(c)) player.setMediaSource(new HlsMediaSource.Factory(http).createMediaSource(item)); else player.setMediaItem(item);
            player.prepare(); player.play();
        } catch(Throwable t){ if(index+1<candidates.size()) play(index+1); else showError("Erreur du lecteur : "+t.getClass().getSimpleName()); }
    }

    private boolean isHls(Candidate c){ String x=(c.type+" "+c.url).toLowerCase(); return x.contains("hls")||x.contains("m3u8")||c.url.contains("piiwii_tv_proxy"); }
    private String origin(String ref){ try{ android.net.Uri u=android.net.Uri.parse(ref); return u.getScheme()+"://"+u.getHost(); }catch(Exception e){return ref;} }
    private String clean(Throwable t){ String m=t.getMessage(); if(m==null||m.trim().isEmpty())m=t.getClass().getSimpleName(); return m.length()>180?m.substring(0,180):m; }
    private void showError(String s){ progress.setVisibility(View.GONE); error.setText(s); error.setVisibility(View.VISIBLE); status.setText("Lecture interrompue"); retry.setVisibility(View.VISIBLE); releasePlayer(); }

    private void parseCandidates(String raw){
        try{ JSONArray a=new JSONArray(raw==null?"[]":raw); for(int i=0;i<a.length();i++){ JSONObject o=a.optJSONObject(i); if(o==null)continue; String u=o.optString("url","").trim(); if(!(u.startsWith("http://")||u.startsWith("https://")))continue; Candidate c=new Candidate(); c.url=u; c.provider=o.optString("provider",""); c.type=o.optString("type",""); c.referrer=o.optString("referrer",""); c.userAgent=o.optString("user_agent",""); candidates.add(c); } }catch(Exception ignored){}
    }
    private GradientDrawable round(int color,int r){ GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(r)); return d; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    @Override protected void onStop(){ if(player!=null)player.pause(); super.onStop(); }
    @Override protected void onStart(){ super.onStart(); if(player!=null)player.play(); }
    @Override protected void onDestroy(){ SystemBars.setImmersive(this,false); releasePlayer(); super.onDestroy(); }
    private void releasePlayer(){ if(player!=null){ playerView.setPlayer(null); player.release(); player=null; } }
}

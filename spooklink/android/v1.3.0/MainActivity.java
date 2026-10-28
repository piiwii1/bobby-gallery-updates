package ch.piiwii.spooklink;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final int background = Color.rgb(13,12,26), card = Color.rgb(34,27,55), soft = Color.rgb(173,160,193), orange = Color.rgb(243,146,76), green=Color.rgb(137,230,170);
    private final ArrayList<String> pcNames=new ArrayList<>();
    private final ArrayList<String> pcHosts=new ArrayList<>();
    private final AtomicBoolean searching=new AtomicBoolean(false);
    private final Runnable repeatSearch=new Runnable(){public void run(){if(!isFinishing()&&!connected)discover(true);if(!isFinishing())ui.postDelayed(this,9000);}};
    private LinearLayout root;
    private FrameLayout pages;
    private TextView connectionStatus, countLabel, speedLabel, screenStatus;
    private Button[] tabs = new Button[3];
    private EditText messageInput;
    private SeekBar count, speed;
    private Spinner direction, pcPicker;
    private String host="", token="";
    private boolean connected=false, updatingPCs=false;
    private int page=0;
    private long lastCheck=0;

    private int dp(float v){return Math.round(getResources().getDisplayMetrics().density*v);}
    private GradientDrawable shape(int rgb, int radius){GradientDrawable d=new GradientDrawable();d.setColor(rgb);d.setCornerRadius(dp(radius));d.setStroke(dp(1),Color.rgb(73,57,104));return d;}
    private LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-1,1f);}
    private void space(LinearLayout x,int height){View v=new View(this);x.addView(v,lp(1,height));}
    private TextView text(String value,int size,int color,boolean strong){TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(size);v.setGravity(Gravity.CENTER_VERTICAL);if(strong)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String text,int color,int font){Button b=new Button(this);b.setText(text);b.setTextSize(font);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(shape(color,14));b.setPadding(dp(3),dp(4),dp(3),dp(4));b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);return b;}
    private EditText field(String hint,boolean digits){EditText v=new EditText(this);v.setSingleLine(true);v.setTextSize(14);v.setTextColor(Color.WHITE);v.setHintTextColor(soft);v.setHint(hint);v.setPadding(dp(12),dp(7),dp(12),dp(7));v.setBackground(shape(Color.rgb(20,17,37),12));if(digits)v.setInputType(InputType.TYPE_CLASS_NUMBER);return v;}
    private Spinner spinner(String[] values){Spinner x=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);x.setAdapter(a);x.setBackground(shape(Color.rgb(24,21,42),10));return x;}
    private LinearLayout pageBase(String title,String subtitle){LinearLayout x=col();x.setPadding(dp(6),dp(8),dp(6),dp(8));x.addView(text(title,22,Color.WHITE,true),lp(-1,34));x.addView(text(subtitle,12,soft,false),lp(-1,26));space(x,8);return x;}
    private void status(String msg,boolean good){ui.post(()->{connected=good; if(connectionStatus!=null){connectionStatus.setText((good?"● ":"○ ")+msg);connectionStatus.setTextColor(good?green:orange);}});}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(Build.VERSION.SDK_INT>=29) getWindow().setNavigationBarContrastEnforced(false);
        host=getPreferences(0).getString("host","");token=getPreferences(0).getString("token","");
        root=col();root.setBackgroundColor(background);
        root.setPadding(dp(14),0,dp(14),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());top=bars.top;bottom=bars.bottom;}
            else {top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            v.setPadding(dp(14),top+dp(2),dp(14),bottom+dp(3));return insets;
        });
        setContentView(root);root.requestApplyInsets();
        LinearLayout head=row();head.setPadding(dp(4),dp(4),dp(4),dp(4));root.addView(head,lp(-1,75));
        TextView icon=text("👻",30,Color.WHITE,true);head.addView(icon,lp(49,-1));
        LinearLayout titles=col();titles.setGravity(Gravity.CENTER_VERTICAL);head.addView(titles,new LinearLayout.LayoutParams(0,-1,1));
        titles.addView(text("SpookLink",24,Color.WHITE,true));
        connectionStatus=text("○ Recherche du PC…",11,orange,true);connectionStatus.setSingleLine(true);connectionStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(connectionStatus);
        head.setOnClickListener(v->showPage(2));
        pages=new FrameLayout(this);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1f));
        buildEffects();buildMessages();buildSettings();
        LinearLayout nav=row();nav.setPadding(dp(2),dp(7),dp(2),dp(3));root.addView(nav,lp(-1,69));
        String[] labels={"👻  Effets","💬  Messages","⚙  Réglages"};
        for(int i=0;i<3;i++){final int idx=i;Button b=button(labels[i],card,12);tabs[i]=b;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(3),0,dp(3),0);nav.addView(b,p);b.setOnClickListener(v->showPage(idx));}
        showPage(0);
        if(!host.isEmpty()&&!token.isEmpty())verifyKnown();else discover(false);
        ui.postDelayed(repeatSearch,9000);
    }
    private void putPage(LinearLayout p){pages.addView(p,new FrameLayout.LayoutParams(-1,-1));p.setVisibility(View.GONE);}
    private void showPage(int id){page=id;for(int i=0;i<pages.getChildCount();i++)pages.getChildAt(i).setVisibility(i==id?View.VISIBLE:View.GONE);for(int i=0;i<tabs.length;i++)tabs[i].setBackground(shape(i==id?Color.rgb(150,78,57):card,13));}
    private void buildEffects(){LinearLayout p=pageBase("Animations","Appuie pour envoyer sur l'écran choisi.");putPage(p);
        String[][] all={{"👻\nFantôme","ghost",""},{"🎃\nCitrouille","pumpkin",""},{"🦇\nChauve-souris","bat",""},{"💀\nCrâne","emoji","💀"},{"🕷\nAraignée","emoji","🕷"},{"👾\nMonstre","emoji","👾"},{"❤️\nCœur","heart",""},{"⭐\nÉtoile","star",""},{"😂\nRire","emoji","😂"},{"🎉\nFête","emoji","🎉"},{"🔥\nFeu","emoji","🔥"},{"👋\nSalut","emoji","👋"}};
        for(int r=0;r<4;r++){LinearLayout line=row();LinearLayout.LayoutParams rowP=new LinearLayout.LayoutParams(-1,0,1);rowP.bottomMargin=dp(6);p.addView(line,rowP);for(int c=0;c<3;c++){String[] a=all[r*3+c];Button b=button(a[0],card,14);b.setGravity(Gravity.CENTER);b.setAllCaps(false); // Button API in Android: inherited TextView setAllCaps
            LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-1,1);cell.setMargins(dp(3),0,dp(3),0);line.addView(b,cell);b.setOnClickListener(v->send(a[1],a[2]));}}
    }
    private void buildMessages(){LinearLayout p=pageBase("Messages","Ton texte traversera l'écran du PC.");putPage(p);
        p.addView(text("Écris un message",15,Color.WHITE,true),lp(-1,31));
        messageInput=field("Hello ! 👋",false);p.addView(messageInput,lp(-1,52));space(p,9);
        Button send=button("➤   Envoyer le message",Color.rgb(181,91,37),16);p.addView(send,lp(-1,53));send.setOnClickListener(v->{String message=messageInput.getText().toString().trim();if(message.isEmpty()){status("Écris un message",false);return;}send("message",message);});
        space(p,16);p.addView(text("Messages rapides",16,Color.WHITE,true),lp(-1,34));
        String[] shortTexts={"Hello ! 👋","Bravo ! 🎤","Booouh ! 👻","Bon karaoké ! 🎶","Surprise ! ✨","Bonne soirée 🎉"};
        for(int i=0;i<3;i++){LinearLayout line=row();LinearLayout.LayoutParams size=lp(-1,52);size.bottomMargin=dp(6);p.addView(line,size);for(int j=0;j<2;j++){String value=shortTexts[i*2+j];Button b=button(value,card,13);LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-1,1);cell.setMargins(dp(3),0,dp(3),0);line.addView(b,cell);b.setOnClickListener(v->send("message",value));}}
    }
    private void buildSettings(){LinearLayout p=pageBase("Réglages","PC sur le même Wi-Fi · connexion automatique.");putPage(p);
        p.addView(text("Ordinateurs SpookLink détectés",14,orange,true),lp(-1,29));
        pcPicker=spinner(new String[]{"Recherche en cours…"});p.addView(pcPicker,lp(-1,47));space(p,8);
        Button find=button("⌕   Rechercher les ordinateurs",Color.rgb(75,62,109),13);p.addView(find,lp(-1,46));find.setOnClickListener(v->discover(false));
        pcPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View view,int index,long id){
            if(updatingPCs||index<0||index>=pcHosts.size())return;
            String address=pcHosts.get(index);if(address.equals(host)&&connected)return;
            host=address;token=getPreferences(0).getString("token_"+address,"");getPreferences(0).edit().putString("host",address).apply();
            connected=false;status("Connexion au PC sélectionné…",false);worker.execute(()->connectTo(address));
        }});
        space(p,15);p.addView(text("Association sans code",14,orange,true),lp(-1,27));
        TextView help=text("La première fois, sur ton PC : icône SpookLink près de l'horloge → Autoriser mon téléphone (2 min). Ensuite, connexion automatique.",12,soft,false);
        help.setGravity(Gravity.TOP|Gravity.LEFT);p.addView(help,lp(-1,78));
        p.addView(text("Écran de diffusion",13,orange,true),lp(-1,24));
        screenStatus=text("Le moniteur est choisi sur Windows : icône SpookLink → Réglages / écran.",12,soft,false);
        screenStatus.setGravity(Gravity.TOP|Gravity.LEFT);p.addView(screenStatus,lp(-1,50));
        space(p,2);p.addView(text("Nombre de personnages",13,Color.WHITE,true),lp(-1,22));
        count=new SeekBar(this);count.setMax(11);count.setProgress(2);p.addView(count,lp(-1,32));countLabel=text("3 personnages",11,soft,false);p.addView(countLabel,lp(-1,19));
        count.setOnSeekBarChangeListener(new SimpleSeek(()->countLabel.setText((count.getProgress()+1)+" personnages")));
        p.addView(text("Vitesse",13,Color.WHITE,true),lp(-1,21));
        speed=new SeekBar(this);speed.setMax(9);speed.setProgress(4);p.addView(speed,lp(-1,30));speedLabel=text("5 / 10",11,soft,false);p.addView(speedLabel,lp(-1,18));
        speed.setOnSeekBarChangeListener(new SimpleSeek(()->speedLabel.setText((speed.getProgress()+1)+" / 10")));
        p.addView(text("Direction",13,Color.WHITE,true),lp(-1,21));
        direction=spinner(new String[]{"Aléatoire","Gauche vers droite","Droite vers gauche"});p.addView(direction,lp(-1,38));
    }
    private static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {final Runnable change;SimpleSeek(Runnable action){change=action;}public void onProgressChanged(SeekBar bar,int value,boolean fromUser){change.run();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}}
    private String cleanHost(String raw){return raw.trim().replace("http://","").replace("https://","").replaceAll(":8787/?$","").replaceAll("/$","");}
    private void saveConnection(String address,String newToken){host=address;token=newToken;getPreferences(0).edit().putString("host",address).putString("token_"+address,newToken).putString("token",newToken).apply();}
    private void verifyKnown(){status("Reconnexion automatique…",false);final String address=host;worker.execute(()->{
        try{JSONObject info=get(address,"/api/screens",token);connectionEstablished(address,info);}
        catch(Exception e){discoverBackground(true);}
    });}
    private void discover(boolean auto){if(!searching.compareAndSet(false,true))return;if(!auto)status("Recherche sur le Wi-Fi…",false);
        worker.execute(()->{try{discoverBackground(auto);}finally{searching.set(false);}});
    }
    private void discoverBackground(boolean auto){LinkedHashMap<String,String> found=new LinkedHashMap<>();
        try(DatagramSocket sock=new DatagramSocket()){
            sock.setBroadcast(true);sock.setSoTimeout(600);
            byte[] msg="SPOOK_DISCOVER_V1".getBytes(StandardCharsets.UTF_8);
            sock.send(new DatagramPacket(msg,msg.length,InetAddress.getByName("255.255.255.255"),8788));
            try{WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);DhcpInfo dhcp=wifi.getDhcpInfo();if(dhcp!=null){int bc=(dhcp.ipAddress&dhcp.netmask)|~dhcp.netmask;byte[] addr={(byte)bc,(byte)(bc>>8),(byte)(bc>>16),(byte)(bc>>24)};sock.send(new DatagramPacket(msg,msg.length,InetAddress.getByAddress(addr),8788));}}catch(Exception ignored){}
            long until=System.currentTimeMillis()+2100;
            while(System.currentTimeMillis()<until){
                byte[] bytes=new byte[256];DatagramPacket packet=new DatagramPacket(bytes,bytes.length);
                try{sock.receive(packet);}catch(SocketTimeoutException ignored){continue;}
                String data=new String(packet.getData(),0,packet.getLength(),StandardCharsets.UTF_8);
                if(!data.startsWith("SPOOK_SERVER_V1|"))continue;
                String addr=packet.getAddress().getHostAddress();String[] fields=data.split("\\|",-1);
                String label=fields.length>1&&!fields[1].isEmpty()?fields[1]:"PC SpookLink";
                found.put(addr,label+" · "+addr);
            }
        }catch(Exception ignored){}
        if(found.isEmpty()){if(!connected)status("Aucun PC trouvé sur ce Wi-Fi",false);return;}
        ui.post(()->{
            updatingPCs=true;pcNames.clear();pcHosts.clear();
            for(String ip:found.keySet()){pcHosts.add(ip);pcNames.add(found.get(ip));}
            ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,pcNames);pcPicker.setAdapter(adapter);
            int idx=pcHosts.indexOf(host);pcPicker.setSelection(idx<0?0:idx);updatingPCs=false;
        });
        String address=found.containsKey(host)?host:(found.size()==1?found.keySet().iterator().next():"");
        if(address.isEmpty()){status("Plusieurs PC trouvés · choisis un ordinateur",false);return;}
        if(connected&&address.equals(host))return;
        connectTo(address);
    }
    private void connectTo(String address){try{
        String saved=getPreferences(0).getString("token_"+address,address.equals(host)?token:"");
        if(!saved.isEmpty()){
            try{JSONObject response=get(address,"/api/screens",saved);token=saved;connectionEstablished(address,response);return;}catch(Exception ignored){}
        }
        // First pairing is explicitly enabled from the PC's tray icon; no PIN or manual IP.
        JSONObject response=post(address,"/api/pair-automatic",new JSONObject(),"");
        String newToken=response.getString("token");saveConnection(address,newToken);
        JSONObject info=get(address,"/api/screens",newToken);connectionEstablished(address,info);
    }catch(Exception e){status("PC trouvé · autorise le téléphone depuis l'icône du PC",false);}}
    private void connectionEstablished(String address,JSONObject data){saveConnection(address,token);lastCheck=System.currentTimeMillis();
        status("Connecté · "+address,true);
        ui.post(()->{if(screenStatus!=null)screenStatus.setText("Écran ciblé sur le PC · changement uniquement dans Windows.");});
    }
    private void send(String kind,String text){if(token.isEmpty()||host.isEmpty()||!connected){showPage(2);status("PC non connecté · cherche sur le Wi-Fi",false);return;}
        int quantity=count.getProgress()+1,velocity=speed.getProgress()+1,where=direction.getSelectedItemPosition();String move=where==1?"left":where==2?"right":"random";
        worker.execute(()->{try{JSONObject payload=new JSONObject();payload.put("kind",kind);payload.put("text",text);payload.put("count",quantity);payload.put("speed",velocity);payload.put("direction",move);
            post(host,"/api/send",payload,token);status("Envoyé ! ✨",true);
        }catch(Exception e){status("Échec Wi-Fi : "+e.getMessage(),false);discoverBackground(true);}});
    }
    @Override protected void onResume(){super.onResume();if(root!=null&&System.currentTimeMillis()-lastCheck>12000){if(!token.isEmpty()&&!host.isEmpty())verifyKnown();else discover(true);}}
    private JSONObject get(String address,String endpoint,String authorization)throws Exception{return request(address,endpoint,null,authorization);}
    private JSONObject post(String address,String endpoint,JSONObject payload,String authorization)throws Exception{return request(address,endpoint,payload,authorization);}
    private JSONObject request(String address,String endpoint,JSONObject payload,String authorization)throws Exception{
        URL url=new URL("http://"+cleanHost(address)+":8787"+endpoint);HttpURLConnection con=(HttpURLConnection)url.openConnection();
        con.setConnectTimeout(2800);con.setReadTimeout(2800);con.setRequestMethod(payload==null?"GET":"POST");
        if(!authorization.isEmpty())con.setRequestProperty("X-Spook-Token",authorization);
        if(payload!=null){con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=con.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}}
        int code=con.getResponseCode();InputStream stream=code<400?con.getInputStream():con.getErrorStream();if(stream==null)throw new IOException("HTTP "+code);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[1024];int n;try(InputStream in=stream){while((n=in.read(buf))!=-1)bytes.write(buf,0,n);};con.disconnect();if(code>=400)throw new IOException(code==403?"Non associé":"HTTP "+code);return new JSONObject(bytes.toString("UTF-8"));
    }
    @Override protected void onDestroy(){ui.removeCallbacks(repeatSearch);super.onDestroy();worker.shutdownNow();}
}
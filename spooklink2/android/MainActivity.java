package ch.piiwii.spooklink;
import android.app.*;import android.os.*;import android.content.*;import android.net.*;import android.net.wifi.*;
import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;
import org.json.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;
import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
 final int BG=0xff10121c,CARD=0xff1c2232,BLUE=0xff7866df,PALE=0xffaeb8cc,WHITE=-1;
 final ExecutorService worker=Executors.newSingleThreadExecutor(); final Handler ui=new Handler(Looper.getMainLooper());
 final AtomicBoolean scanning=new AtomicBoolean(false);
 final LinkedHashMap<String,String> found=new LinkedHashMap<>();
 android.content.SharedPreferences prefs; String id,host="",token="",preferred="",tab="effects";
 boolean connected=false;int count=3,speed=5;String direction="random";
 LinearLayout pageView,footer;TextView status,subtitle;Spinner choices;
 final Runnable pulse=new Runnable(){public void run(){if(!isFinishing()){if(connected)check();else scan();ui.postDelayed(this,7500);}}};
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
 GradientDrawable bg(int color){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(15));return g;}
 TextView t(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 Button b(String s){Button v=new Button(this);v.setText(s);v.setTextSize(14);v.setTextColor(WHITE);v.setAllCaps(false);v.setBackground(bg(CARD));return v;}
 void note(String msg,boolean good){ui.post(()->{status.setText(msg);status.setTextColor(good?0xff55e2a8:PALE);});}
 void connection(boolean ok,String pc){ui.post(()->{connected=ok;subtitle.setText(ok?"●  Connecté · "+pc:"○  Recherche du PC sur le Wi-Fi");subtitle.setTextColor(ok?0xff55e2a8:PALE);});}
 @Override public void onCreate(Bundle saved){super.onCreate(saved);prefs=getSharedPreferences("SpookLink2",0);
 id=prefs.getString("id","");if(id.length()!=32){id=UUID.randomUUID().toString().replace("-","");prefs.edit().putString("id",id).commit();}
 preferred=prefs.getString("pc","");
 getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
 LinearLayout root=col();root.setBackgroundColor(BG);root.setPadding(dp(15),dp(8),dp(15),dp(8));setContentView(root);
 TextView brand=t("👻  SpookLink",25,WHITE);root.addView(brand,lp(-1,46));
 subtitle=t("○  Recherche du PC sur le Wi-Fi",13,PALE);root.addView(subtitle,lp(-1,28));
 status=t("Détection automatique…",12,PALE);status.setMaxLines(2);root.addView(status,lp(-1,43));
 pageView=col();LinearLayout.LayoutParams middle=new LinearLayout.LayoutParams(-1,0,1);root.addView(pageView,middle);
 footer=row();root.addView(footer,lp(-1,70));
 for(String[] nav:new String[][]{{"👻 Effets","effects"},{"✉ Messages","message"},{"⚙ Réglages","settings"}}){
  Button z=b(nav[0]);LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(0,-1,1);x.setMargins(dp(3),dp(6),dp(3),dp(4));footer.addView(z,x);z.setTag(nav[1]);z.setOnClickListener(v->show(nav[1]));}
 show("effects");ui.post(pulse);
 }
 @Override protected void onDestroy(){ui.removeCallbacks(pulse);worker.shutdownNow();super.onDestroy();}
 void show(String section){tab=section;pageView.removeAllViews();
 for(int i=0;i<footer.getChildCount();i++){Button z=(Button)footer.getChildAt(i);z.setBackground(bg(z.getTag().equals(section)?BLUE:CARD));}
 LinearLayout card=col();card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(bg(0xff191e2d));pageView.addView(card,lp(-1,-1));
 if(section.equals("effects"))effects(card);else if(section.equals("message"))messages(card);else settings(card);
 }
 void title(LinearLayout c,String str){TextView v=t(str,18,WHITE);v.setTypeface(null,1);c.addView(v,lp(-1,43));}
 void effects(LinearLayout c){title(c,"Envoyer une animation");
 String[][] labels={{"👻  Fantôme","🎃  Citrouille"},{"🦇  Chauve-souris","❤️  Cœur"},{"⭐  Étoile","🎉  Emoji"}};
 String[][] kinds={{"ghost","pumpkin"},{"bat","heart"},{"star","emoji"}};
 for(int i=0;i<3;i++){LinearLayout r=row();LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,0,1);h.bottomMargin=dp(8);c.addView(r,h);
 for(int j=0;j<2;j++){String kind=kinds[i][j];Button z=b(labels[i][j]);LinearLayout.LayoutParams w=new LinearLayout.LayoutParams(0,-1,1);if(j==1)w.leftMargin=dp(9);r.addView(z,w);z.setOnClickListener(v->send(kind,kind.equals("emoji")?"🎉":""));}}
 TextView a=t("Nombre : "+count+"  ·  Vitesse : "+speed,13,PALE);c.addView(a,lp(-1,38));
 LinearLayout sliders=row();c.addView(sliders,lp(-1,46));
 SeekBar q=new SeekBar(this);q.setMax(7);q.setProgress(count-1);sliders.addView(q,new LinearLayout.LayoutParams(0,-1,1));
 SeekBar v=new SeekBar(this);v.setMax(9);v.setProgress(speed-1);sliders.addView(v,new LinearLayout.LayoutParams(0,-1,1));
 q.setOnSeekBarChangeListener(change(p->{count=p+1;a.setText("Nombre : "+count+"  ·  Vitesse : "+speed);}));
 v.setOnSeekBarChangeListener(change(p->{speed=p+1;a.setText("Nombre : "+count+"  ·  Vitesse : "+speed);}));
 LinearLayout dirs=row();c.addView(dirs,lp(-1,51));
 for(String[] d:new String[][]{{"↔ Aléatoire","random"},{"← Gauche","left"},{"Droite →","right"}}){Button z=b(d[0]);z.setBackground(bg(direction.equals(d[1])?BLUE:CARD));LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(0,dp(47),1);x.rightMargin=dp(5);dirs.addView(z,x);z.setOnClickListener(o->{direction=d[1];show("effects");});}
 }
 interface Changed{void take(int n);}
 SeekBar.OnSeekBarChangeListener change(Changed action){return new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int n,boolean user){if(user)action.take(n);}};}
 void messages(LinearLayout c){title(c,"Message personnalisé");EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(WHITE);input.setHintTextColor(PALE);input.setHint("Écris ton message");input.setBackground(bg(CARD));input.setPadding(dp(12),0,dp(12),0);c.addView(input,lp(-1,55));Button z=b("Envoyer ✨");z.setBackground(bg(BLUE));LinearLayout.LayoutParams x=lp(-1,53);x.topMargin=dp(11);c.addView(z,x);z.setOnClickListener(o->send("message",input.getText().toString().trim()));
 TextView quick=t("MESSAGES RAPIDES",13,PALE);c.addView(quick,lp(-1,47));
 for(String s:new String[]{"Hello 👋","BOUH ! 👻","Bravo ! 🎤","Joyeux Halloween 🎃"}){Button z2=b(s);LinearLayout.LayoutParams p=lp(-1,48);p.bottomMargin=dp(7);c.addView(z2,p);z2.setOnClickListener(o->send("message",s));}}
 void settings(LinearLayout c){title(c,"Connexion automatique");c.addView(t("Le premier téléphone est mémorisé sur le PC. Aucun code n'est demandé.",13,PALE),lp(-1,72));
 choices=new Spinner(this);c.addView(choices,lp(-1,54));refreshChoices();
 Button select=b("Choisir le PC");c.addView(select,lp(-1,52));select.setOnClickListener(o->{int n=choices.getSelectedItemPosition();if(n>=0&&n<found.size()){preferred=new ArrayList<>(found.keySet()).get(n);prefs.edit().putString("pc",preferred).apply();connected=false;connect(preferred);}});
 Button scan=b("↻  Rechercher sur le Wi-Fi");scan.setBackground(bg(BLUE));LinearLayout.LayoutParams w=lp(-1,53);w.topMargin=dp(17);c.addView(scan,w);scan.setOnClickListener(o->scan());
 c.addView(t("L'écran de diffusion se choisit dans les réglages Windows accessibles près de l'horloge.",13,PALE),lp(-1,103));}
 void refreshChoices(){if(choices==null)return;ArrayList<String> names=new ArrayList<>();int at=0,n=0;for(String h:found.keySet()){names.add(found.get(h)+" · "+h);if(h.equals(preferred))at=n;n++;}if(names.isEmpty())names.add("Aucun ordinateur détecté");choices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));choices.setSelection(at);}
 boolean wifi(){ConnectivityManager c=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(c==null||c.getActiveNetwork()==null)return false;NetworkCapabilities n=c.getNetworkCapabilities(c.getActiveNetwork());return n!=null&&n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);}
 InetAddress broadcast(){try{WifiManager w=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);DhcpInfo d=w.getDhcpInfo();int ip=(d.ipAddress&d.netmask)|~d.netmask;return InetAddress.getByAddress(new byte[]{(byte)ip,(byte)(ip>>8),(byte)(ip>>16),(byte)(ip>>24)});}catch(Exception e){return null;}}
 void scan(){if(!scanning.compareAndSet(false,true))return;worker.execute(()->{try{discover();}finally{scanning.set(false);}});}
 void discover(){if(!wifi()){connection(false,"");note("Connecte le téléphone au même Wi-Fi que le PC.",false);return;}
 LinkedHashMap<String,String> list=new LinkedHashMap<>();byte[] data="SPOOKLINK2_DISCOVER".getBytes(StandardCharsets.UTF_8);
 try(DatagramSocket s=new DatagramSocket()){s.setBroadcast(true);s.setSoTimeout(300);
 InetAddress target=broadcast();if(target!=null)s.send(new DatagramPacket(data,data.length,target,8798));
 s.send(new DatagramPacket(data,data.length,InetAddress.getByName("255.255.255.255"),8798));
 long until=System.currentTimeMillis()+1800;while(System.currentTimeMillis()<until){try{byte[] buf=new byte[250];DatagramPacket p=new DatagramPacket(buf,buf.length);s.receive(p);String r=new String(p.getData(),0,p.getLength(),StandardCharsets.UTF_8);if(r.startsWith("SPOOKLINK2|")){String[] bits=r.split("\\|");if(bits.length==3&&bits[2].equals("8797"))list.put(p.getAddress().getHostAddress(),bits[1]);}}catch(java.net.SocketTimeoutException ignored){}}}catch(Exception e){note("Recherche réseau : "+e.getMessage(),false);}
 if(list.isEmpty()&&!preferred.isEmpty()){try{JSONObject h=http(preferred,"/v2/hello","GET",null,"");if(h.optInt("protocol")==2)list.put(preferred,h.optString("name","PC"));}catch(Exception ignored){}}
 ui.post(()->{found.clear();found.putAll(list);refreshChoices();});
 String target="";if(!preferred.isEmpty()&&list.containsKey(preferred))target=preferred;else if(list.size()==1)target=list.keySet().iterator().next();
 if(target.isEmpty()){connection(false,"");note(list.size()>1?"Plusieurs PC détectés : choisis un PC dans Réglages.":"PC introuvable · vérifie le Wi-Fi et le pare-feu Windows.",false);return;}doConnect(target);
 }
 void connect(String h){worker.execute(()->doConnect(h));}
 void doConnect(String h){try{JSONObject hello=http(h,"/v2/hello","GET",null,"");if(hello.optInt("protocol")!=2)throw new Exception("Protocole incompatible");
 JSONObject data=new JSONObject();data.put("device_id",id);JSONObject result=http(h,"/v2/connect","POST",data,"");String newToken=result.getString("token");if(newToken.isEmpty())throw new Exception("Jeton absent");
 host=h;token=newToken;preferred=h;prefs.edit().putString("pc",h).apply();connection(true,result.optString("name",h));note("Connecté ! Tu peux envoyer tes effets.",true);
 }catch(Exception e){connection(false,"");note("Connexion : "+e.getMessage(),false);}}
 void check(){String h=host,key=token;if(h.isEmpty()||key.isEmpty()){connection(false,"");scan();return;}
 worker.execute(()->{try{http(h,"/v2/status","GET",null,key);}catch(Exception e){connection(false,"");note("PC perdu · reconnexion automatique…",false);discover();}});}
 void send(String kind,String msg){if(!connected){note("Recherche du PC…",false);scan();return;}if(kind.equals("message")&&msg.isEmpty()){note("Écris un message.",false);return;}
 String h=host,key=token,dir=direction;int q=count,v=speed;
 worker.execute(()->{try{JSONObject j=new JSONObject();j.put("kind",kind);j.put("text",msg);j.put("count",kind.equals("message")?1:q);j.put("speed",v);j.put("direction",dir);
 http(h,"/v2/send","POST",j,key);note("Animation envoyée ! ✨",true);}catch(Exception e){connection(false,"");note("Envoi : "+e.getMessage(),false);discover();}});}
 JSONObject http(String h,String route,String method,JSONObject request,String auth)throws Exception{
 HttpURLConnection c=(HttpURLConnection)new URL("http://"+h+":8797"+route).openConnection();c.setRequestMethod(method);c.setConnectTimeout(1900);c.setReadTimeout(2500);c.setRequestProperty("X-Spook-Token",auth);c.setRequestProperty("Accept","application/json");
 try{if(request!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(request.toString().getBytes(StandardCharsets.UTF_8));}}
 int code=c.getResponseCode();InputStream input=code>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream();if(input!=null){byte[] z=new byte[512];int n;while((n=input.read(z))>0&&b.size()<8000)b.write(z,0,n);input.close();}
 JSONObject r=new JSONObject(b.toString("UTF-8"));if(code>=400)throw new Exception(r.optString("error","HTTP "+code));return r;}finally{c.disconnect();}}
}
package ch.piiwii.cartesorganizer;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

public final class MainActivity extends Activity {
    private static final int BG=Color.rgb(5,18,33), PANEL=Color.rgb(10,33,53), FG=Color.WHITE,
        MUTED=Color.rgb(163,187,209), ACCENT=Color.rgb(32,150,218), GREEN=Color.rgb(13,148,102);
    private static final String API="wp-json/cartes-organizer/v1/";
    private final ExecutorService pool=Executors.newFixedThreadPool(4);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final android.util.LruCache<String,Bitmap> thumbs=new android.util.LruCache<>(32);
    private LinearLayout root;
    private JSONObject state;
    private String site="", username="", password="";
    private boolean disconnected=false;
    private static final String LOCAL_CACHE="offline_manifest_030";
    private Pending pendingShare;
    private boolean returningFromShare=false;
    private boolean archives=false;
    private String page="overview";
    private boolean navExpanded=false;
    private String pairVerifier="";
    private String searchQuery="";
    private int chosenYear=0;
    private static final int PICK_PHOTO=41;

    private interface Result {void done(byte[] bytes) throws Exception;}
    private interface Choice {void chosen(int friendId,int slot);}
    private static final class Pending {
        final int card,friend,slot;
        Pending(int c,int f,int s){card=c;friend=f;slot=s;}
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        android.content.SharedPreferences pref=getPreferences(MODE_PRIVATE);
        site="https://piiwii.ch"; username=pref.getString("user","");
        try {password=readSecret(pref.getString("auth",""));}catch(Exception e){password="";}
        if(!handleDeepLink(getIntent())) {
            loadSavedState();
            renderScreen();
            if(!username.isEmpty()&&!password.isEmpty())refresh();
            else {disconnected=true;toast("Cartes Organizer : mode local — synchronisation privée non activée");}
        }
    }
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private void startScreen(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(7),dp(10),dp(8));root.setBackgroundColor(BG);setContentView(root);
    }
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private android.graphics.drawable.GradientDrawable shape(int c,int radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(radius));return d;
    }
    private Button button(String s,Runnable a){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(FG);b.setTextSize(12);b.setBackground(shape(PANEL,9));b.setPadding(dp(7),0,dp(7),0);b.setOnClickListener(v->a.run());return b;
    }
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void loading(String message){startScreen();TextView t=text(message,16,FG);t.setGravity(Gravity.CENTER);root.addView(t,new LinearLayout.LayoutParams(-1,0,1));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void error(String s){new AlertDialog.Builder(this).setTitle("Cartes Organizer").setMessage(s).setPositiveButton("OK",null).show();}
    private void loadSavedState(){
        try{String saved=getPreferences(MODE_PRIVATE).getString(LOCAL_CACHE,"");
            state=saved.isEmpty()?emptyState():new JSONObject(saved);
        }catch(Exception e){state=emptyState();}
    }
    private JSONObject emptyState(){
        JSONObject obj=new JSONObject();try{
            obj.put("cards",new JSONArray());obj.put("friends",new JSONArray());obj.put("copies",new JSONArray());
            obj.put("archive_years",new JSONArray());obj.put("events",new JSONArray());
        }catch(Exception ignored){}return obj;
    }
    private void persist(){
        if(state!=null)getPreferences(MODE_PRIVATE).edit().putString(LOCAL_CACHE,state.toString()).apply();
    }
    private int nextId(JSONArray arr){int max=0;for(int k=0;k<arr.length();k++)max=Math.max(max,arr.optJSONObject(k)==null?0:arr.optJSONObject(k).optInt("id"));return max+1;}
    private JSONObject dataObject(Object...pairs)throws JSONException{JSONObject d=new JSONObject();for(int k=0;k<pairs.length;k+=2)d.put(""+pairs[k],pairs[k+1]);return d;}
    private void record(String name){try{a("events").put(dataObject("action",name,"created_at",new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.FRANCE).format(new Date())));}catch(Exception ignored){}}
    private boolean offlineMode(){return username.isEmpty()||password.isEmpty();}
    private void localOperation(String op,Object...pairs){
        Map<String,String> values=new HashMap<>();for(int k=0;k<pairs.length;k+=2)values.put(""+pairs[k],""+pairs[k+1]);
        try{
            JSONArray cards=a("cards"),friends=a("friends"),copies=a("copies");
            int cardId=Integer.parseInt(values.getOrDefault("card_id","0")), friendId=Integer.parseInt(values.getOrDefault("friend_id","0")),copyId=Integer.parseInt(values.getOrDefault("copy_id","0"));
            JSONObject c=card(cardId),f=friend(friendId),cp=null;
            for(int k=0;k<copies.length();k++){JSONObject x=copies.optJSONObject(k);if(x!=null&&x.optInt("id")==copyId){cp=x;break;}}
            switch(op){
                case "friend_add":{
                    String name=values.getOrDefault("name","").trim();if(name.isEmpty())throw new Exception("Nom manquant");
                    friends.put(dataObject("id",nextId(friends),"name",name,"slots",Math.max(1,Integer.parseInt(values.getOrDefault("slots","1"))),"active",true));break;}
                case "friend_edit":if(f!=null){f.put("name",values.getOrDefault("name",f.optString("name")));f.put("slots",Math.max(1,Integer.parseInt(values.getOrDefault("slots","1"))));}break;
                case "friend_remove":if(f!=null)f.put("active",false);break;
                case "assign":if(c!=null){
                    copies.put(dataObject("id",nextId(copies),"card_id",cardId,"friend_id",friendId,"slot_no",Integer.parseInt(values.getOrDefault("slot_no","1")),"status","with_friend","snapshot_url",c.optString("framed_url",c.optString("original_url"))));
                    c.put("stage","with_friend");}break;
                case "move_copy":if(cp!=null){cp.put("friend_id",friendId);cp.put("slot_no",Integer.parseInt(values.getOrDefault("slot_no","1")));}break;
                case "sent":if(cp!=null)cp.put("status","sent");break;
                case "finalize":if(cp!=null){cp.put("status","archived");JSONObject ca=card(cp.optInt("card_id"));if(ca!=null)ca.put("stage","archived");}break;
                case "trash":if(c!=null)c.put("deleted_at",System.currentTimeMillis());break;
                case "restore":if(c!=null)c.put("deleted_at",JSONObject.NULL);break;
                case "rename":if(c!=null)c.put("title",values.getOrDefault("title",c.optString("title")));break;
                default:toast("Action non disponible hors ligne : "+op);return;
            }
            record("Local : "+op);persist();renderScreen();
        }catch(Exception e){error("Modification locale impossible : "+e.getMessage());}
    }
    private EditText edit(String hint,String val,int type){
        EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(val);e.setTextColor(FG);e.setHintTextColor(MUTED);e.setInputType(type);return e;
    }
    private void welcome(){
        startScreen();
        LinearLayout content=col();content.setPadding(dp(18),dp(34),dp(18),0);
        ImageView logo=new ImageView(this);logo.setImageResource(ch.piiwii.cartesorganizer.R.drawable.cartes_icon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(logo,lp(130,105));
        TextView title=text("Cartes Organizer",28,FG);title.setTypeface(null,1);content.addView(title,lp(-1,58));
        TextView sub=text("Ton espace privé de flyers et de cartes",15,MUTED);content.addView(sub,lp(-1,45));
        TextView notice=text("Connexion à piiwii.ch\n\nLa première autorisation de données privées peut être demandée par WordPress. Aucun identifiant ni mot de passe à saisir dans cette application. Ensuite, les ouvertures sont automatiques.",14,FG);
        notice.setLineSpacing(dp(5),1);notice.setPadding(dp(15),dp(20),dp(15),dp(20));notice.setBackground(shape(PANEL,13));content.addView(notice,lp(-1,-2));
        content.addView(button("Autoriser Cartes Organizer",()->launchConsent()),lp(-1,52));
        content.addView(text("Application native · Données privées · HTTPS",12,MUTED),lp(-1,57));
        root.addView(content);
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);handleDeepLink(intent);
    }
    private boolean handleDeepLink(Intent intent){
        Uri uri=intent==null?null:intent.getData();
        if(uri==null||!"cartesorganizer".equals(uri.getScheme()))return false;
        String action=uri.getHost(),newSite=uri.getQueryParameter("site");
        if(newSite!=null&&newSite.startsWith("https://")){
            String requestedSite=newSite.replaceAll("/+$","");
            if(!requestedSite.equals(site)){
                // Never send WordPress credentials from the previous site to a new host.
                username="";password="";state=null;
                getPreferences(MODE_PRIVATE).edit().remove("user").remove("auth").apply();
            }
            site=requestedSite;
            getPreferences(MODE_PRIVATE).edit().putString("site",site).apply();
        }
        if("wpauth".equals(action)){consumeConsent(uri);return true;}
        if("reject".equals(action)){welcome();return true;}
        if("setup".equals(action)){
            if(site.isEmpty()){error("Adresse du site manquante.");return true;}
            beginPairing(uri.getQueryParameter("nonce"));return true;
        }
        if("pair".equals(action)){
            String code=uri.getQueryParameter("code");
            if(pairVerifier.isEmpty()){try{pairVerifier=readSecret(getPreferences(MODE_PRIVATE).getString("pair_verifier",""));}catch(Exception e){pairVerifier="";}}
            if(code==null||pairVerifier.isEmpty()){
                error("Le lien de connexion a expiré. Retourne dans Réglages sur ton site et recommence.");welcome();return true;
            }
            exchangePairing(code);return true;
        }
        return false;
    }
    private void launchConsent(){
        welcome();
        try{
            byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);
            String id=android.util.Base64.encodeToString(bytes,android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING|android.util.Base64.NO_WRAP);
            getPreferences(MODE_PRIVATE).edit().putString("wp_state",storeSecret(id)).apply();
            Uri authorize=Uri.parse("https://piiwii.ch/wp-admin/authorize-application.php").buildUpon()
                .appendQueryParameter("app_name","Cartes Organizer Android — PiiWii")
                .appendQueryParameter("app_id","f347b234-f30d-44c9-acac-9bd496e0a7cb")
                .appendQueryParameter("success_url","cartesorganizer://wpauth?state="+Uri.encode(id))
                .appendQueryParameter("reject_url","cartesorganizer://reject?state="+Uri.encode(id)).build();
            Intent browser=new Intent(Intent.ACTION_VIEW,authorize);
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(browser);
        }catch(Exception e){error("Autorisation WordPress impossible : "+e.getMessage());}
    }
    private void consumeConsent(Uri uri){
        String expected="";
        try{expected=readSecret(getPreferences(MODE_PRIVATE).getString("wp_state",""));}catch(Exception ignored){}
        String supplied=uri.getQueryParameter("state");
        if(expected.isEmpty()||supplied==null||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){
            welcome();error("Réponse WordPress non reconnue.");return;
        }
        getPreferences(MODE_PRIVATE).edit().remove("wp_state").apply();
        String host=uri.getQueryParameter("site_url");
        String u=uri.getQueryParameter("user_login"),pass=uri.getQueryParameter("password");
        if(host==null||!"https://piiwii.ch".equals(host.replaceAll("/+$",""))||u==null||u.isEmpty()||pass==null||pass.isEmpty()){
            welcome();error("WordPress n’a pas autorisé Cartes Organizer.");return;
        }
        site="https://piiwii.ch";username=u;password=pass;
        try{
            getPreferences(MODE_PRIVATE).edit().putString("site",site).putString("user",u).putString("auth",storeSecret(pass)).apply();
            page="overview";loading("Synchronisation automatique…");refresh();
        }catch(Exception e){username="";password="";welcome();error("Impossible de sécuriser la connexion.");}
    }
    private void beginPairing(String nonce){
        if(nonce==null||nonce.isEmpty()){error("Liaison non autorisée. Ouvre Cartes Organizer /cartes/ depuis ta session WordPress et touche le bouton dans Réglages.");welcome();return;}
        try{
            byte[] random=new byte[32];new SecureRandom().nextBytes(random);
            pairVerifier=android.util.Base64.encodeToString(random,android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING);
            String challenge=android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(pairVerifier.getBytes(StandardCharsets.US_ASCII)),android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING);
            getPreferences(MODE_PRIVATE).edit().putString("pair_verifier",storeSecret(pairVerifier)).apply();
            loading("Liaison sécurisée avec WordPress…");
            Intent browser=new Intent(Intent.ACTION_VIEW,Uri.parse(site+"/cartes/?co_android_pair="+challenge+"&co_auth_nonce="+Uri.encode(nonce)));
            browser.addCategory(Intent.CATEGORY_BROWSABLE);startActivity(browser);
        }catch(Exception e){error("Impossible de démarrer la connexion : "+e.getMessage());}
    }
    private void exchangePairing(String code){
        loading("Connexion automatique en cours…");
        final String verifier=pairVerifier;pairVerifier="";
        getPreferences(MODE_PRIVATE).edit().remove("pair_verifier").apply();
        final byte[] payload=("code="+encode(code)+"&verifier="+encode(verifier)).getBytes(StandardCharsets.UTF_8);
        job(()->http(base()+"mobile/pair","POST",payload,"application/x-www-form-urlencoded; charset=UTF-8"),response->{
            JSONObject credentials=new JSONObject(new String(response,StandardCharsets.UTF_8));
            username=credentials.getString("user");password=credentials.getString("password");
            getPreferences(MODE_PRIVATE).edit().putString("site",site).putString("user",username).putString("auth",storeSecret(password)).apply();
            page="overview";refresh();
        });
    }
    private String base(){return site+"/" + API;}
    private String authorization(){return "Basic "+android.util.Base64.encodeToString((username+":"+password).getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);}
    private byte[] http(String url,String method,byte[] body,String contentType)throws Exception{
        if(url.startsWith("file://")){
            File file=new File(Uri.parse(url).getPath());
            if(!file.getCanonicalPath().startsWith(getFilesDir().getCanonicalPath()+File.separator))throw new IOException("Chemin fichier interdit");
            try(InputStream in=new FileInputStream(file)){return readAll(in);}
        }
        if(!url.startsWith("https://"))throw new IOException("Adresse HTTPS obligatoire.");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(18000);c.setReadTimeout(45000);c.setRequestMethod(method);c.setInstanceFollowRedirects(false);
        if(!username.isEmpty()&&!password.isEmpty())c.setRequestProperty("Authorization",authorization());c.setRequestProperty("Accept","application/json, image/*");
        if(body!=null){
            c.setDoOutput(true);c.setRequestProperty("Content-Type",contentType);
            c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()){out.write(body);}
        }
        int code=c.getResponseCode();
        try(InputStream in=code<400?c.getInputStream():c.getErrorStream()){
            byte[] bytes=in==null?new byte[0]:readAll(in);
            if(code>=300){
                String details=new String(bytes,StandardCharsets.UTF_8);
                if(details.length()>250)details=details.substring(0,250);
                throw new IOException("HTTP "+code+" : "+details);
            }
            return bytes;
        }finally{c.disconnect();}
    }
    private byte[] readAll(InputStream in)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[32768];int n;
        while((n=in.read(buf))!=-1){b.write(buf,0,n);if(b.size()>32*1024*1024)throw new IOException("Image trop volumineuse (>32 Mo).");}
        return b.toByteArray();
    }
    private void job(Callable<byte[]> work,Result ok){
        pool.execute(()->{
            try{
                byte[] out=work.call();
                runOnUiThread(()->{try{ok.done(out);}catch(Exception e){error(e.getMessage());}});
            }catch(Exception e){runOnUiThread(()->error(e.getMessage()==null?"Erreur réseau":e.getMessage()));}
        });
    }
    private void refresh(){
        job(()->http(base()+"manifest","GET",null,null),b->{
            JSONObject j=new JSONObject(new String(b,StandardCharsets.UTF_8));
            if(!j.has("cards")||!j.has("friends"))throw new JSONException("Réponse du plugin non reconnue. Installe Cartes Organizer 0.3.8.");
            state=j;disconnected=false;persist();
            getPreferences(MODE_PRIVATE).edit().putString("site",site).putString("user",username).putString("auth",storeSecret(password)).apply();
            renderScreen();
        });
    }
    private JSONArray a(String name){JSONArray v=state==null?null:state.optJSONArray(name);return v==null?new JSONArray():v;}
    private JSONObject item(JSONArray arr,int i){return arr.optJSONObject(i);}
    private JSONObject card(int id){JSONArray cards=a("cards");for(int i=0;i<cards.length();i++){JSONObject c=item(cards,i);if(c!=null&&c.optInt("id")==id)return c;}return null;}
    private JSONObject friend(int id){JSONArray x=a("friends");for(int i=0;i<x.length();i++){JSONObject f=item(x,i);if(f!=null&&f.optInt("id")==id)return f;}return null;}
    private JSONObject currentCopy(int friendId,int slot){JSONArray x=a("copies");for(int i=0;i<x.length();i++){JSONObject c=item(x,i);if(c!=null&&c.optInt("friend_id")==friendId&&c.optInt("slot_no")==slot&&"with_friend".equals(c.optString("status")))return c;}return null;}
    private boolean alive(JSONObject c){return c!=null&&c.isNull("deleted_at");}
    private void toolbar(String label){
        LinearLayout bar=row();bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0,0,0,dp(8));
        TextView burger=text("☰",24,FG);burger.setGravity(Gravity.CENTER);
        burger.setBackground(shape(PANEL,10));bar.addView(burger,lp(43,42));burger.setOnClickListener(v->showMenu());
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.cartes_icon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);bar.addView(logo,lp(43,42));
        TextView title=text(label,16,FG);title.setTypeface(null,1);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams name=new LinearLayout.LayoutParams(0,dp(44),1);name.leftMargin=dp(3);bar.addView(title,name);
        TextView refresh=text("⟳",26,FG);refresh.setGravity(Gravity.CENTER);bar.addView(refresh,lp(37,40));refresh.setOnClickListener(v->refresh());
        TextView add=text("+",27,Color.rgb(94,231,202));add.setGravity(Gravity.CENTER);bar.addView(add,lp(33,40));add.setOnClickListener(v->openPhoto());
        root.addView(bar);
        if(offlineMode()){
            TextView status=text("●  MODE LOCAL   ·   piiwii.ch non synchronisé",11,Color.rgb(238,196,120));
            status.setPadding(dp(12),dp(6),dp(10),dp(7));status.setBackground(shape(PANEL,7));
            root.addView(status,lp(-1,29));
        }
    }
    private void showMenu(){
        final Dialog dlg=new Dialog(this);
        LinearLayout container=col();container.setPadding(dp(13),dp(17),dp(13),dp(12));container.setBackgroundColor(Color.rgb(9,25,39));
        TextView h=text("MON ESPACE",11,MUTED);h.setPadding(dp(9),0,0,dp(10));container.addView(h);
        addMenuItem(container,"▣   Vue d’ensemble","overview",dlg);
        addMenuItem(container,"▤   Archives","archives",dlg);
        addMenuItem(container,"▧   Bibliothèque","library",dlg);
        TextView mg=text((navExpanded?"▾":"▸")+"   Gestion des cartes",15,FG);mg.setPadding(dp(11),dp(12),0,dp(12));container.addView(mg);
        mg.setOnClickListener(v->{navExpanded=!navExpanded;dlg.dismiss();showMenu();});
        if(navExpanded){
            addMenuItem(container,"     ◫  Flyers originaux","flyers",dlg);
            addMenuItem(container,"     ◷  Cartes en attente","pending",dlg);
            addMenuItem(container,"     ♧  Chez mes amis","friends",dlg);
            addMenuItem(container,"     ➤  Cartes envoyées","sent",dlg);
        }
        TextView tool=text("OUTILS",11,MUTED);tool.setPadding(dp(9),dp(16),0,dp(6));container.addView(tool);
        addMenuItem(container,"◴   Historique","history",dlg);
        addMenuItem(container,"▤   Corbeille","trash",dlg);
        addMenuItem(container,"⚙   Réglages","settings",dlg);
        TextView foot=text("●  Bibliothèque privée    ·    0.3.0",11,MUTED);foot.setPadding(dp(8),dp(20),0,0);container.addView(foot);
        ScrollView sc=new ScrollView(this);sc.addView(container);dlg.setContentView(sc);
        Window w=dlg.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(dp(286),-1);w.setGravity(Gravity.TOP|Gravity.LEFT);w.setDimAmount(.35f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
        dlg.show();w=dlg.getWindow();if(w!=null){w.setLayout(dp(286),-1);w.setGravity(Gravity.TOP|Gravity.LEFT);}
    }
    private void addMenuItem(LinearLayout parent,String text,String id,Dialog dlg){
        TextView item=text(text,14,id.equals(page)?FG:MUTED);
        item.setPadding(dp(13),0,dp(10),0);
        item.setBackground(shape(id.equals(page)?Color.rgb(23,58,72):Color.rgb(9,25,39),9));
        LinearLayout.LayoutParams p=lp(-1,42);p.bottomMargin=dp(3);parent.addView(item,p);
        item.setOnClickListener(v->{dlg.dismiss();page=id;renderScreen();});
    }
    private void renderScreen(){
        archives="archives".equals(page);
        if("archives".equals(page))renderArchives();
        else if("overview".equals(page))renderHome();
        else if("settings".equals(page))renderSettings();
        else if("history".equals(page))renderHistory();
        else if("trash".equals(page))renderTrash();
        else renderSection();
    }
    private boolean match(JSONObject c){return searchQuery.isEmpty()||c.optString("title","").toLowerCase(java.util.Locale.ROOT).contains(searchQuery.toLowerCase(java.util.Locale.ROOT));}
    private void renderSection(){
        startScreen();String title="Bibliothèque";
        switch(page){case "flyers":title="Flyers originaux";break;case "pending":title="Cartes en attente";break;case "friends":title="Chez mes amis";break;case "sent":title="Cartes envoyées";break;}
        toolbar(title);
        LinearLayout actions=row();actions.addView(button("⌕  Rechercher",()->{
            EditText field=edit("Rechercher une carte",searchQuery,InputType.TYPE_CLASS_TEXT);
            new AlertDialog.Builder(this).setTitle("Recherche").setView(field)
               .setPositiveButton("OK",(d,n)->{searchQuery=field.getText().toString().trim();renderScreen();})
               .setNeutralButton("Effacer",(d,n)->{searchQuery="";renderScreen();}).show();
        }),lp(-1,42));root.addView(actions);
        ScrollView scroll=new ScrollView(this);LinearLayout list=col();list.setPadding(dp(4),dp(8),dp(4),dp(16));scroll.addView(list);
        JSONArray cards=a("cards"),copies=a("copies"),friends=a("friends");int found=0;
        if("friends".equals(page)||"sent".equals(page)){
            for(int i=0;i<friends.length();i++){
                JSONObject f=item(friends,i);if(f==null||!f.optBoolean("active",true))continue;
                final int friendId=f.optInt("id");
                LinearLayout header=row();TextView label=text(f.optString("name"),16,FG);label.setTypeface(null,1);header.addView(label,new LinearLayout.LayoutParams(0,dp(43),1));
                header.addView(button("✎",()->editFriend(f)),lp(48,39));list.addView(header);
                HorizontalScrollView carousel=new HorizontalScrollView(this);carousel.setHorizontalScrollBarEnabled(false);LinearLayout strip=row();
                for(int slot=1;slot<=Math.min(24,f.optInt("slots",1));slot++){
                    JSONObject cp=null;
                    for(int j=0;j<copies.length();j++){JSONObject candidate=item(copies,j);if(candidate!=null&&candidate.optInt("friend_id")==friendId&&candidate.optInt("slot_no")==slot&&(("friends".equals(page)&&"with_friend".equals(candidate.optString("status")))||("sent".equals(page)&&"sent".equals(candidate.optString("status"))))){cp=candidate;break;}}
                    JSONObject c=cp==null?null:card(cp.optInt("card_id"));
                    strip.addView(tile(c,cp,f.optString("name")+" · "+slot));found++;
                }
                carousel.addView(strip);list.addView(carousel);
            }
            if("friends".equals(page))list.addView(button("+  Ajouter un ami",()->addFriend()),lp(-1,50));
        }else{
            for(int i=0;i<cards.length();i++){
                JSONObject c=item(cards,i);if(!alive(c)||!match(c))continue;
                if("flyers".equals(page)&&c.optBoolean("has_frame"))continue;
                if("pending".equals(page)&&(!"pending".equals(c.optString("stage"))||!c.optBoolean("has_frame")))continue;
                if("library".equals(page)||"flyers".equals(page)||"pending".equals(page)){
                    LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(shape(PANEL,9));
                    row.addView(tile(c,null,c.optBoolean("has_frame")?"pending":"original"));
                    String detail=c.optString("title")+"\n"+(c.optBoolean("has_frame")?"Carte bordée":"Flyer original");
                    TextView label=text(detail,13,FG);label.setPadding(dp(10),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,dp(100),1));
                    LinearLayout.LayoutParams margin=lp(-1,-2);margin.bottomMargin=dp(7);list.addView(row,margin);found++;
                }
            }
        }
        if(found==0)list.addView(text("Aucun élément dans cette rubrique.",14,MUTED),lp(-1,68));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void addFriend(){
        EditText field=edit("Prénom", "",InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Ajouter un ami").setView(field).setPositiveButton("Ajouter",(d,n)->operation("friend_add","name",field.getText().toString(),"slots",1)).setNegativeButton("Annuler",null).show();
    }
    private void editFriend(JSONObject f){
        EditText field=edit("Nom",f.optString("name"),InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Modifier "+f.optString("name")).setView(field)
           .setPositiveButton("Enregistrer",(d,n)->operation("friend_edit","friend_id",f.optInt("id"),"name",field.getText().toString(),"slots",f.optInt("slots",1)))
           .setNeutralButton("Supprimer",(d,n)->new AlertDialog.Builder(this).setTitle("Supprimer cet ami ?").setPositiveButton("Supprimer",(z,k)->operation("friend_remove","friend_id",f.optInt("id"))).setNegativeButton("Annuler",null).show())
           .setNegativeButton("Annuler",null).show();
    }
    private void renderHistory(){
        startScreen();toolbar("Historique");ScrollView sc=new ScrollView(this);LinearLayout l=col();sc.addView(l);
        JSONArray events=a("events");for(int i=0;i<events.length();i++){
            JSONObject e=item(events,i);if(e==null)continue;
            TextView t=text(e.optString("action")+"  ·  "+e.optString("created_at"),13,FG);
            t.setPadding(dp(12),dp(10),dp(10),dp(10));l.addView(t,lp(-1,57));
        }root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void renderTrash(){
        startScreen();toolbar("Corbeille");ScrollView sc=new ScrollView(this);LinearLayout l=col();sc.addView(l);
        JSONArray cards=a("cards");for(int i=0;i<cards.length();i++){
            JSONObject c=item(cards,i);if(c==null||alive(c))continue;
            LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.addView(tile(c,null,"original"));
            TextView name=text(c.optString("title"),14,FG);r.addView(name,new LinearLayout.LayoutParams(0,dp(76),1));
            r.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Restaurer la carte ?").setPositiveButton("Restaurer",(d,n)->operation("restore","card_id",c.optInt("id"))).setNegativeButton("Annuler",null).show());l.addView(r);
        }root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void renderSettings(){
        startScreen();toolbar("Réglages");TextView t=text("Cartes Organizer Android 0.3.0\n\nSite : piiwii.ch\nMode : "+(offlineMode()?"LOCAL (sans synchronisation privée)":"Synchronisation automatique")+"\n\nLes cartes créées localement sont conservées sur ce téléphone. Le plugin WordPress 0.3.8 ne permet pas l’accès privé anonyme.",14,FG);
        t.setPadding(dp(18),dp(20),dp(16),dp(25));t.setLineSpacing(dp(5),1);root.addView(t,lp(-1,-2));
        root.addView(button("⟳  Actualiser",()->{if(offlineMode()){loadSavedState();renderScreen();}else refresh();}),lp(-1,50));
    }
    private LinearLayout strip(String label,int count){
        LinearLayout section=col();section.setPadding(dp(8),dp(6),dp(8),dp(6));section.setBackground(shape(PANEL,9));
        LinearLayout header=row();
        TextView name=text(label,14,FG);name.setTypeface(null,1);header.addView(name,new LinearLayout.LayoutParams(0,dp(27),1));
        header.addView(text(String.valueOf(count),12,MUTED));
        section.addView(header);
        return section;
    }
    private void renderHome(){
        if(state==null)return;
        startScreen();toolbar("Vue d’ensemble");
        JSONArray cards=a("cards"),copies=a("copies"),friends=a("friends");
        ArrayList<JSONObject> flyers=new ArrayList<>(),pending=new ArrayList<>(),sent=new ArrayList<>();
        for(int i=0;i<cards.length();i++){
            JSONObject c=item(cards,i);if(!alive(c))continue;
            if("flyer".equals(c.optString("stage"))&&!c.optBoolean("has_frame"))flyers.add(c);
            if("pending".equals(c.optString("stage"))&&c.optBoolean("has_frame"))pending.add(c);
        }
        for(int i=0;i<copies.length();i++){
            JSONObject copy=item(copies,i);
            if(copy!=null&&"sent".equals(copy.optString("status"))&&alive(card(copy.optInt("card_id"))))sent.add(copy);
        }
        addStrip("Flyers originaux",flyers.size(),strip->{
            for(JSONObject c:flyers)strip.addView(tile(c,null,"original"));
        });
        addStrip("Cartes en attente",pending.size(),strip->{
            for(JSONObject c:pending)strip.addView(tile(c,null,"pending"));
        });
        int slots=0;for(int i=0;i<friends.length();i++){JSONObject f=item(friends,i);if(f!=null&&f.optBoolean("active",true))slots+=f.optInt("slots",1);}
        final int totalSlots=slots;
        addStrip("Cartes chez des amis",totalSlots,strip->{
            for(int i=0;i<friends.length();i++){
                JSONObject f=item(friends,i);if(f==null||!f.optBoolean("active",true))continue;
                int id=f.optInt("id");
                for(int n=1;n<=Math.min(24,f.optInt("slots",1));n++){
                    JSONObject copy=currentCopy(id,n);
                    JSONObject c=copy==null?null:card(copy.optInt("card_id"));
                    strip.addView(tile(c,copy,f.optString("name")+" · "+n));
                }
            }
        });
        addStrip("Cartes envoyées",sent.size(),strip->{
            for(JSONObject copy:sent)strip.addView(tile(card(copy.optInt("card_id")),copy,"sent"));
        });
        LinearLayout footer=row();
        footer.addView(button("＋ Importer une photo",()->openPhoto()),new LinearLayout.LayoutParams(0,dp(48),1));
        footer.addView(button("＋ Ami",()->addFriend()),lp(72,48));
        root.addView(footer,lp(-1,54));
    }
    private interface Fill {void fill(LinearLayout l);}
    private void addStrip(String label,int count,Fill fill){
        LinearLayout box=strip(label,count);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setFillViewport(true);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout children=row();children.setGravity(Gravity.CENTER_VERTICAL);fill.fill(children);
        if(children.getChildCount()==0){TextView empty=text("Aucune carte",12,MUTED);empty.setPadding(dp(8),0,0,0);children.addView(empty);}
        scroll.addView(children);
        box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.setMargins(0,dp(4),0,dp(2));root.addView(box,sp);
    }
    private View tile(JSONObject c,JSONObject cp,String role){
        LinearLayout tile=col();tile.setPadding(dp(5),dp(3),dp(5),dp(2));
        int w=105;
        ImageView image=new ImageView(this);image.setAdjustViewBounds(false);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackground(shape(Color.rgb(3,13,25),5));
        tile.addView(image,lp(w,78));
        String title=c==null?"Emplacement libre":c.optString("title","Carte");
        if("pending".equals(role)||"sent".equals(role)||cp!=null)title="Carte_"+title;
        TextView name=text(title,10,FG);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(name,lp(w,23));
        if(c!=null){
            String url=cp!=null?cp.optString("snapshot_url"):c.optString("framed_url");
            if("original".equals(role))url=c.optString("original_url");
            thumb(image,url);
        } else {
            image.setImageResource(android.R.drawable.ic_input_add);
            image.setColorFilter(MUTED);
        }
        final JSONObject cc=c,copy=cp;
        tile.setOnClickListener(v->{
            if(cc==null){toast("Case libre");return;}
            showActions(cc,copy,role);
        });
        tile.setOnLongClickListener(v->{if(cc!=null)showActions(cc,copy,role);return true;});
        return tile;
    }
    private void thumb(ImageView view,String url){
        if(url==null||(!url.startsWith("https://")&&!url.startsWith("file://")))return;
        Bitmap b=thumbs.get(url);if(b!=null){view.setImageBitmap(b);return;}
        view.setImageResource(android.R.drawable.ic_menu_gallery);
        pool.execute(()->{
            try{
                byte[] bytes=http(url,"GET",null,null);
                BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=5;
                Bitmap pic=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);
                if(pic!=null){thumbs.put(url,pic);runOnUiThread(()->{if(!isFinishing())view.setImageBitmap(pic);});}
            }catch(Exception ignored){}
        });
    }
    private void showActions(JSONObject c,JSONObject copy,String role){
        final int id=c.optInt("id");
        ArrayList<String> names=new ArrayList<>();ArrayList<Runnable> commands=new ArrayList<>();
        if("pending".equals(role)){
            names.add("Partager et attribuer à un ami");commands.add(()->pickSlot(-1,(f,s)->shareThenAssign(c,f,s)));
            names.add("Attribuer sans partager");commands.add(()->pickSlot(-1,(f,s)->operation("assign","card_id",id,"friend_id",f,"slot_no",s)));
        }
        if("original".equals(role)){names.add("Créer la bordure");commands.add(()->frameDialog(c));}
        if(copy!=null&&"with_friend".equals(copy.optString("status"))){
            names.add("Déplacer vers une autre case");commands.add(()->pickSlot(copy.optInt("id"),(f,s)->operation("move_copy","copy_id",copy.optInt("id"),"friend_id",f,"slot_no",s)));
            names.add("✓ Marquer comme envoyée");commands.add(()->operation("sent","copy_id",copy.optInt("id")));
        }
        if(copy!=null&&"sent".equals(copy.optString("status"))){
            names.add("Archiver définitivement");commands.add(()->new AlertDialog.Builder(this).setTitle("Terminer le cycle ?")
                .setMessage("La carte sera conservée dans les Archives annuelles et quittera l'accueil.")
                .setNegativeButton("Annuler",null).setPositiveButton("Archiver",(d,n)->operation("finalize","copy_id",copy.optInt("id"))).show());
        }
        names.add("Enregistrer sur le téléphone");commands.add(()->saveCard(c,copy,role));
        names.add("Partager l'image");commands.add(()->shareCard(c,copy,null));
        names.add("Détails");commands.add(()->new AlertDialog.Builder(this).setTitle(c.optString("title")).setMessage(
            "Année : "+c.optInt("archive_year")+"\nÉtat : "+c.optString("stage")+"\nN° : "+id).setPositiveButton("OK",null).show());
        new AlertDialog.Builder(this).setTitle("Carte_"+c.optString("title"))
            .setItems(names.toArray(new String[0]),(d,choice)->commands.get(choice).run()).show();
    }
    private void pickSlot(int movingCopyId,Choice chose){
        ArrayList<String> list=new ArrayList<>();ArrayList<int[]> slots=new ArrayList<>();
        JSONArray friends=a("friends");
        for(int i=0;i<friends.length();i++){
            JSONObject f=item(friends,i);if(f==null||!f.optBoolean("active",true))continue;
            for(int j=1;j<=Math.min(24,f.optInt("slots",1));j++){
                if(currentCopy(f.optInt("id"),j)!=null)continue;
                list.add(f.optString("name")+" — case "+j+" (libre)");
                slots.add(new int[]{f.optInt("id"),j});
            }
        }
        if(list.isEmpty()){error("Aucun emplacement libre. Ajoute une case dans WordPress.");return;}
        new AlertDialog.Builder(this).setTitle("Choisir l'ami et sa case")
            .setItems(list.toArray(new String[0]),(d,n)->chose.chosen(slots.get(n)[0],slots.get(n)[1]))
            .setNegativeButton("Annuler",null).show();
    }
    private void operation(String op,Object... pairs){
        StringBuilder b=new StringBuilder("op="+encode(op));
        for(int i=0;i<pairs.length;i+=2)b.append("&").append(encode(""+pairs[i])).append("=").append(encode(""+pairs[i+1]));
        byte[] payload=b.toString().getBytes(StandardCharsets.UTF_8);
        if(offlineMode()){localOperation(op,pairs);return;}
        loading("Mise à jour…");
        job(()->http(base()+"mobile/action","POST",payload,"application/x-www-form-urlencoded; charset=UTF-8"),out->{
            JSONObject response=new JSONObject(new String(out,StandardCharsets.UTF_8));
            if(!response.optBoolean("success"))throw new IOException(response.optJSONObject("data")==null?"Action refusée":response.optJSONObject("data").optString("message","Action refusée"));
            page="overview";archives=false;refresh();
        });
    }
    private String encode(String s){try{return java.net.URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}}
    private String imageUrl(JSONObject c,JSONObject cp,String role){
        if(cp!=null)return cp.optString("snapshot_url");
        return "original".equals(role)?c.optString("original_url"):c.optString("framed_url");
    }
    private void saveCard(JSONObject c,JSONObject cp,String role){
        String url=imageUrl(c,cp,role);if(url.isEmpty()){error("Image absente.");return;}
        job(()->http(url,"GET",null,null),b->{
            String filename=filename(c,role,b);
            String mime=png(b)?"image/png":"image/jpeg";
            ContentValues values=new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,filename);
            values.put(MediaStore.Images.Media.MIME_TYPE,mime);
            values.put(MediaStore.Images.Media.RELATIVE_PATH,android.os.Environment.DIRECTORY_PICTURES+"/Cartes Organizer");
            values.put(MediaStore.Images.Media.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new IOException("Impossible d'enregistrer la photo.");
            try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Accès Galerie impossible.");out.write(b);}
            catch(Exception e){getContentResolver().delete(uri,null,null);throw e;}
            values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);
            toast("Image enregistrée dans Photos / Cartes Organizer");
        });
    }
    private String filename(JSONObject c,String role,byte[] b){
        String title=c.optString("title","Carte_"+c.optInt("id")).replaceAll("[^a-zA-Z0-9._-]","_");
        if(title.length()>90)title=title.substring(0,90);
        return ("original".equals(role)?"Flyer_":"Carte_")+title+"_"+c.optInt("id")+(png(b)?".png":".jpg");
    }
    private boolean png(byte[] b){return b.length>4 && (b[0]&0xff)==137 && b[1]=='P' && b[2]=='N' && b[3]=='G';}
    private void shareThenAssign(JSONObject card,int friendId,int slot){
        pendingShare=new Pending(card.optInt("id"),friendId,slot);
        shareCard(card,null,()->{returningFromShare=true;});
    }
    private void shareCard(JSONObject c,JSONObject cp,Runnable started){
        String url=imageUrl(c,cp,cp!=null?"copy":"pending");
        if(url.isEmpty()){error("Carte sans bordure.");return;}
        job(()->http(url,"GET",null,null),b->{
            boolean isPng=png(b);
            File dir=new File(getCacheDir(),"partages");dir.mkdirs();
            String n="c"+c.optInt("id")+"_"+System.currentTimeMillis()+(isPng?".png":".jpg");
            File file=new File(dir,n);
            try(FileOutputStream os=new FileOutputStream(file)){os.write(b);}
            Intent send=new Intent(Intent.ACTION_SEND);send.setType(isPng?"image/png":"image/jpeg");
            Uri uri=Uri.parse("content://ch.piiwii.cartesorganizer.share/"+n);
            send.putExtra(Intent.EXTRA_STREAM,uri);send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri("Carte",uri));
            if(started!=null)started.run();
            startActivity(Intent.createChooser(send,"Partager la carte"));
        });
    }
    @Override protected void onResume(){
        super.onResume();
        if(returningFromShare&&pendingShare!=null){
            returningFromShare=false;
            Pending p=pendingShare;
            handler.postDelayed(()->{
                if(isFinishing())return;
                new AlertDialog.Builder(this).setTitle("Partager et attribuer")
                    .setMessage("As-tu partagé la carte à ton ami ? WhatsApp ne confirme pas l'envoi à Cartes Organizer.")
                    .setPositiveButton("Oui, attribuer à la case",(d,n)->{pendingShare=null;operation("assign","card_id",p.card,"friend_id",p.friend,"slot_no",p.slot);})
                    .setNegativeButton("Non, laisser en attente",(d,n)->{pendingShare=null;})
                    .setCancelable(false).show();
            },900);
        }
    }
    private void openPhoto(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_PHOTO);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==PICK_PHOTO&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            job(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Fichier inaccessible.");return readAll(in);}},b->{
                String name="photo_"+System.currentTimeMillis()+(png(b)?".png":".jpg");
                if(offlineMode()){
                    try{
                        File images=new File(getFilesDir(),"local_photos");images.mkdirs();File target=new File(images,name);
                        try(FileOutputStream out=new FileOutputStream(target)){out.write(b);}
                        int id=nextId(a("cards"));
                        JSONObject card=dataObject("id",id,"title","Carte "+id,"stage","flyer","has_frame",false,"original_url","file://"+target.getAbsolutePath(),"framed_url","","archive_year",Calendar.getInstance().get(Calendar.YEAR),"deleted_at",JSONObject.NULL);
                        a("cards").put(card);record("Photo ajoutée");persist();page="overview";renderScreen();
                    }catch(Exception e){error("Import local impossible : "+e.getMessage());}
                }else upload("upload",new String[]{"year",String.valueOf(Calendar.getInstance().get(Calendar.YEAR))},b,name,"image",null);
            });
        }
    }
    private void frameDialog(JSONObject c){
        if(offlineMode()){new AlertDialog.Builder(this).setTitle("Bordure")
            .setMessage("Le cadrage Photoshop du plugin nécessite la connexion WordPress. En local, marquer cette image comme prête sans créer une bordure ?")
            .setPositiveButton("Marquer prête",(d,n)->{try{c.put("has_frame",true);c.put("stage","pending");c.put("framed_url",c.optString("original_url"));persist();renderScreen();}catch(Exception e){error(e.getMessage());}})
            .setNegativeButton("Annuler",null).show();return;}
        String[] opts={"Verticale 1292 × 1800","Horizontale 1800 × 1292"};
        new AlertDialog.Builder(this).setTitle("Bordure fixe Photoshop").setItems(opts,(d,n)->{
            job(()->{
                byte[] raw=http(c.optString("original_url"),"GET",null,null);
                BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=2;
                Bitmap original=BitmapFactory.decodeByteArray(raw,0,raw.length,opt);
                if(original==null)throw new IOException("Image illisible");
                int w=n==0?1292:1800,h=n==0?1800:1292,mx=n==0?59:82,my=n==0?95:68;
                Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(result);canvas.drawColor(Color.WHITE);
                Paint p=new Paint(3);p.setFilterBitmap(true);
                Rect frame=new Rect(mx,my,w-mx,h-my);
                canvas.drawBitmap(original,null,frame,p);
                p.setColor(Color.BLACK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
                canvas.drawRect(mx,my,w-mx,h-my,p);
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                result.compress(Bitmap.CompressFormat.PNG,100,out);
                original.recycle();result.recycle();
                return png300dpi(out.toByteArray());
            },b->{
                upload("frame",new String[]{"card_id",String.valueOf(c.optInt("id")),"model",n==0?"portrait":"landscape",
                    "color","#ffffff","pct","0","template","psd-fixed-frame-v1"},b,"Carte.png","image",null);
            });
        }).show();
    }
    private byte[] png300dpi(byte[] png)throws IOException{
        if(!png(png)||png.length<33)return png;
        // pHYs: 11811 pixels per metre = 299.9994 ppp.
        ByteArrayOutputStream chunk=new ByteArrayOutputStream();java.io.DataOutputStream d=new java.io.DataOutputStream(chunk);
        d.writeInt(9);byte[] label={'p','H','Y','s'};
        d.write(label);d.writeInt(11811);d.writeInt(11811);d.writeByte(1);
        java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(label);byte[] data=chunk.toByteArray();crc.update(data,8,9);
        d.writeInt((int)crc.getValue());d.flush();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(png,0,33);out.write(chunk.toByteArray());out.write(png,33,png.length-33);
        return out.toByteArray();
    }
    private void upload(String op,String[] fields,byte[] bytes,String name,String param,Runnable onDone){
        if(bytes.length>25*1024*1024){error("Le fichier dépasse 25 Mo.");return;}
        loading("Transfert vers WordPress…");
        job(()->{
            String boundary="CO_"+System.currentTimeMillis();
            ByteArrayOutputStream output=new ByteArrayOutputStream();StringBuilder f=new StringBuilder();
            f.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"op\"\r\n\r\n").append(op).append("\r\n");
            for(int i=0;i<fields.length;i+=2)f.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(fields[i]).append("\"\r\n\r\n").append(fields[i+1]).append("\r\n");
            output.write(f.toString().getBytes(StandardCharsets.UTF_8));
            String header="--"+boundary+"\r\nContent-Disposition: form-data; name=\""+param+"\"; filename=\""+name+"\"\r\nContent-Type: image/png\r\n\r\n";
            output.write(header.getBytes(StandardCharsets.UTF_8));output.write(bytes);
            output.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            return http(base()+"mobile/action","POST",output.toByteArray(),"multipart/form-data; boundary="+boundary);
        },b->{
            JSONObject r=new JSONObject(new String(b,StandardCharsets.UTF_8));
            if(!r.optBoolean("success"))throw new IOException(r.optJSONObject("data")==null?"Import refusé":r.optJSONObject("data").optString("message"));
            if(onDone!=null)onDone.run();
            page="overview";archives=false;refresh();
        });
    }
    private void renderArchives(){
        if(state==null)return;
        startScreen();toolbar("Archives");
        JSONArray years=a("archive_years");
        ArrayList<String> labels=new ArrayList<>();ArrayList<Integer> ids=new ArrayList<>();
        for(int i=0;i<years.length();i++){JSONObject y=item(years,i);if(y!=null){int year=y.optInt("year");if(year>0){labels.add(""+year);ids.add(year);}}}
        if(ids.isEmpty()){root.addView(text("Aucune année archivée pour le moment.",15,MUTED));return;}
        if(!ids.contains(chosenYear))chosenYear=ids.get(0);
        root.addView(button("Année "+chosenYear+" ▾",()->new AlertDialog.Builder(this).setTitle("Choisir une année")
          .setItems(labels.toArray(new String[0]),(d,n)->{chosenYear=ids.get(n);renderArchives();}).show()),lp(-1,43));
        ScrollView scroll=new ScrollView(this);LinearLayout list=col();scroll.addView(list);
        JSONArray copies=a("copies");int found=0;
        for(int i=0;i<copies.length();i++){
            JSONObject copy=item(copies,i);if(copy==null||!"archived".equals(copy.optString("status")))continue;
            JSONObject c=card(copy.optInt("card_id"));if(!alive(c)||c.optInt("archive_year")!=chosenYear)continue;
            found++;
            LinearLayout r=row();r.setPadding(dp(8),dp(5),dp(8),dp(5));r.setGravity(Gravity.CENTER_VERTICAL);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);r.addView(image,lp(78,95));thumb(image,copy.optString("archive_url"));
            String f=friend(copy.optInt("friend_id"))==null?"Ami":friend(copy.optInt("friend_id")).optString("name");
            TextView name=text("Carte_"+c.optString("title")+"\n"+f+" — "+copy.optString("archived_at"),12,FG);
            r.addView(name,new LinearLayout.LayoutParams(0,dp(94),1));
            r.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Archive")
              .setItems(new String[]{"Enregistrer la carte","Enregistrer le flyer original"},(d,n)->{
                  if(n==0)saveCard(c,copy,"archived");else saveCard(c,null,"original");
              }).show());
            list.addView(r);View divider=new View(this);divider.setBackgroundColor(PANEL);list.addView(divider,lp(-1,1));
        }
        if(found==0)list.addView(text("Pas encore de cartes terminées pour "+chosenYear+".",13,MUTED));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private String storeSecret(String s)throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("co_mobile_auth")){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("co_mobile_auth",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,(java.security.Key)ks.getKey("co_mobile_auth",null));
        byte[] iv=cipher.getIV(),encrypt=cipher.doFinal(s.getBytes(StandardCharsets.UTF_8));
        byte[] data=new byte[iv.length+encrypt.length];System.arraycopy(iv,0,data,0,iv.length);System.arraycopy(encrypt,0,data,iv.length,encrypt.length);
        return android.util.Base64.encodeToString(data,android.util.Base64.NO_WRAP);
    }
    private String readSecret(String s)throws Exception{
        if(s==null||s.isEmpty())return "";
        byte[] data=android.util.Base64.decode(s,android.util.Base64.NO_WRAP);if(data.length<29)return "";
        byte[] iv=Arrays.copyOfRange(data,0,12),encrypted=Arrays.copyOfRange(data,12,data.length);
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,(java.security.Key)ks.getKey("co_mobile_auth",null),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
    }
    @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
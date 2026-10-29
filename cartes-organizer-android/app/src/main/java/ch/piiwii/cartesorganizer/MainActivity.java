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
    private Pending pendingShare;
    private boolean returningFromShare=false;
    private boolean archives=false;
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
        site=pref.getString("site",""); username=pref.getString("user","");
        try {password=readSecret(pref.getString("auth",""));}catch(Exception e){password="";}
        if(site.isEmpty()||username.isEmpty()||password.isEmpty())login();
        else {loading("Connexion à Cartes Organizer…");refresh();}
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
    private EditText edit(String hint,String val,int type){
        EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(val);e.setTextColor(FG);e.setHintTextColor(MUTED);e.setInputType(type);return e;
    }
    private void login(){
        startScreen();
        TextView title=text("Cartes Organizer",26,FG);title.setPadding(0,dp(16),0,dp(12));root.addView(title);
        TextView sub=text("Connexion au plugin WordPress privé. Aucun mot de passe principal n'est nécessaire.",14,MUTED);root.addView(sub);
        EditText address=edit("https://monsite.ch",site,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText user=edit("Identifiant WordPress",username,InputType.TYPE_CLASS_TEXT);
        EditText pass=edit("Mot de passe d'application WordPress",password,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(address);root.addView(user);root.addView(pass);
        TextView note=text("Sur ton site : Utilisateurs → Profil → Mots de passe d'application → Ajouter. HTTPS obligatoire.",12,MUTED);note.setPadding(0,dp(16),0,dp(20));root.addView(note);
        root.addView(button("Se connecter",()->{
            String url=address.getText().toString().trim().replaceAll("/+$","");
            if(!url.startsWith("https://")){error("L'adresse doit commencer par https://");return;}
            site=url; username=user.getText().toString().trim(); password=pass.getText().toString();
            if(username.isEmpty()||password.isEmpty()){error("Renseigne le compte et son mot de passe d'application.");return;}
            loading("Vérification de la connexion…");refresh();
        }),lp(-1,50));
    }
    private String base(){return site+"/" + API;}
    private String authorization(){return "Basic "+android.util.Base64.encodeToString((username+":"+password).getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);}
    private byte[] http(String url,String method,byte[] body,String contentType)throws Exception{
        if(!url.startsWith("https://"))throw new IOException("Adresse HTTPS obligatoire.");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(18000);c.setReadTimeout(45000);c.setRequestMethod(method);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Authorization",authorization());c.setRequestProperty("Accept","application/json, image/*");
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
            state=j;
            getPreferences(MODE_PRIVATE).edit().putString("site",site).putString("user",username).putString("auth",storeSecret(password)).apply();
            if(archives)renderArchives();else renderHome();
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
        TextView title=text(label,19,FG);title.setTypeface(null,1);bar.addView(title,new LinearLayout.LayoutParams(0,dp(45),1));
        if(archives){bar.addView(button("Accueil",()->{archives=false;renderHome();}),lp(84,40));}
        else{bar.addView(button("Archives",()->{archives=true;renderArchives();}),lp(86,40));}
        bar.addView(button("↻",()->refresh()),lp(46,40));
        bar.addView(button("⋮",()->new AlertDialog.Builder(this).setItems(new String[]{"Changer de compte","Actualiser"},(d,n)->{if(n==0)login();else refresh();}).show()),lp(42,40));
        root.addView(bar);
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
        startScreen();toolbar("Cartes Organizer");
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
        if(url==null||!url.startsWith("https://"))return;
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
        loading("Mise à jour…");
        job(()->http(base()+"mobile/action","POST",payload,"application/x-www-form-urlencoded; charset=UTF-8"),out->{
            JSONObject response=new JSONObject(new String(out,StandardCharsets.UTF_8));
            if(!response.optBoolean("success"))throw new IOException(response.optJSONObject("data")==null?"Action refusée":response.optJSONObject("data").optString("message","Action refusée"));
            archives=false;refresh();
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
                String name="photo_"+System.currentTimeMillis()+".jpg";
                upload("upload",new String[]{"year",String.valueOf(Calendar.getInstance().get(Calendar.YEAR))},b,name,"image",null);
            });
        }
    }
    private void frameDialog(JSONObject c){
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
            archives=false;refresh();
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

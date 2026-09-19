package ch.piiwii.store2;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.core.content.FileProvider;

import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String API = "https://piiwii.ch/wp-json/piiwii-store/v1/apps";
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final ArrayList<AppItem> apps = new ArrayList<>();
    private LinearLayout appList, categories;
    private EditText search;
    private TextView status;
    private String selectedCategory = "Toutes";
    private long activeDownload = -1;
    private File pendingApk;
    private BroadcastReceiver downloadReceiver;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        registerDownloadReceiver();
        loadCatalog();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { if (downloadReceiver != null) unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        io.shutdownNow();
    }

    @Override protected void onResume() {
        super.onResume();
        if (pendingApk != null && pendingApk.exists() && canInstallPackages()) installApk(pendingApk);
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float sp, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); return t;
    }
    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }

    private void buildUi() {
        int blue = Color.rgb(20,87,217), muted = Color.rgb(100,112,135), bg = Color.rgb(245,247,251);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);

        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(20), dp(18), dp(20), dp(18)); header.setBackgroundColor(blue);
        TextView title = text("PiiWii Store", 27, Color.WHITE); title.setTypeface(null, android.graphics.Typeface.BOLD); header.addView(title);
        TextView subtitle = text("Tes applications Android, simplement.", 14, 0xFFE3ECFF); header.addView(subtitle);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout tools = new LinearLayout(this); tools.setOrientation(LinearLayout.VERTICAL); tools.setPadding(dp(14), dp(14), dp(14), dp(8));
        search = new EditText(this); search.setHint("Rechercher une application"); search.setSingleLine(true); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH); search.setTextSize(16); search.setPadding(dp(16),0,dp(16),0); search.setBackground(rounded(Color.WHITE, 16));
        tools.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); hsv.setPadding(0,dp(10),0,0);
        categories = new LinearLayout(this); categories.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(categories); tools.addView(hsv, new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(tools);

        status = text("Chargement du catalogue…", 14, muted); status.setGravity(Gravity.CENTER); status.setPadding(dp(14),dp(8),dp(14),dp(8)); root.addView(status);

        ScrollView scroll = new ScrollView(this); appList = new LinearLayout(this); appList.setOrientation(LinearLayout.VERTICAL); appList.setPadding(dp(14),dp(6),dp(14),dp(24)); scroll.addView(appList); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){ render(); } public void afterTextChanged(android.text.Editable e){}
        });
    }

    private void loadCatalog() {
        status.setText("Chargement du catalogue…");
        io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection)new URL(API).openConnection();
                c.setConnectTimeout(12000); c.setReadTimeout(16000); c.setRequestProperty("Accept","application/json"); c.setRequestProperty("User-Agent","PiiWiiStore/1.0.0");
                int code = c.getResponseCode(); if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
                String raw = readAll(c.getInputStream()); JSONObject root = new JSONObject(raw); JSONArray arr = root.optJSONArray("apps");
                ArrayList<AppItem> loaded = new ArrayList<>();
                if (arr != null) for (int i=0;i<arr.length();i++) loaded.add(AppItem.from(arr.getJSONObject(i)));
                runOnUiThread(() -> { apps.clear(); apps.addAll(loaded); buildCategories(); render(); status.setText(apps.isEmpty()?"Aucune application publiée pour le moment.":apps.size()+" application"+(apps.size()>1?"s":"")); });
            } catch (Exception e) {
                runOnUiThread(() -> { status.setText("Impossible de joindre le Store. Vérifie que le plugin WordPress PiiWii Store est installé et activé."); appList.removeAllViews(); addRetry(); });
            }
        });
    }

    private void addRetry() {
        Button b = new Button(this); b.setText("Réessayer"); b.setOnClickListener(v -> loadCatalog()); appList.addView(b);
    }

    private void buildCategories() {
        categories.removeAllViews();
        LinkedHashSet<String> cats = new LinkedHashSet<>(); cats.add("Toutes"); for (AppItem a: apps) if (!a.category.isEmpty()) cats.add(a.category);
        for (String cat: cats) {
            TextView chip = text(cat,14, cat.equals(selectedCategory)?Color.WHITE:0xFF26344D); chip.setGravity(Gravity.CENTER); chip.setPadding(dp(16),0,dp(16),0); chip.setBackground(rounded(cat.equals(selectedCategory)?0xFF1457D9:Color.WHITE, 20));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,dp(38)); lp.setMargins(0,0,dp(8),0); categories.addView(chip,lp);
            chip.setOnClickListener(v -> { selectedCategory=cat; buildCategories(); render(); });
        }
    }

    private void render() {
        if (appList == null) return; appList.removeAllViews();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT); int shown = 0;
        for (AppItem a: apps) {
            if (!selectedCategory.equals("Toutes") && !selectedCategory.equals(a.category)) continue;
            String hay=(a.name+" "+a.description+" "+a.category).toLowerCase(Locale.ROOT); if (!q.isEmpty() && !hay.contains(q)) continue;
            addCard(a); shown++;
        }
        if (shown==0 && !apps.isEmpty()) { TextView none=text("Aucune application ne correspond à ta recherche.",15,0xFF647087); none.setGravity(Gravity.CENTER); none.setPadding(0,dp(36),0,0); appList.addView(none); }
    }

    private void addCard(AppItem a) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(16),dp(16),dp(16)); card.setBackground(rounded(Color.WHITE,18)); card.setElevation(dp(2));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this); icon.setScaleType(ImageView.ScaleType.CENTER_CROP); icon.setImageResource(android.R.drawable.sym_def_app_icon); top.addView(icon,new LinearLayout.LayoutParams(dp(64),dp(64)));
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(14),0,0,0);
        TextView name=text(a.name,18,0xFF141F37); name.setTypeface(null,android.graphics.Typeface.BOLD); info.addView(name);
        info.addView(text(a.category + (a.versionName.isEmpty()?"":"  •  v"+a.versionName),13,0xFF647087));
        if (!a.packageName.isEmpty()) info.addView(text(a.packageName,11,0xFF8994A8));
        top.addView(info,new LinearLayout.LayoutParams(0,-2,1)); card.addView(top);
        if (!a.description.isEmpty()) { TextView d=text(a.description,14,0xFF46536B); d.setPadding(0,dp(12),0,0); card.addView(d); }
        LinearLayout bottom=new LinearLayout(this); bottom.setGravity(Gravity.CENTER_VERTICAL); bottom.setPadding(0,dp(12),0,0);
        String size=a.fileSize>0?formatBytes(a.fileSize):"APK"; bottom.addView(text(size,12,0xFF7B879B),new LinearLayout.LayoutParams(0,-2,1));
        Button install=new Button(this); install.setAllCaps(false);
        boolean installed=isInstalled(a.packageName);
        long installedCode=getInstalledVersionCode(a.packageName);
        if(installed && a.versionCode>0 && installedCode>=a.versionCode){ install.setText("Ouvrir"); install.setOnClickListener(v -> openInstalled(a)); }
        else { install.setText(installed?"Mettre à jour":"Installer"); install.setOnClickListener(v -> download(a)); }
        install.setTextColor(Color.WHITE); install.setTextSize(14); install.setBackground(rounded(0xFF1457D9,14)); install.setPadding(dp(18),0,dp(18),0); bottom.addView(install,new LinearLayout.LayoutParams(-2,dp(44))); card.addView(bottom);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,0,0,dp(12)); appList.addView(card,cp);
        if (!a.iconUrl.isEmpty()) loadIcon(a.iconUrl,icon);
    }

    private void loadIcon(String url, ImageView target) {
        io.execute(() -> { try { HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(8000); Bitmap bmp=BitmapFactory.decodeStream(c.getInputStream()); if(bmp!=null) runOnUiThread(() -> target.setImageBitmap(bmp)); } catch(Exception ignored){} });
    }

    private boolean isInstalled(String packageName){
        if(packageName==null||packageName.isEmpty()) return false;
        try{ getPackageManager().getPackageInfo(packageName,0); return true; } catch(Exception e){ return false; }
    }

    private long getInstalledVersionCode(String packageName){
        if(packageName==null||packageName.isEmpty()) return -1;
        try{
            android.content.pm.PackageInfo pi=getPackageManager().getPackageInfo(packageName,0);
            return Build.VERSION.SDK_INT>=28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch(Exception e){ return -1; }
    }

    private void openInstalled(AppItem a){
        try{ Intent i=getPackageManager().getLaunchIntentForPackage(a.packageName); if(i==null){ toast("Application installée, mais aucune activité de lancement n’est disponible."); return; } startActivity(i); }
        catch(Exception e){ toast("Impossible d’ouvrir l’application."); }
    }

    private void download(AppItem a) {
        if (a.downloadUrl.isEmpty()) { toast("Lien de téléchargement indisponible."); return; }
        try {
            String fn = safe(a.packageName.isEmpty()?a.name:a.packageName) + "-" + safe(a.versionName.isEmpty()?"latest":a.versionName) + ".apk";
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS); if (dir==null) throw new IOException("Stockage indisponible"); File out=new File(dir,fn); if(out.exists()) out.delete();
            DownloadManager.Request r=new DownloadManager.Request(Uri.parse(a.downloadUrl)); r.setTitle(a.name); r.setDescription("Téléchargement de l’APK"); r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); r.setMimeType("application/vnd.android.package-archive"); r.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fn);
            activeDownload=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r); pendingApk=out; toast("Téléchargement démarré…");
        } catch(Exception e){ toast("Impossible de démarrer le téléchargement."); }
    }

    private void registerDownloadReceiver() {
        downloadReceiver=new BroadcastReceiver(){ @Override public void onReceive(Context context,Intent intent){ long id=intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1); if(id!=activeDownload||pendingApk==null)return; if(!pendingApk.exists()){toast("Le téléchargement a échoué.");return;} if(!canInstallPackages()){ requestInstallPermission(); } else installApk(pendingApk); }};
        IntentFilter f=new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(downloadReceiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(downloadReceiver,f);
    }

    private boolean canInstallPackages(){ return Build.VERSION.SDK_INT<26 || getPackageManager().canRequestPackageInstalls(); }
    private void requestInstallPermission(){
        if(Build.VERSION.SDK_INT>=26){ toast("Autorise PiiWii Store à installer des applications, puis reviens ici."); Intent i=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())); startActivity(i); }
    }
    private void installApk(File apk){
        try{
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",apk); Intent i=new Intent(Intent.ACTION_VIEW); i.setDataAndType(uri,"application/vnd.android.package-archive"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); pendingApk=null;
        }catch(Exception e){ toast("Impossible d’ouvrir l’installateur Android."); }
    }

    private static String readAll(InputStream in) throws IOException { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while((n=in.read(b))>0)out.write(b,0,n); return out.toString(StandardCharsets.UTF_8.name()); }
    private static String safe(String s){ return s.replaceAll("[^A-Za-z0-9._-]+","-").replaceAll("-+","-"); }
    private static String formatBytes(long n){ if(n>=1024L*1024) return String.format(Locale.getDefault(),"%.1f Mo",n/(1024f*1024f)); if(n>=1024) return String.format(Locale.getDefault(),"%.0f Ko",n/1024f); return n+" o"; }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }

    static class AppItem {
        String name="Application", packageName="", category="Autres", description="", iconUrl="", versionName="", downloadUrl=""; long fileSize=0, versionCode=0;
        static AppItem from(JSONObject o){ AppItem a=new AppItem(); a.name=o.optString("name","Application"); a.packageName=o.optString("package_name",""); a.category=o.optString("category","Autres"); a.description=o.optString("description",""); a.iconUrl=o.optString("icon_url",""); if("null".equals(a.iconUrl))a.iconUrl=""; a.versionName=o.optString("version_name",""); a.versionCode=o.optLong("version_code",0); a.downloadUrl=o.optString("download_url",""); a.fileSize=o.optLong("file_size",0); return a; }
    }
}

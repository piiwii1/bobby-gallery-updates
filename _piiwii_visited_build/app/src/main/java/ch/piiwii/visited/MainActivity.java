package ch.piiwii.visited;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 4101;
    private static final int REQ_PLACES = 4102;
    private MapView map;
    private TextView counter;
    private PlaceDb db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LocationManager locationManager;
    private Marker currentLocationMarker;
    private boolean placementMode = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().setOsmdroidBasePath(getCacheDir());
        Configuration.getInstance().setOsmdroidTileCache(new java.io.File(getCacheDir(), "osm"));
        setContentView(R.layout.activity_main);

        db = new PlaceDb(this);
        map = findViewById(R.id.map);
        counter = findViewById(R.id.counter);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.getController().setZoom(5.2);
        map.getController().setCenter(new GeoPoint(46.8, 8.2));

        MapEventsOverlay mapEvents = new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (placementMode) {
                    placementMode = false;
                    proposePlace(p.getLatitude(), p.getLongitude(), null);
                    return true;
                }
                return false;
            }
            @Override public boolean longPressHelper(GeoPoint p) {
                placementMode = false;
                proposePlace(p.getLatitude(), p.getLongitude(), null);
                return true;
            }
        });
        map.getOverlays().add(mapEvents);

        findViewById(R.id.btnLocate).setOnClickListener(v -> locateMe());
        findViewById(R.id.navMap).setOnClickListener(v -> fitToPlaces());
        findViewById(R.id.navPlaces).setOnClickListener(v -> startActivityForResult(new Intent(this, PlacesActivity.class), REQ_PLACES));
        findViewById(R.id.navAdd).setOnClickListener(v -> showAddMenu());
        findViewById(R.id.navSync).setOnClickListener(v -> showMyMapsMenu());

        renderPlaces();
        if (db.count() == 0) syncMyMaps(true);
    }

    private void renderPlaces() {
        java.util.List<org.osmdroid.views.overlay.Overlay> remove = new java.util.ArrayList<>();
        for (org.osmdroid.views.overlay.Overlay o : map.getOverlays()) {
            if (o instanceof Marker && o != currentLocationMarker) remove.add(o);
        }
        map.getOverlays().removeAll(remove);
        List<Place> places = db.all();
        for (Place p : places) {
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(p.lat, p.lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle(p.name);
            m.setSubDescription("mymaps".equals(p.source) ? "Importé depuis My Maps" : "Ajouté dans PiiWii Visited");
            m.setIcon(makePin(Color.rgb(229,57,53)));
            m.setOnMarkerClickListener((marker, mapView) -> { marker.showInfoWindow(); return true; });
            map.getOverlays().add(m);
        }
        if (currentLocationMarker != null && !map.getOverlays().contains(currentLocationMarker)) map.getOverlays().add(currentLocationMarker);
        counter.setText(places.size() + (places.size() > 1 ? " lieux" : " lieu"));
        map.invalidate();
    }

    private android.graphics.drawable.Drawable makePin(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL); d.setColor(color); d.setStroke(dp(3), Color.WHITE); d.setSize(dp(18), dp(18)); return d;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void fitToPlaces() {
        List<Place> places = db.all();
        if (places.isEmpty()) {
            map.getController().animateTo(new GeoPoint(46.8, 8.2)); map.getController().setZoom(5.2); return;
        }
        double minLat=90,maxLat=-90,minLon=180,maxLon=-180;
        for (Place p : places) {
            minLat=Math.min(minLat,p.lat); maxLat=Math.max(maxLat,p.lat); minLon=Math.min(minLon,p.lon); maxLon=Math.max(maxLon,p.lon);
        }
        map.zoomToBoundingBox(new org.osmdroid.util.BoundingBox(maxLat,maxLon,minLat,minLon), true, dp(48));
    }

    private void showAddMenu() {
        new AlertDialog.Builder(this).setTitle("Ajouter un lieu")
                .setItems(new String[]{"Ma position actuelle", "Rechercher une adresse ou un lieu", "Placer directement sur la carte"}, (d, which) -> {
                    if (which == 0) locateAndPropose();
                    else if (which == 1) searchAddressDialog();
                    else { placementMode = true; Toast.makeText(this, "Touchez l'endroit à ajouter sur la carte", Toast.LENGTH_LONG).show(); }
                }).show();
    }

    private void showMyMapsMenu() {
        new AlertDialog.Builder(this).setTitle("Google My Maps")
                .setMessage("La synchronisation remplace uniquement les points importés depuis ta carte Google. Tes ajouts faits dans l'application restent conservés.")
                .setPositiveButton("Synchroniser", (d,w) -> syncMyMaps(false))
                .setNeutralButton("Ouvrir l'originale", (d,w) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(MyMapsImporter.SHARE_URL))); }
                    catch (Exception e) { Toast.makeText(this, "Impossible d'ouvrir le lien", Toast.LENGTH_SHORT).show(); }
                }).setNegativeButton("Fermer", null).show();
    }

    private void syncMyMaps(boolean quiet) {
        if (!quiet) Toast.makeText(this, "Synchronisation My Maps…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                List<Place> imported = MyMapsImporter.download(); db.replaceMyMaps(imported);
                runOnUiThread(() -> { renderPlaces(); fitToPlaces(); Toast.makeText(this, imported.size() + " points My Maps synchronisés", Toast.LENGTH_LONG).show(); });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "My Maps indisponible pour le moment. Les lieux déjà enregistrés restent accessibles.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void locateMe() {
        if (!hasLocationPermission()) { requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION); return; }
        requestLocation(false);
    }
    private void locateAndPropose() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            Toast.makeText(this, "Réappuie sur Ajouter après avoir autorisé la localisation", Toast.LENGTH_LONG).show(); return;
        }
        requestLocation(true);
    }

    private void requestLocation(boolean proposeAdd) {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location best = null;
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            best = newer(gps, net);
        } catch (SecurityException ignored) { }
        if (best != null) showCurrentLocation(best, proposeAdd);
        final Location bestFinal = best;
        LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                showCurrentLocation(location, proposeAdd);
                try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
            }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        };
        try {
            String provider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            locationManager.requestLocationUpdates(provider, 0, 0, listener, getMainLooper());
        } catch (Exception e) {
            if (bestFinal == null) Toast.makeText(this, "Localisation indisponible", Toast.LENGTH_LONG).show();
        }
    }

    private Location newer(Location a, Location b) {
        if (a == null) return b; if (b == null) return a; return a.getTime() >= b.getTime() ? a : b;
    }

    private void showCurrentLocation(Location l, boolean proposeAdd) {
        GeoPoint p = new GeoPoint(l.getLatitude(), l.getLongitude());
        if (currentLocationMarker == null) {
            currentLocationMarker = new Marker(map); currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            currentLocationMarker.setTitle("Ma position"); currentLocationMarker.setIcon(makePin(Color.rgb(30,136,229))); map.getOverlays().add(currentLocationMarker);
        }
        currentLocationMarker.setPosition(p); map.getController().animateTo(p);
        if (map.getZoomLevelDouble() < 16) map.getController().setZoom(16.0);
        map.invalidate();
        if (proposeAdd) proposePlace(l.getLatitude(), l.getLongitude(), null);
        else Toast.makeText(this, "Position trouvée. Utilise Ajouter pour l'enregistrer.", Toast.LENGTH_SHORT).show();
    }

    private void searchAddressDialog() {
        final EditText input = new EditText(this); input.setSingleLine(false); input.setHint("Ex. Glacier 3000, Paris ou une adresse complète");
        int pad = dp(20); input.setPadding(pad, pad, pad, pad);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Rechercher un lieu").setView(input).setPositiveButton("Rechercher", null).setNegativeButton("Annuler", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String q = input.getText().toString().trim(); if (q.isEmpty()) return; dialog.dismiss(); geocodeQuery(q);
        }));
        dialog.show();
    }

    private void geocodeQuery(String query) {
        Toast.makeText(this, "Recherche…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                if (!Geocoder.isPresent()) throw new IllegalStateException("Geocoder absent");
                Geocoder g = new Geocoder(this, Locale.getDefault()); List<Address> list = g.getFromLocationName(query, 5);
                if (list == null || list.isEmpty()) throw new IllegalStateException("Aucun résultat");
                Address a = list.get(0); String suggested = bestAddressName(a, query);
                runOnUiThread(() -> { GeoPoint p = new GeoPoint(a.getLatitude(), a.getLongitude()); map.getController().animateTo(p); map.getController().setZoom(16.0); proposePlace(a.getLatitude(), a.getLongitude(), suggested); });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Lieu introuvable. Essaie une adresse plus précise.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void proposePlace(double lat, double lon, String suggested) {
        if (suggested != null) { askNameAndSave(lat, lon, suggested); return; }
        io.execute(() -> {
            String name = "Lieu visité";
            try {
                if (Geocoder.isPresent()) {
                    Geocoder g = new Geocoder(this, Locale.getDefault()); List<Address> a = g.getFromLocation(lat, lon, 1);
                    if (a != null && !a.isEmpty()) name = bestAddressName(a.get(0), name);
                }
            } catch (Exception ignored) { }
            String finalName = name; runOnUiThread(() -> askNameAndSave(lat, lon, finalName));
        });
    }

    private String bestAddressName(Address a, String fallback) {
        if (a == null) return fallback;
        if (a.getFeatureName() != null && !a.getFeatureName().matches("\\d+")) return a.getFeatureName();
        if (a.getLocality() != null) return a.getLocality();
        if (a.getSubAdminArea() != null) return a.getSubAdminArea();
        if (a.getMaxAddressLineIndex() >= 0 && a.getAddressLine(0) != null) return a.getAddressLine(0);
        return fallback;
    }

    private void askNameAndSave(double lat, double lon, String suggested) {
        final EditText input = new EditText(this); input.setText(suggested); input.setSelectAllOnFocus(true); input.setPadding(dp(20), dp(20), dp(20), dp(20));
        new AlertDialog.Builder(this).setTitle("Ajouter cet endroit ?").setMessage(String.format(Locale.getDefault(), "%.6f, %.6f", lat, lon)).setView(input)
                .setPositiveButton("Ajouter", (d,w) -> {
                    String n = input.getText().toString().trim(); if (n.isEmpty()) n = "Lieu visité";
                    db.add(n, lat, lon, "local"); renderPlaces(); Toast.makeText(this, n + " ajouté", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Annuler", null).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (hasLocationPermission()) locateMe();
            else new AlertDialog.Builder(this).setTitle("Localisation désactivée")
                    .setMessage("Tu peux continuer à ajouter des lieux manuellement. Pour utiliser ta position, autorise la localisation dans les réglages Android.")
                    .setPositiveButton("Réglages", (d,w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("Fermer", null).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PLACES && resultCode == RESULT_OK && data != null) {
            renderPlaces();
            if (data.hasExtra("lat")) {
                GeoPoint p = new GeoPoint(data.getDoubleExtra("lat", 0), data.getDoubleExtra("lon", 0)); map.getController().animateTo(p); map.getController().setZoom(16.0);
            }
        }
    }

    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override protected void onPause() { if (map != null) map.onPause(); super.onPause(); }
    @Override protected void onDestroy() { io.shutdownNow(); if (db != null) db.close(); super.onDestroy(); }
}

package ch.piiwii.visited;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class PlaceDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "visited.db";
    private static final int DB_VERSION = 1;

    public PlaceDb(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE places (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "lat REAL NOT NULL," +
                "lon REAL NOT NULL," +
                "source TEXT NOT NULL DEFAULT 'local'," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE(name, lat, lon) ON CONFLICT IGNORE)");
        db.execSQL("CREATE INDEX idx_places_name ON places(name)");
        db.execSQL("CREATE INDEX idx_places_source ON places(source)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public synchronized long add(String name, double lat, double lon, String source) {
        ContentValues v = new ContentValues();
        v.put("name", name == null || name.trim().isEmpty() ? "Lieu visité" : name.trim());
        v.put("lat", lat); v.put("lon", lon);
        v.put("source", source == null ? "local" : source);
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict("places", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized void replaceMyMaps(List<Place> imported) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("places", "source=?", new String[]{"mymaps"});
            for (Place p : imported) {
                ContentValues v = new ContentValues();
                v.put("name", p.name); v.put("lat", p.lat); v.put("lon", p.lon);
                v.put("source", "mymaps"); v.put("created_at", System.currentTimeMillis());
                db.insertWithOnConflict("places", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized List<Place> all() {
        List<Place> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name,lat,lon,source FROM places ORDER BY name COLLATE NOCASE", null);
        try { while (c.moveToNext()) out.add(new Place(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getString(4))); }
        finally { c.close(); }
        return out;
    }

    public synchronized List<Place> search(String q) {
        if (q == null || q.trim().isEmpty()) return all();
        List<Place> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name,lat,lon,source FROM places WHERE name LIKE ? ORDER BY name COLLATE NOCASE", new String[]{"%" + q.trim() + "%"});
        try { while (c.moveToNext()) out.add(new Place(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getString(4))); }
        finally { c.close(); }
        return out;
    }

    public synchronized int count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM places", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }
    public synchronized void delete(long id) { getWritableDatabase().delete("places", "id=?", new String[]{String.valueOf(id)}); }
}

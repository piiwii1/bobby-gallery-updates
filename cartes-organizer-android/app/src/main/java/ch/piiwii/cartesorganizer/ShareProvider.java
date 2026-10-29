package ch.piiwii.cartesorganizer;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
public final class ShareProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "image/png";}
    @Override public Cursor query(Uri uri,String[] pr,String selection,String[] args,String order) {
        File f = file(uri);
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
        if(f.exists())c.addRow(new Object[]{"Carte_"+f.getName(),f.length()});
        return c;
    }
    private File file(Uri uri) {
        String n=uri.getLastPathSegment();
        if(n==null || !n.matches("[0-9a-zA-Z_-]+\\.(png|jpg|jpeg|webp)"))throw new SecurityException("Nom invalide");
        return new File(getContext().getCacheDir(),"partages/"+n);
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Lecture uniquement");
        File f=file(uri);
        if(!f.isFile())throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String where,String[] args){return 0;}
    @Override public int update(Uri uri,ContentValues v,String where,String[] args){return 0;}
}

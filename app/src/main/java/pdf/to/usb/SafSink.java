package pdf.to.usb;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Destinazione B/pdf-to-usb via SAF. Da Android 6.0 la scrittura per percorso su una
 * chiavetta USB è vietata a un'app normale: l'unica via è l'albero scelto dall'utente.
 */
final class SafSink implements Copy.Sink {

    private static final String FOLDER = "pdf-to-usb";

    private final Context ctx;
    private final DocumentFile dir;

    SafSink(Context ctx, Uri treeB) throws IOException {
        this.ctx = ctx;
        DocumentFile root = DocumentFile.fromTreeUri(ctx, treeB);
        if (root == null || !root.canWrite()) throw new IOException("chiavetta B non accessibile");
        DocumentFile folder = root.findFile(FOLDER);
        if (folder == null) folder = root.createDirectory(FOLDER);
        if (folder == null || !folder.canWrite()) throw new IOException("cartella pdf-to-usb non creabile");
        dir = folder;
    }

    @Override
    public Map<String, Long> items() {
        Map<String, Long> out = new HashMap<>();
        for (DocumentFile f : dir.listFiles()) {
            String name = f.getName();
            if (name != null) out.put(name, f.length());
        }
        return out;
    }

    @Override
    public OutputStream create(String name) throws IOException {
        DocumentFile file = dir.createFile("application/pdf", name);
        if (file == null) throw new IOException("creazione di " + name + " rifiutata");
        OutputStream out = ctx.getContentResolver().openOutputStream(file.getUri());
        if (out == null) {
            file.delete();
            throw new IOException("apertura di " + name + " fallita");
        }
        return out;
    }

    @Override
    public void discard(String name) {
        DocumentFile file = dir.findFile(name);
        if (file != null) file.delete();
    }
}

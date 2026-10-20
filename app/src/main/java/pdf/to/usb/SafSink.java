package pdf.to.usb;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Destination B/pdf-to-usb through SAF. From Android 6.0 a normal app cannot write to a USB
 * stick by path: the only route is the tree the user picked.
 */
final class SafSink implements Copy.Sink {

    private static final String FOLDER = "pdf-to-usb";

    private final Context ctx;
    private final DocumentFile dir;

    SafSink(Context ctx, Uri treeB) throws IOException {
        this.ctx = ctx;
        DocumentFile root = DocumentFile.fromTreeUri(ctx, treeB);
        if (root == null || !root.canWrite()) throw new IOException("drive B not accessible");
        DocumentFile folder = root.findFile(FOLDER);
        if (folder == null) folder = root.createDirectory(FOLDER);
        if (folder == null || !folder.canWrite()) throw new IOException("pdf-to-usb folder cannot be created");
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
        if (file == null) throw new IOException("creation of " + name + " refused");
        OutputStream out = ctx.getContentResolver().openOutputStream(file.getUri());
        if (out == null) {
            file.delete();
            throw new IOException("opening " + name + " failed");
        }
        return out;
    }

    @Override
    public void discard(String name) {
        DocumentFile file = dir.findFile(name);
        if (file != null) file.delete();
    }
}

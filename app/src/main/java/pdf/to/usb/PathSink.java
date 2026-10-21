package pdf.to.usb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Destination B/pdf-to-usb as a plain directory. Used only when the board has no system
 * folder picker (no DocumentsUI), which is common on managed panels: there the app writes
 * with WRITE_EXTERNAL_STORAGE, allowed up to Android 9, instead of SAF.
 */
final class PathSink implements Copy.Sink {

    static final String FOLDER = "pdf-to-usb";

    private final File dir;

    /** Opens or creates B/pdf-to-usb; throws when the volume is read only or missing. */
    PathSink(File volumeRoot) throws IOException {
        if (volumeRoot == null) throw new IOException("drive B not set");
        dir = new File(volumeRoot, FOLDER);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("pdf-to-usb folder cannot be created");
        if (!dir.canWrite()) throw new IOException("drive B is read only");
    }

    /** Name -> size of what is already in the folder: the copy engine uses it for dedupe. */
    @Override
    public Map<String, Long> items() {
        Map<String, Long> out = new HashMap<>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) out.put(f.getName(), f.length());
        return out;
    }

    /** Creates the PDF on B and opens its stream. */
    @Override
    public OutputStream create(String name) throws IOException {
        return new FileOutputStream(new File(dir, name));
    }

    /** Best effort: a file that cannot be deleted is skipped by dedupe on the next attempt. */
    @Override
    public void discard(String name) {
        new File(dir, name).delete();
    }
}

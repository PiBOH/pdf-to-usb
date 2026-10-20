package pdf.to.usb;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Volumi rimovibili visti dal sistema e percorso reale di un albero SAF. */
final class Volumes {

    /** Autority del provider che il sistema usa per le chiavette (StorageProvider). */
    private static final String EXTERNAL = "com.android.externalstorage.documents";

    static final class Volume {
        final String label;
        final File dir;
        final String uuid;

        Volume(String label, File dir) {
            this(label, dir, dir == null ? null : dir.getName());
        }

        Volume(String label, File dir, String uuid) {
            this.label = label;
            this.dir = dir;
            this.uuid = uuid;
        }
    }

    private Volumes() {
    }

    static List<Volume> removable(Context c) {
        List<Volume> out = new ArrayList<>();
        StorageManager sm = (StorageManager) c.getSystemService(Context.STORAGE_SERVICE);
        if (sm == null) return out;
        if (Build.VERSION.SDK_INT >= 24) {
            for (StorageVolume v : sm.getStorageVolumes()) {
                if (!v.isRemovable()) continue;
                if (!Environment.MEDIA_MOUNTED.equals(v.getState())) continue;
                File dir = path(v);
                if (dir != null) out.add(new Volume(v.getDescription(c), dir, v.getUuid()));
            }
        } else {
            // API 21-23: getStorageVolumes() non esiste ancora, si passa dai metodi nascosti.
            try {
                Object[] volumes = (Object[]) StorageManager.class.getMethod("getVolumeList").invoke(sm);
                for (Object v : volumes) {
                    if (!((Boolean) v.getClass().getMethod("isRemovable").invoke(v))) continue;
                    String path = (String) v.getClass().getMethod("getPath").invoke(v);
                    if (path == null) continue;
                    String label = (String) v.getClass().getMethod("getDescription", Context.class).invoke(v, c);
                    out.add(new Volume(label, new File(path)));
                }
            } catch (Exception ignored) {
                // device senza volumi rimovibili o metodo nascosto rimosso: elenco vuoto
            }
        }
        return out;
    }

    /** Percorso del volume: UUID pubblico, poi i metodi nascosti, per ultimo null. */
    @android.annotation.TargetApi(24)
    private static File path(StorageVolume v) {
        if (Build.VERSION.SDK_INT >= 24) {
            String uuid = v.getUuid();
            if (uuid != null) {
                File dir = new File("/storage/" + uuid);
                if (dir.isDirectory()) return dir;
            }
        }
        try {
            Object p = StorageVolume.class.getMethod("getPathFile").invoke(v);
            if (p instanceof File && ((File) p).isDirectory()) return (File) p;
        } catch (Exception ignored) {
        }
        try {
            Object p = StorageVolume.class.getMethod("getPath").invoke(v);
            if (p instanceof String && new File((String) p).isDirectory()) return new File((String) p);
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Albero SAF della radice del volume: serve a pre-selezionarlo nel picker di B. */
    static Uri tree(Volume v) {
        if (v == null || v.uuid == null || v.uuid.isEmpty()) return null;
        return DocumentsContract.buildTreeDocumentUri(EXTERNAL, v.uuid + ":");
    }

    /** La sorgente A è la chiavetta con più PDF: è quella dove l'utente tiene i documenti. */
    static Volume source(List<Volume> volumes, int[] pdfs) {
        Volume best = null;
        int bestCount = -1;
        for (int i = 0; i < volumes.size() && i < pdfs.length; i++) {
            if (pdfs[i] > bestCount) {
                bestCount = pdfs[i];
                best = volumes.get(i);
            }
        }
        return best;
    }

    /** La destinazione B è l'altra chiavetta: null se non ce n'è esattamente una. */
    static Volume destination(List<Volume> volumes, Volume source) {
        Volume out = null;
        for (Volume v : volumes) {
            if (v == source) continue;
            if (source != null && source.uuid != null && source.uuid.equals(v.uuid)) continue;
            if (out != null) return null;   // tre o più chiavette: non si indovina
            out = v;
        }
        return out;
    }

    /** Conta i PDF fino a due livelli: basta a distinguere la chiavetta dei documenti. */
    static int pdfCount(File dir, int depth) {
        if (dir == null || depth > 2) return 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        int found = 0;
        for (File child : children) {
            if (child.isDirectory()) found += pdfCount(child, depth + 1);
            else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) found++;
        }
        return found;
    }

    /**
     * Percorso del filesystem dietro un albero SAF: l'id di ExternalStorageProvider è
     * "UUID:cartella". Serve a FileObserver, che vuole un File e non un Uri.
     */
    static File treePath(Uri tree) {
        if (tree == null) return null;
        String id;
        try {
            id = DocumentsContract.getTreeDocumentId(tree);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int colon = id.indexOf(':');
        if (colon <= 0) return null;
        String volume = id.substring(0, colon);
        String rel = id.substring(colon + 1);
        File root;
        if ("primary".equals(volume)) {
            root = Environment.getExternalStorageDirectory();
        } else {
            root = new File("/storage/" + volume);
        }
        if (root == null || !root.isDirectory()) return null;
        return rel.isEmpty() ? root : new File(root, rel);
    }
}

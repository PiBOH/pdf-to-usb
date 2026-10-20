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

/** Volumi rimovibili visti dal sistema e percorso reale di un albero SAF. */
final class Volumes {

    static final class Volume {
        final String label;
        final File dir;

        Volume(String label, File dir) {
            this.label = label;
            this.dir = dir;
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
                if (dir != null) out.add(new Volume(v.getDescription(c), dir));
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

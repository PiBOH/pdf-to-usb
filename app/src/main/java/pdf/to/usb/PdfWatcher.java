package pdf.to.usb;

import android.os.FileObserver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sorveglia i PDF di A. FileObserver.ACCESS è inotify IN_ACCESS: il kernel segnala la
 * lettura fatta da QUALUNQUE processo, quindi anche dal lettore PDF scelto dall'utente.
 * Limite noto: su alcuni device/storage (FUSE, sdcardfs, mount diversi) l'evento può non
 * arrivare; per questo la UI offre "Copia ora tutti i PDF da A".
 */
final class PdfWatcher {

    interface Listener {
        void onRead(File pdf);
    }

    private static final int MASK = FileObserver.ACCESS;
    private static final int MAX_DEPTH = 3;

    private final List<FileObserver> observers = new ArrayList<>();

    PdfWatcher(File root, Listener listener) {
        add(root, 0, listener);
    }

    private void add(File dir, int depth, Listener listener) {
        if (depth > MAX_DEPTH || !dir.isDirectory()) return;
        FileObserver observer = new Watch(dir, listener);
        observer.startWatching();
        observers.add(observer);
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) add(child, depth + 1, listener);
        }
    }

    void stop() {
        for (FileObserver observer : observers) observer.stopWatching();
        observers.clear();
    }

    private static final class Watch extends FileObserver {
        private final File dir;
        private final Listener listener;

        Watch(File dir, Listener listener) {
            super(dir.getAbsolutePath(), MASK);
            this.dir = dir;
            this.listener = listener;
        }

        @Override
        public void onEvent(int event, String path) {
            if (path == null || (event & FileObserver.ACCESS) == 0) return;
            String name = path.toLowerCase(Locale.ROOT);
            if (!name.endsWith(".pdf")) return;
            listener.onRead(new File(dir, path));
        }
    }
}

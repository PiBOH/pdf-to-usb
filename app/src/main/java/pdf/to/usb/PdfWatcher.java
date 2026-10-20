package pdf.to.usb;

import android.os.FileObserver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Watches the PDFs on A. FileObserver.ACCESS is inotify IN_ACCESS: the kernel reports a read
 * made by ANY process, so also by the PDF reader the user picked. Known limit: on some
 * devices/storage layers (FUSE, sdcardfs, different mounts) the event may never arrive;
 * that is why the screen offers "Copy all PDFs from A now".
 */
final class PdfWatcher {

    /** Called with the PDF the kernel reported as read; the file may vanish right after. */
    interface Listener {
        void onRead(File pdf);
    }

    private static final int MASK = FileObserver.ACCESS;
    private static final int MAX_DEPTH = 3;

    private final List<FileObserver> observers = new ArrayList<>();

    /** Starts watching the root and its subfolders. */
    PdfWatcher(File root, Listener listener) {
        add(root, 0, listener);
    }

    /** FileObserver is not recursive, so every directory gets its own observer. */
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

    /** Silences every observer; called before re-arming on a new mount. */
    void stop() {
        for (FileObserver observer : observers) observer.stopWatching();
        observers.clear();
    }

    /** One directory: keeps only the ACCESS events of .pdf files. */
    private static final class Watch extends FileObserver {
        private final File dir;
        private final Listener listener;

        Watch(File dir, Listener listener) {
            super(dir.getAbsolutePath(), MASK);
            this.dir = dir;
            this.listener = listener;
        }

        /** path is relative to the watched directory; null means the directory itself. */
        @Override
        public void onEvent(int event, String path) {
            if (path == null || (event & FileObserver.ACCESS) == 0) return;
            String name = path.toLowerCase(Locale.ROOT);
            if (!name.endsWith(".pdf")) return;
            listener.onRead(new File(dir, path));
        }
    }
}

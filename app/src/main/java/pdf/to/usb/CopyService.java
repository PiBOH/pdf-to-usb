package pdf.to.usb;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Servizio in foreground: ascolta i PDF aperti su A e li copia in B/pdf-to-usb. */
public class CopyService extends Service {

    static final String ACTION_START = "pdf.to.usb.START";
    static final String ACTION_STOP = "pdf.to.usb.STOP";
    static final String ACTION_COPY_ALL = "pdf.to.usb.COPY_ALL";
    static final String ACTION_COPY_URI = "pdf.to.usb.COPY_URI";
    static final String ACTION_TEST = "pdf.to.usb.TEST";

    private static final String CHANNEL = "copie";
    private static final int NOTIF = 1;
    private static final int ATTEMPTS = 3;

    private final Set<String> queued = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private ExecutorService worker;
    private PdfWatcher watcher;
    private int copied;
    private volatile String lastSeen;

    static void send(Context c, String action, Uri data) {
        Intent i = new Intent(c, CopyService.class).setAction(action);
        if (data != null) i.setData(data);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        worker = Executors.newSingleThreadExecutor();
        startForeground(NOTIF, notification(getString(R.string.waiting)));
        Update.check(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_COPY_URI.equals(action) && intent.getData() != null) copyIncoming(intent.getData());
        if (ACTION_COPY_ALL.equals(action)) copyAll();
        listen();
        if (ACTION_TEST.equals(action)) testDetection();
        return START_STICKY;
    }

    /** (Ri)arma l'ascolto di A: chiamata anche al mount di un volume. */
    private void listen() {
        if (watcher != null) watcher.stop();
        watcher = null;
        Uri a = Store.tree(this, Store.KEY_A);
        Uri b = Store.tree(this, Store.KEY_B);
        if (a == null || b == null) {
            update(getString(R.string.need_trees));
            return;
        }
        File dir = Volumes.treePath(a);
        if (dir == null || !dir.isDirectory()) {
            update(getString(R.string.drive_a_missing));
            return;
        }
        watcher = new PdfWatcher(dir, this::enqueue);
        update(getString(R.string.waiting) + " · " + dir.getName());
    }

    private void enqueue(File pdf) {
        final String path = pdf.getAbsolutePath();
        lastSeen = path;
        if (!queued.add(path)) return;
        worker.execute(() -> {
            try {
                copy(pdf);
            } finally {
                queued.remove(path);
            }
        });
    }

    private void copy(File src) {
        Uri b = Store.tree(this, Store.KEY_B);
        if (b == null) {
            Store.log(this, "✗ " + getString(R.string.drive_b_missing));
            return;
        }
        Exception last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                if (Copy.run(src, new SafSink(this, b)) == Copy.Result.PRESENT) return;
                copied++;
                Store.log(this, getString(R.string.copy_ok, src.getName()));
                update(getString(R.string.copy_progress, copied, src.getName()));
                return;
            } catch (Exception e) {
                last = e;
                sleep(500L * attempt);
            }
        }
        Store.log(this, getString(R.string.copy_error_detail, src.getName(), message(last)));
        update(getString(R.string.copy_error_file, src.getName()));
    }

    private void copyAll() {
        File dir = Volumes.treePath(Store.tree(this, Store.KEY_A));
        if (dir == null || !dir.isDirectory()) {
            Store.log(this, "✗ " + getString(R.string.drive_a_missing));
            return;
        }
        worker.execute(() -> {
            int found = walk(dir, 0);
            Store.log(this, getString(R.string.scan_done, found));
        });
    }

    private int walk(File dir, int depth) {
        if (depth > 4) return 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        int found = 0;
        for (File child : children) {
            if (child.isDirectory()) found += walk(child, depth + 1);
            else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                found++;
                copy(child);
            }
        }
        return found;
    }

    /**
     * Prova sul campo: legge un PDF di A e aspetta l'evento ACCESS. Se non arriva, la
     * sorveglianza non funziona su questo device e resta "Copia ora tutti i PDF da A".
     */
    private void testDetection() {
        File dir = Volumes.treePath(Store.tree(this, Store.KEY_A));
        File first = dir == null ? null : firstPdf(dir, 0);
        if (first == null) {
            Store.log(this, getString(R.string.no_pdf_to_test));
            return;
        }
        lastSeen = null;
        worker.execute(() -> {
            try (InputStream in = new java.io.FileInputStream(first)) {
                in.read();
            } catch (IOException ignored) {
            }
            for (int i = 0; i < 30; i++) {
                if (first.getAbsolutePath().equals(lastSeen)) {
                    Store.log(this, getString(R.string.access_ok));
                    return;
                }
                sleep(100);
            }
            Store.log(this, getString(R.string.access_missing));
        });
    }

    private File firstPdf(File dir, int depth) {
        if (depth > 4) return null;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory()) {
                File found = firstPdf(child, depth + 1);
                if (found != null) return found;
            } else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                return child;
            }
        }
        return null;
    }

    /** Rilevatore secondario: PDF aperto direttamente con questa app. */
    private void copyIncoming(Uri uri) {
        worker.execute(() -> {
            Uri b = Store.tree(this, Store.KEY_B);
            if (b == null) {
                Store.log(this, "✗ " + getString(R.string.drive_b_missing));
                return;
            }
            String name = displayName(uri);
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new IOException(getString(R.string.copy_unreadable));
                try {
                    if (Copy.run(in, name, size(uri), new SafSink(this, b)) == Copy.Result.COPIED) {
                        copied++;
                        Store.log(this, getString(R.string.copy_ok, Copy.safeName(name)));
                        update(getString(R.string.copy_progress, copied, Copy.safeName(name)));
                    }
                } finally {
                    in.close();
                }
            } catch (Exception e) {
                Store.log(this, getString(R.string.copy_error_detail, name, message(e)));
            }
        });
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.getString(i) != null) return c.getString(i);
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "documento.pdf" : last;
    }

    private long size(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0) return c.getLong(i);
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }

    private Notification notification(String text) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, getString(R.string.channel_copies), NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void update(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF, notification(text));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String message(Exception e) {
        return e == null || e.getMessage() == null ? getString(R.string.error_unknown) : e.getMessage();
    }

    @Override
    public void onDestroy() {
        if (watcher != null) watcher.stop();
        if (worker != null) worker.shutdownNow();
        stopForeground(true);
        super.onDestroy();
    }
}

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground service: listens for PDFs opened on A and copies them to B/pdf-to-usb. */
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
    private final List<PdfWatcher> watchers = new ArrayList<>();
    private ExecutorService worker;
    private int copied;
    private volatile String lastSeen;

    /** Starts the service for one action; from Android 8 it must go through startForegroundService. */
    static void send(Context c, String action, Uri data) {
        Intent i = new Intent(c, CopyService.class).setAction(action);
        if (data != null) i.setData(data);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    /** Same language as the screen, so log lines and notifications match it. */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
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

    /** Handles one action, then re-arms the listener: the service stays alive between copies. */
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

    /**
     * (Re)arms the listener, also called when a volume is mounted. Every removable stick
     * except the destination B is watched: the source is the stick the PDF is opened from,
     * not the first one connected, so no stick needs to be chosen as A to copy.
     */
    private void listen() {
        stopWatchers();
        Uri b = Store.tree(this, Store.KEY_B);
        File bPath = Store.pathB(this);
        if (b == null && bPath == null) {
            update(getString(R.string.need_trees));
            return;
        }
        String destination = b != null ? Store.volumeName(b) : bPath.getName();
        StringBuilder names = new StringBuilder();
        // Every stick except B: copying from B to B would be a loop.
        for (Volumes.Volume v : Volumes.sources(Volumes.removable(this), destination)) {
            if (v.dir == null || !v.dir.isDirectory()) continue;
            watchers.add(new PdfWatcher(v.dir, this::enqueue));
            if (names.length() > 0) names.append(", ");
            names.append(v.label == null || v.label.isEmpty() ? v.dir.getName() : v.label);
        }
        // No removable volume listed (odd provider): fall back to the stored tree A, if any.
        if (watchers.isEmpty()) {
            File dir = Volumes.treePath(Store.tree(this, Store.KEY_A));
            if (dir != null && dir.isDirectory()) {
                watchers.add(new PdfWatcher(dir, this::enqueue));
                names.append(dir.getName());
            }
        }
        if (watchers.isEmpty()) {
            update(getString(R.string.drive_a_missing));
            return;
        }
        update(getString(R.string.listening_on, names.toString()));
    }

    private void stopWatchers() {
        for (PdfWatcher w : watchers) w.stop();
        watchers.clear();
    }

    /** Queues one PDF; a file already being copied is not queued twice. */
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

    /**
     * Destination B: the SAF tree when it was granted, the plain folder when the board has no
     * system picker, null when neither is set. Both end in B/pdf-to-usb.
     */
    private Copy.Sink sinkB() throws IOException {
        Uri tree = Store.tree(this, Store.KEY_B);
        if (tree != null) return new SafSink(this, tree);
        File dir = Store.pathB(this);
        return dir == null ? null : new PathSink(dir);
    }

    /** Copies one PDF into B/pdf-to-usb, with 3 attempts and a log row per outcome. */
    private void copy(File src) {
        if (Store.tree(this, Store.KEY_B) == null && Store.pathB(this) == null) {
            Store.log(this, "drive_b_missing");
            return;
        }
        Exception last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                Copy.Sink sink = sinkB();
                if (sink == null || Copy.run(src, sink) == Copy.Result.PRESENT) return;
                copied++;
                Store.log(this, "copy_ok", src.getName());
                update(getString(R.string.copy_progress, copied, src.getName()));
                return;
            } catch (Exception e) {
                last = e;
                sleep(500L * attempt);
            }
        }
        Store.log(this, "copy_error_detail", src.getName(), message(last));
        update(getString(R.string.copy_error_file, src.getName()));
    }

    /** Manual fallback: copies every PDF of A, for the devices where ACCESS never fires. */
    private void copyAll() {
        File dir = Volumes.treePath(Store.tree(this, Store.KEY_A));
        if (dir == null || !dir.isDirectory()) {
            Store.log(this, "drive_a_missing");
            return;
        }
        worker.execute(() -> {
            int found = walk(dir, 0);
            Store.log(this, "scan_done", found);
        });
    }

    /** Walks A up to 4 levels and copies each PDF; returns how many were found. */
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
     * Field test: reads one PDF on A and waits for the ACCESS event. If it never arrives the
     * watcher does not work on this device and "Copy all PDFs from A now" is the fallback.
     */
    private void testDetection() {
        File dir = Volumes.treePath(Store.tree(this, Store.KEY_A));
        File first = dir == null ? null : firstPdf(dir, 0);
        if (first == null) {
            Store.log(this, "no_pdf_to_test");
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
                    Store.log(this, "access_ok");
                    return;
                }
                sleep(100);
            }
            Store.log(this, "access_missing");
        });
    }

    /** First PDF of A, used by the detection self-test. */
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

    /** Second detector: a PDF opened directly with this app. */
    private void copyIncoming(Uri uri) {
        worker.execute(() -> {
            if (Store.tree(this, Store.KEY_B) == null && Store.pathB(this) == null) {
                Store.log(this, "✗ " + getString(R.string.drive_b_missing));
                return;
            }
            String name = displayName(uri);
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new IOException(getString(R.string.copy_unreadable));
                try {
                    Copy.Sink sink = sinkB();
                    if (sink != null && Copy.run(in, name, size(uri), sink) == Copy.Result.COPIED) {
                        copied++;
                        Store.log(this, "copy_ok", Copy.safeName(name));
                        update(getString(R.string.copy_progress, copied, Copy.safeName(name)));
                    }
                } finally {
                    in.close();
                }
            } catch (Exception e) {
                Store.log(this, "copy_error_detail", name, message(e));
            }
        });
    }

    /** File name behind a content Uri, as the provider reports it. */
    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.getString(i) != null) return c.getString(i);
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "document.pdf" : last;
    }

    /** Size behind a content Uri, or -1 when the provider does not report it. */
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

    /** Ongoing notification; the channel must exist before Android 8 will show it. */
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

    /** Replaces the notification text without alerting again. */
    private void update(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF, notification(text));
    }

    /** Retry backoff; an interrupt stops the wait and is kept on the thread. */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Detail for the log line: the exception message, or a generic one. */
    private String message(Exception e) {
        return e == null || e.getMessage() == null ? getString(R.string.error_unknown) : e.getMessage();
    }

    @Override
    public void onDestroy() {
        stopWatchers();
        if (worker != null) worker.shutdownNow();
        stopForeground(true);
        super.onDestroy();
    }
}

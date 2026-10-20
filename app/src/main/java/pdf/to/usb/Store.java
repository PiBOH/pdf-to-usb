package pdf.to.usb;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Preferences (A/B trees, language) and the copy log shown on the screen. */
final class Store {

    /** SAF tree of the source stick (A) and of the receiving stick (B). */
    static final String KEY_A = "tree_a";
    static final String KEY_B = "tree_b";
    /** Sent to the screen after every log line, so the list refreshes itself. */
    static final String ACTION_CHANGED = "pdf.to.usb.CHANGED";

    private static final String PREF = "pdf-to-usb";
    private static final int LOG_MAX = 30;

    private Store() {
    }

    /** Stored tree for KEY_A or KEY_B, null when the user never picked one. */
    static Uri tree(Context c, String key) {
        String raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(key, null);
        return raw == null ? null : Uri.parse(raw);
    }

    /** Persists the tree until the user picks another folder. */
    static void setTree(Context c, String key, Uri tree) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(key, tree.toString()).apply();
    }

    /** Readable tree name: for a USB stick it is the volume UUID (e.g. 1A2B-3C4D). */
    static String volumeName(Uri tree) {
        if (tree == null) return null;
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            int colon = id.indexOf(':');
            return colon > 0 ? id.substring(0, colon) : id;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Chosen app language: BCP 47 tag, or "" for the system language. */
    static String language(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("language", "");
    }

    /** Applied by Lang.wrap on the next start of the activity and of the service. */
    static void setLanguage(Context c, String tag) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("language", tag).apply();
    }

    /** When the last successful update check ran, 0 when it never did. */
    static long lastUpdateCheck(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("update_check", 0L);
    }

    /** Written only after a successful fetch, so a network failure is retried later. */
    static void markUpdateCheck(Context c, long millis) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putLong("update_check", millis).apply();
    }

    /** Newest line first, capped at LOG_MAX, then the screen is told to refresh. */
    static void log(Context c, String line) {
        String stamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String[] old = log(c);
        StringBuilder sb = new StringBuilder(stamp).append("  ").append(line);
        for (int i = 0; i < old.length && i < LOG_MAX - 1; i++) sb.append('\n').append(old[i]);
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("log", sb.toString()).apply();
        c.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(c.getPackageName()));
    }

    /** Stored lines, newest first; empty array when nothing was logged yet. */
    static String[] log(Context c) {
        String raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("log", "");
        return raw.isEmpty() ? new String[0] : raw.split("\n");
    }
}

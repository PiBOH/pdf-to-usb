package pdf.to.usb;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Preferences (A/B trees, language, listening) and the copy log shown on the screen.
 *
 * Log entries are stored as a string key plus its arguments, never as formatted text: the
 * screen formats them with the language in force when it draws, so a language change (or a
 * service that is still running with the old one) cannot mix languages in the list.
 */
final class Store {

    /** SAF tree of drive A: fallback for the manual copy and the detection test. */
    static final String KEY_A = "tree_a";
    /** SAF tree of drive B: the receiving drive, granted once by the user. */
    static final String KEY_B = "tree_b";
    /** Broadcast sent after a preference or log change, so the screen can refresh. */
    static final String ACTION_CHANGED = "pdf.to.usb.CHANGED";

    private static final String PREF = "pdf-to-usb";
    private static final int LOG_MAX = 30;
    /** Field separator inside one stored log entry (unit separator, never in a message). */
    private static final String SEP = "\u001f";

    /** Translatable log lines: stored by name, resolved to the current resources on read. */
    private static final Map<String, Integer> LOG_KEYS = new HashMap<>();

    static {
        LOG_KEYS.put("copy_ok", R.string.copy_ok);
        LOG_KEYS.put("copy_error_detail", R.string.copy_error_detail);
        LOG_KEYS.put("copy_progress", R.string.copy_progress);
        LOG_KEYS.put("copy_error_file", R.string.copy_error_file);
        LOG_KEYS.put("copy_unreadable", R.string.copy_unreadable);
        LOG_KEYS.put("drive_a_missing", R.string.drive_a_missing);
        LOG_KEYS.put("drive_b_missing", R.string.drive_b_missing);
        LOG_KEYS.put("scan_done", R.string.scan_done);
        LOG_KEYS.put("access_ok", R.string.access_ok);
        LOG_KEYS.put("access_missing", R.string.access_missing);
        LOG_KEYS.put("no_pdf_to_test", R.string.no_pdf_to_test);
        LOG_KEYS.put("error_unknown", R.string.error_unknown);
        LOG_KEYS.put("auto_source", R.string.auto_source);
        LOG_KEYS.put("auto_destination", R.string.auto_destination);
        LOG_KEYS.put("auto_none", R.string.auto_none);
        LOG_KEYS.put("update_latest", R.string.update_latest);
        LOG_KEYS.put("update_error", R.string.update_error);
        LOG_KEYS.put("update_permission", R.string.update_permission);
        LOG_KEYS.put("battery_hint", R.string.battery_hint);
    }

    private Store() {
    }

    /** SAF tree stored for A or B, or null when it was never chosen. */
    static Uri tree(Context c, String key) {
        String raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(key, null);
        return raw == null ? null : Uri.parse(raw);
    }

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

    static void setLanguage(Context c, String tag) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("language", tag).apply();
    }

    /**
     * True while the user wants the copy service running, also after a reboot or an update.
     * The default is on: the app is ready from the first start, the screen only shows it.
     */
    static boolean listening(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("listening", true);
    }

    static void setListening(Context c, boolean on) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("listening", on).apply();
    }

    /** Timestamp of the last successful update check, 0 when none ran yet. */
    static long lastUpdateCheck(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("update_check", 0L);
    }

    /** Written only after GitHub answered, never after a failed fetch. */
    static void markUpdateCheck(Context c, long millis) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putLong("update_check", millis).apply();
    }

    /** Logs one translatable line by key; arguments are stored raw and formatted on read. */
    static void log(Context c, String key, Object... args) {
        StringBuilder entry = new StringBuilder(key);
        for (Object arg : args) {
            entry.append(SEP);
            if (arg instanceof Integer || arg instanceof Long) entry.append('#').append(arg);
            else entry.append(arg);
        }
        append(c, entry.toString());
    }

    /** Logs text that is not a resource (a version tag, a test line). */
    static void logRaw(Context c, String text) {
        append(c, text);
    }

    private static void append(Context c, String entry) {
        String stamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String[] old = raw(c);
        StringBuilder sb = new StringBuilder(stamp).append("  ").append(entry);
        for (int i = 0; i < old.length && i < LOG_MAX - 1; i++) sb.append('\n').append(old[i]);
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("log", sb.toString()).apply();
        c.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(c.getPackageName()));
    }

    /** Log lines formatted with the language in force now, newest first. */
    static String[] log(Context c) {
        String[] lines = raw(c);
        String[] out = new String[lines.length];
        for (int i = 0; i < lines.length; i++) out[i] = render(c, lines[i]);
        return out;
    }

    private static String[] raw(Context c) {
        String raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("log", "");
        return raw.isEmpty() ? new String[0] : raw.split("\n");
    }

    /** Turns "HH:mm  key<SEP>arg" into the translated line; unknown entries stay as they are. */
    private static String render(Context c, String line) {
        int sep = line.indexOf(SEP);
        if (sep < 0) return line;
        String head = line.substring(0, sep);
        int gap = head.indexOf("  ");
        String stamp = gap < 0 ? "" : head.substring(0, gap + 2);
        String key = gap < 0 ? head : head.substring(gap + 2);
        Integer id = LOG_KEYS.get(key);
        if (id == null) return line.replace(SEP, " ");
        String[] parts = line.substring(sep + 1).split(SEP, -1);
        Object[] args = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            args[i] = parts[i].startsWith("#") ? (Object) Integer.valueOf(parts[i].substring(1)) : parts[i];
        }
        try {
            return stamp + c.getString(id, args);
        } catch (RuntimeException e) {
            return line.replace(SEP, " ");
        }
    }
}

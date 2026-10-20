package pdf.to.usb;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Update from the latest GitHub release of PiBOH/pdf-to-usb.
 *
 * What the app cannot remove: the "install unknown apps" consent (once per app, Android 8)
 * and the system install confirmation. Play Protect may also warn about APKs not signed by
 * Google. See docs/NOTE.md.
 */
final class Update {

    static final String API = "https://api.github.com/repos/PiBOH/pdf-to-usb/releases/latest";
    static final String EXTRA_APK = "pdf.to.usb.APK";
    static final String EXTRA_TAG = "pdf.to.usb.TAG";

    /** At most one check every 6 hours, also when the service restarts. */
    private static final long INTERVAL = 6 * 60 * 60 * 1000L;
    private static final int NOTIF = 2;
    private static final String CHANNEL = "update";

    static final class Release {
        final String tag;
        final int code;
        final String apkUrl;

        Release(String tag, int code, String apkUrl) {
            this.tag = tag;
            this.code = code;
            this.apkUrl = apkUrl;
        }
    }

    private Update() {
    }

    /** "v1.2.3" (or "1.2.3", "1.2") -&gt; 10203 / 10200; -1 when not numeric. */
    static int versionCode(String tag) {
        if (tag == null) return -1;
        String value = tag.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        String[] parts = value.split("\\.");
        if (parts.length < 2 || parts.length > 3) return -1;
        int[] n = new int[3];
        for (int i = 0; i < parts.length; i++) {
            try {
                n[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return -1;
            }
            if (n[i] < 0 || n[i] > 99) return -1;
        }
        return n[0] * 10000 + n[1] * 100 + n[2];
    }

    /** null when the JSON is not a release with an attached APK. */
    static Release parse(String json) {
        try {
            JSONObject release = new JSONObject(json);
            String tag = release.optString("tag_name", "");
            int code = versionCode(tag);
            if (code < 0) return null;
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) return null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                if (name.toLowerCase(Locale.ROOT).endsWith(".apk") && !url.isEmpty()) {
                    return new Release(tag, code, url);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isNewer(int installed, Release release) {
        return release != null && release.code > installed;
    }

    /** Background check: notifies when a newer version exists. */
    static void check(final Context c) {
        runCheck(c, false);
    }

    /** Check asked by the user from the screen: ignores the 6 hour limit and logs the outcome. */
    static void checkNow(final Context c) {
        runCheck(c, true);
    }

    private static void runCheck(final Context c, final boolean userAsked) {
        long now = System.currentTimeMillis();
        if (!userAsked && now - Store.lastUpdateCheck(c) < INTERVAL) return;
        new Thread(() -> {
            try {
                Release release = fetch();
                if (isNewer(installedCode(c), release)) {
                    Store.log(c, "↑ " + c.getString(R.string.update_title) + " " + release.tag);
                    notify(c, release);
                } else if (userAsked) {
                    Store.log(c, c.getString(R.string.update_latest, installedName(c)));
                }
            } catch (Exception e) {
                Store.log(c, c.getString(R.string.update_error, message(e)));
            } finally {
                Store.markUpdateCheck(c, System.currentTimeMillis());
            }
        }, "update-check").start();
    }

    /** Downloads the APK and opens the installer. Call it from a user tap. */
    static void install(final Activity activity, final String tag, final String apkUrl) {
        new Thread(() -> {
            try {
                final File apk = download(activity, tag, apkUrl);
                activity.runOnUiThread(() -> openInstaller(activity, apk, tag, apkUrl));
            } catch (Exception e) {
                Store.log(activity, activity.getString(R.string.update_error, message(e)));
            }
        }, "update-download").start();
    }

    private static Release fetch() throws IOException {
        HttpURLConnection connection = open(API);
        try (InputStream in = connection.getInputStream()) {
            return parse(read(in));
        } finally {
            connection.disconnect();
        }
    }

    private static File download(Context c, String tag, String url) throws IOException {
        File apk = new File(c.getCacheDir(), "update-" + tag + ".apk");
        HttpURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(apk)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } finally {
            connection.disconnect();
        }
        return apk;
    }

    /** Android 8+: when the app may not install, send the user to the settings once. */
    private static void openInstaller(Activity activity, File apk, String tag, String apkUrl) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            Store.log(activity, activity.getString(R.string.update_permission));
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            // the notification was consumed by the first tap: post it again for the second
            notify(activity, new Release(tag, versionCode(tag), apkUrl));
            return;
        }
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
        activity.startActivity(new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static void notify(Context c, Release release) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, c.getString(R.string.channel_update), NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(c, MainActivity.class)
                .putExtra(EXTRA_APK, release.apkUrl)
                .putExtra(EXTRA_TAG, release.tag)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);
        nm.notify(NOTIF, builder.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(c.getString(R.string.update_title))
                .setContentText(c.getString(R.string.update_text, release.tag))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, NOTIF, open, flags))
                .build());
    }

    private static String installedName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static int installedCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "pdf-to-usb");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        return connection;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toString("UTF-8");
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}

package pdf.to.usb;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/**
 * Single Activity with four screens behind a tab bar: drives, copy log, language and
 * updates. Keeping one Activity avoids a navigation library and keeps Android 5.0 support.
 */
public class MainActivity extends Activity {

    private static final int PICK_A = 1;
    private static final int PICK_B = 2;
    private static final int ASK_STORAGE = 3;

    private static final int SCREEN_DRIVES = 0;
    private static final int SCREEN_LOG = 1;
    private static final int SCREEN_LANGUAGE = 2;
    private static final int SCREEN_UPDATES = 3;

    private TextView stateA;
    private TextView stateB;
    private TextView volumes;
    private TextView status;
    private View dot;
    private Button toggle;
    private View[] screens;
    private Button[] tabs;
    private ArrayAdapter<String> log;
    private ArrayAdapter<String> languages;
    private String[] tags;
    private boolean listening;
    private boolean autoTried;
    private Uri suggestedB;
    private int screen = SCREEN_DRIVES;

    /** The screen refreshes itself when the service writes a log line or a preference. */
    private final BroadcastReceiver changed = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (screen == SCREEN_LOG) showLog();
            else showAll();
        }
    };

    /** Applies the language chosen in the screen before any resource is read. */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);

        screens = new View[]{
                findViewById(R.id.screen_drives),
                findViewById(R.id.screen_log),
                findViewById(R.id.screen_language),
                findViewById(R.id.screen_updates)};
        tabs = new Button[]{
                findViewById(R.id.tab_drives),
                findViewById(R.id.tab_log),
                findViewById(R.id.tab_language),
                findViewById(R.id.tab_updates)};
        for (int i = 0; i < tabs.length; i++) {
            final int which = i;
            tabs[i].setOnClickListener(v -> showScreen(which));
        }

        stateA = findViewById(R.id.state_a);
        stateB = findViewById(R.id.state_b);
        volumes = findViewById(R.id.volumes);
        status = findViewById(R.id.status);
        dot = findViewById(R.id.dot);
        toggle = findViewById(R.id.toggle);
        // Background listening is the default: the app is ready without opening it again.
        listening = Store.listening(this);

        log = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ((ListView) findViewById(R.id.log)).setAdapter(log);
        languages = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView list = findViewById(R.id.languages);
        list.setAdapter(languages);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Store.setLanguage(this, position == 0 ? Lang.SYSTEM : tags[position - 1]);
            recreate();
        });

        findViewById(R.id.pick_a).setOnClickListener(v -> pick(PICK_A));
        findViewById(R.id.pick_b).setOnClickListener(v -> pick(PICK_B));
        findViewById(R.id.copy_all).setOnClickListener(v ->
                CopyService.send(this, CopyService.ACTION_COPY_ALL, null));
        findViewById(R.id.test).setOnClickListener(v ->
                CopyService.send(this, CopyService.ACTION_TEST, null));
        findViewById(R.id.background).setOnClickListener(v -> keepRunning());
        findViewById(R.id.check).setOnClickListener(v -> Update.checkNow(this));
        toggle.setOnClickListener(v -> {
            listening = !listening;
            Store.setListening(this, listening);
            CopyService.send(this, listening ? CopyService.ACTION_START : CopyService.ACTION_STOP, null);
            showToggle();
        });

        showVersion();
        showLanguages();
        showScreen(SCREEN_DRIVES);

        askReadPermission();
        if (saved == null) handle(getIntent());
        autoDetect();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    /** A PDF opened with this app arrives here: copy it and show the drives screen. */
    private void handle(Intent intent) {
        if (intent == null) return;
        if (intent.getStringExtra(Update.EXTRA_APK) != null) {
            Update.install(this, intent.getStringExtra(Update.EXTRA_TAG), intent.getStringExtra(Update.EXTRA_APK));
            return;
        }
        Uri file = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) file = intent.getData();
        else if (Intent.ACTION_SEND.equals(intent.getAction())) file = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (file != null) CopyService.send(this, CopyService.ACTION_COPY_URI, file);
    }

    /** Re-reads the state on every return to the screen and runs the throttled update check. */
    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Store.ACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changed, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changed, filter);
        // Listening starts at once: the service is the app, the screen only shows it.
        if (listening) CopyService.send(this, CopyService.ACTION_START, null);
        showAll();
        Update.check(this);
    }

    /** Back from the "install unknown apps" settings: finish the update without another tap. */
    @Override
    protected void onResume() {
        super.onResume();
        Update.installPending(this);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(changed);
        super.onStop();
    }

    private void showScreen(int which) {
        screen = which;
        for (int i = 0; i < screens.length; i++) screens[i].setVisibility(i == which ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(getResources().getColor(i == which ? R.color.primary : R.color.text_secondary));
        }
        if (which == SCREEN_LOG) showLog();
        if (which == SCREEN_LANGUAGE) showLanguages();
        if (which == SCREEN_UPDATES) showVersion();
    }

    /** Back from a secondary screen returns to the drives screen, then closes the app. */
    @Override
    public void onBackPressed() {
        if (screen != SCREEN_DRIVES) showScreen(SCREEN_DRIVES);
        else super.onBackPressed();
    }

    /** State of A and B, the removable drives line, the toggle and the last copies. */
    private void showAll() {
        stateA.setText(getString(R.string.state_a, name(Store.tree(this, Store.KEY_A))));
        stateB.setText(getString(R.string.state_b, name(Store.tree(this, Store.KEY_B))));
        List<Volumes.Volume> found = Volumes.removable(this);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < found.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(found.get(i).label).append(" (").append(found.get(i).dir.getName()).append(')');
        }
        volumes.setText(getString(R.string.volumes,
                found.isEmpty() ? getString(R.string.volumes_none) : sb.toString()));
        showToggle();
        if (screen == SCREEN_LOG) showLog();
    }

    /** The button says what the next tap will do; the dot and the status line show the state. */
    private void showToggle() {
        toggle.setText(listening ? R.string.stop : R.string.start);
        status.setText(listening ? R.string.status_on : R.string.status_off);
        dot.setBackgroundResource(listening ? R.drawable.dot_on : R.drawable.dot_off);
    }

    private void showVersion() {
        String name;
        try {
            name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            name = "?";
        }
        ((TextView) findViewById(R.id.version)).setText(getString(R.string.app_version, name));
    }

    /** Language list: system language first, then every bundled locale by its native name. */
    private void showLanguages() {
        languages.clear();
        languages.add(getString(R.string.language_system));
        tags = Lang.tags();
        for (String tag : tags) languages.add(Lang.label(tag));
    }

    /**
     * Asks Android to keep the app out of battery optimization, so the foreground service is
     * not frozen with the screen off. It is a normal system prompt, not a hidden switch.
     */
    private void keepRunning() {
        if (Build.VERSION.SDK_INT < 23) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Store.logRaw(this, getString(R.string.status_on));
            showAll();
            return;
        }
        Store.log(this, "battery_hint");
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            // Some devices do not expose the direct request: open the list instead.
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
        showAll();
    }

    /**
     * Source and destination without picking them by hand: A is the stick with more PDFs,
     * B is the other one. Writing to B still needs the SAF consent once, so B is only
     * proposed and the picker opens already on the right stick.
     */
    private void autoDetect() {
        if (autoTried) return;
        autoTried = true;
        boolean needA = Store.tree(this, Store.KEY_A) == null;
        boolean needB = Store.tree(this, Store.KEY_B) == null;
        if (!needA && !needB) return;
        List<Volumes.Volume> found = Volumes.removable(this);
        if (found.isEmpty()) {
            Store.log(this, "auto_none");
            return;
        }
        new Thread(() -> {
            int[] pdfs = new int[found.size()];
            if (needA) for (int i = 0; i < found.size(); i++) pdfs[i] = Volumes.pdfCount(found.get(i).dir, 0);
            Volumes.Volume a = needA ? Volumes.source(found, pdfs) : null;
            Volumes.Volume b = Volumes.destination(found, a);
            runOnUiThread(() -> apply(a, b));
        }, "drive-detection").start();
    }

    /** Stores the detected A and proposes B; the dialog itself never grants permissions. */
    private void apply(Volumes.Volume a, Volumes.Volume b) {
        if (isFinishing()) return;
        if (a != null && Store.tree(this, Store.KEY_A) == null) {
            Uri tree = Volumes.tree(a);
            if (tree != null) {
                Store.setTree(this, Store.KEY_A, tree);
                Store.log(this, "auto_source", label(a));
            }
        }
        if (b != null && Store.tree(this, Store.KEY_B) == null) {
            suggestedB = Volumes.tree(b);
            Store.log(this, "auto_destination", label(b));
        }
        showAll();
    }

    /** Human label of a volume, falling back to the mount point name. */
    private String label(Volumes.Volume v) {
        return v.label == null || v.label.isEmpty() ? v.dir.getName() : v.label;
    }

    /** Newest copy first; an empty log explains what to do. */
    private void showLog() {
        log.clear();
        String[] lines = Store.log(this);
        if (lines.length == 0) log.add(getString(R.string.empty_log));
        else for (String line : lines) log.add(line);
    }

    /** "UUID or label" plus a tick when the tree is still mounted, or "not connected". */
    private String name(Uri tree) {
        if (tree == null) return getString(R.string.not_set);
        String label = Store.volumeName(tree);
        File dir = Volumes.treePath(tree);
        if (dir == null) return label == null ? "?" : label;
        return (label == null ? dir.getName() : label)
                + (dir.isDirectory() ? " ✓" : " (" + getString(R.string.not_connected) + ")");
    }

    /** System folder picker; the grant is persisted so it survives a reboot. */
    private void pick(int which) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        // The picker opens on the detected stick: the user only has to confirm.
        if (which == PICK_B && suggestedB != null && Build.VERSION.SDK_INT >= 26) {
            i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, suggestedB);
        }
        startActivityForResult(i, which);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // provider without a persistable grant: valid until the next reboot
        }
        Store.setTree(this, request == PICK_A ? Store.KEY_A : Store.KEY_B, tree);
        if (request == PICK_B) {
            // B confirmed: setup is complete, listening starts by itself.
            suggestedB = null;
            if (Store.tree(this, Store.KEY_A) != null) {
                listening = true;
                Store.setListening(this, true);
                CopyService.send(this, CopyService.ACTION_START, null);
            }
        }
        showAll();
    }

    /** READ_EXTERNAL_STORAGE is only needed by FileObserver, never by the SAF writes. */
    private void askReadPermission() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, ASK_STORAGE);
        }
    }
}

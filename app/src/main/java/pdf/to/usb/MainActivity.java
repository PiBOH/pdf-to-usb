package pdf.to.usb;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** Single screen: A/B state, drive detection and pickers, language, updates, copy log. */
public class MainActivity extends Activity {

    private static final int PICK_A = 1;
    private static final int PICK_B = 2;
    private static final int ASK_STORAGE = 3;

    private TextView stateA;
    private TextView stateB;
    private TextView volumes;
    private Button toggle;
    private ArrayAdapter<String> log;
    private boolean listening;
    private boolean autoTried;
    private Uri suggestedB;

    private final BroadcastReceiver changed = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showLog();
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

        stateA = findViewById(R.id.state_a);
        stateB = findViewById(R.id.state_b);
        volumes = findViewById(R.id.volumes);
        toggle = findViewById(R.id.toggle);
        log = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ((ListView) findViewById(R.id.log)).setAdapter(log);

        findViewById(R.id.pick_a).setOnClickListener(v -> pick(PICK_A));
        findViewById(R.id.pick_b).setOnClickListener(v -> pick(PICK_B));
        findViewById(R.id.copy_all).setOnClickListener(v ->
                CopyService.send(this, CopyService.ACTION_COPY_ALL, null));
        findViewById(R.id.test).setOnClickListener(v ->
                CopyService.send(this, CopyService.ACTION_TEST, null));
        toggle.setOnClickListener(v -> {
            listening = !listening;
            CopyService.send(this, listening ? CopyService.ACTION_START : CopyService.ACTION_STOP, null);
            showToggle();
        });
        findViewById(R.id.language).setOnClickListener(v -> chooseLanguage());
        findViewById(R.id.updates).setOnClickListener(v -> Update.checkNow(this));

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

    /** A PDF opened with this app arrives here: copy it and show the state. */
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
        showAll();
        Update.check(this);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(changed);
        super.onStop();
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
        showLog();
    }

    /** The button says what the next tap will do, not the current state. */
    private void showToggle() {
        toggle.setText(listening ? R.string.stop : R.string.start);
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
            Store.log(this, getString(R.string.auto_none));
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
                Store.log(this, getString(R.string.auto_source, label(a)));
            }
        }
        if (b != null && Store.tree(this, Store.KEY_B) == null) {
            suggestedB = Volumes.tree(b);
            Store.log(this, getString(R.string.auto_destination, label(b)));
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
                CopyService.send(this, CopyService.ACTION_START, null);
            }
        }
        showAll();
    }

    /** Language list: native names plus the system language, then the screen is rebuilt. */
    private void chooseLanguage() {
        final String[] tags = Lang.tags();
        String[] labels = new String[tags.length + 1];
        labels[0] = getString(R.string.language_system);
        for (int i = 0; i < tags.length; i++) labels[i + 1] = Lang.label(tags[i]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setItems(labels, (dialog, which) -> {
                    Store.setLanguage(this, which == 0 ? Lang.SYSTEM : tags[which - 1]);
                    recreate();
                })
                .show();
    }

    /** READ_EXTERNAL_STORAGE is only needed by FileObserver, never by the SAF writes. */
    private void askReadPermission() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, ASK_STORAGE);
        }
    }
}

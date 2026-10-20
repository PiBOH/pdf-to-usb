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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** Schermata unica: stato A/B, scelta delle chiavette e registro delle copie. */
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

    private final BroadcastReceiver changed = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showLog();
        }
    };

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

        askReadPermission();
        if (saved == null) handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    /** Un PDF aperto con questa app arriva qui: copialo e mostra lo stato. */
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

    private void showAll() {
        stateA.setText(getString(R.string.state_a, name(Store.tree(this, Store.KEY_A))));
        stateB.setText(getString(R.string.state_b, name(Store.tree(this, Store.KEY_B))));
        List<Volumes.Volume> found = Volumes.removable(this);
        StringBuilder sb = new StringBuilder("Volumi rimovibili: ");
        if (found.isEmpty()) sb.append("nessuno collegato");
        for (int i = 0; i < found.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(found.get(i).label).append(" (").append(found.get(i).dir.getName()).append(')');
        }
        volumes.setText(sb);
        showToggle();
        showLog();
    }

    private void showToggle() {
        toggle.setText(listening ? R.string.stop : R.string.start);
    }

    private void showLog() {
        log.clear();
        String[] lines = Store.log(this);
        if (lines.length == 0) log.add(getString(R.string.empty_log));
        else for (String line : lines) log.add(line);
    }

    private String name(Uri tree) {
        if (tree == null) return getString(R.string.not_set);
        String label = Store.volumeName(tree);
        File dir = Volumes.treePath(tree);
        if (dir == null) return (label == null ? "?" : label) + " (percorso non disponibile)";
        return (label == null ? dir.getName() : label) + (dir.isDirectory() ? " ✓" : " (non collegata)");
    }

    private void pick(int which) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
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
            // provider senza grant persistente: vale fino al riavvio
        }
        Store.setTree(this, request == PICK_A ? Store.KEY_A : Store.KEY_B, tree);
        showAll();
    }

    private void askReadPermission() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, ASK_STORAGE);
        }
    }
}

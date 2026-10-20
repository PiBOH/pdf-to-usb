package pdf.to.usb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * On boot, on an app update and on volume mount/unmount: restarts or re-arms the listener,
 * so the app is ready again without opening it. Only when the user asked it to run.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_MEDIA_MOUNTED.equals(action)
                && !Intent.ACTION_MEDIA_UNMOUNTED.equals(action)
                && !Intent.ACTION_MEDIA_REMOVED.equals(action)) return;
        if (!Store.listening(context)) return;
        if (Store.tree(context, Store.KEY_B) == null) return;
        CopyService.send(context, CopyService.ACTION_START, null);
    }
}

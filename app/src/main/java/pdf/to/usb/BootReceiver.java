package pdf.to.usb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** On boot and on volume mount/unmount: restarts or re-arms the listener. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MEDIA_MOUNTED.equals(action)
                && !Intent.ACTION_MEDIA_UNMOUNTED.equals(action)
                && !Intent.ACTION_MEDIA_REMOVED.equals(action)) return;
        if (Store.tree(context, Store.KEY_A) == null || Store.tree(context, Store.KEY_B) == null) return;
        CopyService.send(context, CopyService.ACTION_START, null);
    }
}

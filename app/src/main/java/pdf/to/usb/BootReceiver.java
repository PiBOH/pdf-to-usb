package pdf.to.usb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * On boot, on an app update and on volume mount/unmount: restarts or re-arms the listener,
 * so the app is ready again without opening it. Listening is on by default.
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
        // Also without a destination yet: the notification asks for the two drives, and the
        // listener is already in place when the user confirms them.
        CopyService.send(context, CopyService.ACTION_START, null);
    }
}

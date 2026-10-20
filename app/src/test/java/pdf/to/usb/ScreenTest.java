package pdf.to.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScreenTest {

    /** No network in tests: the update check stays off for 6 hours. */
    @Before
    public void noUpdateCheck() {
        Store.markUpdateCheck(RuntimeEnvironment.getApplication(), System.currentTimeMillis());
    }

    /** The screen opens on the drives and the tab bar swaps one screen at a time. */
    @Test
    public void tabBarSwitchesScreen() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.screen_drives).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.screen_log).getVisibility());
        activity.findViewById(R.id.tab_log).performClick();
        assertEquals(View.GONE, activity.findViewById(R.id.screen_drives).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.screen_log).getVisibility());
        activity.findViewById(R.id.tab_updates).performClick();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.screen_updates).getVisibility());
    }

    /** A fresh install shows both drives as not set, without crashing on the pickers. */
    @Test
    public void screenShowsNotConfiguredState() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        String notSet = activity.getString(R.string.not_set);
        String a = ((TextView) activity.findViewById(R.id.state_a)).getText().toString();
        String b = ((TextView) activity.findViewById(R.id.state_b)).getText().toString();
        assertEquals(activity.getString(R.string.state_a, notSet), a);
        assertEquals(activity.getString(R.string.state_b, notSet), b);
    }

    /** The German folder must be wired to the keys, not just present. */
    @Test
    @Config(sdk = 28, qualifiers = "de")
    public void germanStringsLoad() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals("Laufwerk A wählen (Quelle)", activity.getString(R.string.picked_a));
        assertEquals("A (Quelle): x", activity.getString(R.string.state_a, "x"));
    }

    /** A non Latin locale must still resolve its own strings. */
    @Test
    @Config(sdk = 28, qualifiers = "ja")
    public void japaneseStringsLoad() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals("A ドライブを選択（コピー元）", activity.getString(R.string.picked_a));
    }

    /** Right to left locales are plain resources: they must load like the others. */
    @Test
    @Config(sdk = 28, qualifiers = "ar")
    public void arabicStringsLoad() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals("اختيار محرك A (المصدر)", activity.getString(R.string.picked_a));
    }

    /** The in-app choice must win over the phone language, before any resource is read. */
    @Test
    public void chosenLanguageOverridesTheSystemOne() {
        Store.setLanguage(RuntimeEnvironment.getApplication(), "it");
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals("Scegli chiavetta A (sorgente)", activity.getString(R.string.picked_a));
        assertEquals("Lingua", activity.getString(R.string.language));
    }

    /** Starting the service with no drives must not crash: it only reports the setup. */
    @Test
    public void serviceStartsWithoutDrives() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), CopyService.class)
                .setAction(CopyService.ACTION_START);
        ServiceController<CopyService> controller = Robolectric.buildService(CopyService.class, intent);
        CopyService service = controller.create().startCommand(0, 0).get();
        assertNotNull(service);
    }
}

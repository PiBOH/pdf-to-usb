package pdf.to.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Intent;
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

    /** Nessuna rete nei test: il controllo aggiornamenti resta spento per 6 ore. */
    @Before
    public void noUpdateCheck() {
        Store.markUpdateCheck(RuntimeEnvironment.getApplication(), System.currentTimeMillis());
    }

    @Test
    public void screenShowsNotConfiguredState() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        String notSet = activity.getString(R.string.not_set);
        String a = ((TextView) activity.findViewById(R.id.state_a)).getText().toString();
        String b = ((TextView) activity.findViewById(R.id.state_b)).getText().toString();
        assertEquals(activity.getString(R.string.state_a, notSet), a);
        assertEquals(activity.getString(R.string.state_b, notSet), b);
    }

    @Test
    @Config(sdk = 28, qualifiers = "de")
    public void germanStringsLoad() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals("Laufwerk A wählen (Quelle)", activity.getString(R.string.picked_a));
        assertEquals("A (Quelle): x", activity.getString(R.string.state_a, "x"));
    }

    @Test
    @Config(sdk = 28, qualifiers = "ja")
    public void japaneseStringsLoad() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals("A ドライブを選択（コピー元）", activity.getString(R.string.picked_a));
    }

    @Test
    @Config(sdk = 28, qualifiers = "ar")
    public void arabicStringsLoad() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals("اختيار محرك A (المصدر)", activity.getString(R.string.picked_a));
    }

    @Test
    public void serviceStartsWithoutDrives() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), CopyService.class)
                .setAction(CopyService.ACTION_START);
        ServiceController<CopyService> controller = Robolectric.buildService(CopyService.class, intent);
        CopyService service = controller.create().startCommand(0, 0).get();
        assertNotNull(service);
    }
}

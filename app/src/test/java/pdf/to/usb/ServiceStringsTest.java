package pdf.to.usb;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Le stringhe del servizio, formattate, devono uscire nella lingua del telefono. */
@RunWith(RobolectricTestRunner.class)
public class ServiceStringsTest {

    private static Context app() {
        return RuntimeEnvironment.getApplication();
    }

    @Test
    @Config(sdk = 28, qualifiers = "de")
    public void germanServiceStrings() {
        Context c = app();
        assertEquals("Kopiert 2 · zuletzt: a.pdf", c.getString(R.string.copy_progress, 2, "a.pdf"));
        assertEquals("✗ a.pdf: kaputt", c.getString(R.string.copy_error_detail, "a.pdf", "kaputt"));
        assertEquals("Fehler bei a.pdf", c.getString(R.string.copy_error_file, "a.pdf"));
        assertEquals("Laufwerk A nicht verbunden", c.getString(R.string.drive_a_missing));
        assertEquals("B nicht festgelegt", c.getString(R.string.drive_b_missing));
        assertEquals("✓ ACCESS erkannt: Überwachung funktioniert", c.getString(R.string.access_ok));
        assertEquals("Datei nicht lesbar", c.getString(R.string.copy_unreadable));
    }

    @Test
    @Config(sdk = 28, qualifiers = "ja")
    public void japaneseServiceStrings() {
        Context c = app();
        assertEquals("2 件コピー済み · 最新: a.pdf", c.getString(R.string.copy_progress, 2, "a.pdf"));
        assertEquals("✗ a.pdf: 壊れた", c.getString(R.string.copy_error_detail, "a.pdf", "壊れた"));
        assertEquals("A ドライブが接続されていません", c.getString(R.string.drive_a_missing));
        assertEquals("ファイルを読み取れません", c.getString(R.string.copy_unreadable));
    }
}

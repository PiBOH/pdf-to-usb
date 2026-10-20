package pdf.to.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** org.json e' fornito da Android: qui serve Robolectric, ma nessuna rete. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateParseTest {

    private static final String RELEASE = "{"
            + "\"tag_name\":\"v0.2.0\","
            + "\"name\":\"Pdf To Usb 0.2.0\","
            + "\"body\":\"note\","
            + "\"assets\":["
            + "{\"name\":\"checksums.txt\",\"browser_download_url\":\"https://example/checksums.txt\"},"
            + "{\"name\":\"pdf-to-usb-0.2.0.apk\",\"browser_download_url\":\"https://example/app.apk\"}"
            + "]}";

    @Test
    public void parsesTagAndApkAsset() {
        Update.Release release = Update.parse(RELEASE);
        assertEquals("v0.2.0", release.tag);
        assertEquals(200, release.code);
        assertEquals("https://example/app.apk", release.apkUrl);
    }

    @Test
    public void rejectsReleaseWithoutApk() {
        assertNull(Update.parse("{\"tag_name\":\"v0.2.0\",\"assets\":["
                + "{\"name\":\"source.zip\",\"browser_download_url\":\"https://example/src.zip\"}]}"));
        assertNull(Update.parse("{\"tag_name\":\"v0.2.0\"}"));
    }

    @Test
    public void rejectsBrokenJsonAndTags() {
        assertNull(Update.parse("non json"));
        assertNull(Update.parse(""));
        assertNull(Update.parse("{\"tag_name\":\"latest\",\"assets\":[]}"));
        assertNull(Update.parse("{\"tag_name\":\"v0.2.0\",\"assets\":[{\"name\":\"a.apk\",\"browser_download_url\":\"\"}]}"));
    }
}

package pdf.to.usb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Version comparison contract, as a table. */
public class UpdateTest {

    @Test
    public void versionCodeTable() {
        String[][] cases = {
                {"v1.2.3", "10203"},
                {"1.2.3", "10203"},
                {"v1.2", "10200"},
                {"0.1.0", "100"},
                {"v0.2.0", "200"},
                {null, "-1"},
                {"", "-1"},
                {"v1", "-1"},
                {"v1.2.3.4", "-1"},
                {"latest", "-1"},
                {"v1.x.3", "-1"},
                {"v100.2.3", "-1"},
        };
        for (String[] c : cases) {
            assertEquals("tag " + c[0], Integer.parseInt(c[1]), Update.versionCode(c[0]));
        }
    }

    @Test
    public void isNewerTable() {
        Update.Release next = new Update.Release("v0.2.0", 200, "https://example/app.apk");
        // installed, release, expected
        Object[][] cases = {
                {100, next, true},
                {200, next, false},
                {300, next, false},
                {100, null, false},
        };
        for (Object[] c : cases) {
            assertEquals("installed " + c[0], c[2], Update.isNewer((Integer) c[0], (Update.Release) c[1]));
        }
    }
}

package pdf.to.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StoreTest {

    private static Uri tree(String documentId) {
        return DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", documentId);
    }

    @Test
    public void volumeNameComesFromDocumentId() {
        assertEquals("1A2B-3C4D", Store.volumeName(tree("1A2B-3C4D:PDF/miei")));
        assertEquals("primary", Store.volumeName(tree("primary:Download")));
    }

    @Test
    public void treePathMapsPrimaryStorage() {
        File path = Volumes.treePath(tree("primary:Download"));
        assertNotNull(path);
        assertEquals(new File(Environment.getExternalStorageDirectory(), "Download"), path);
    }

    @Test
    public void treePathIgnoresNonExternalProviders() {
        Uri other = DocumentsContract.buildTreeDocumentUri("com.example.provider", "root-1");
        assertEquals(null, Volumes.treePath(other));
    }

    /** The log keeps the newest line first and stops at 30 entries. */
    @Test
    public void logKeepsNewestFirstAndIsCapped() {
        for (int i = 1; i <= 35; i++) Store.log(RuntimeEnvironment.getApplication(), "riga " + i);
        String[] lines = Store.log(RuntimeEnvironment.getApplication());
        assertEquals(30, lines.length);
        org.junit.Assert.assertTrue(lines[0], lines[0].endsWith("riga 35"));
    }
}

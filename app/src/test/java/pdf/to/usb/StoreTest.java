package pdf.to.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
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

    /** Listening is on from the first start: the app has to be ready without opening it. */
    @Test
    public void listeningDefaultsToOn() {
        org.junit.Assert.assertTrue(Store.listening(RuntimeEnvironment.getApplication()));
    }

    /** The volume name is the part of the SAF document id before the colon. */
    @Test
    public void volumeNameComesFromDocumentId() {
        assertEquals("1A2B-3C4D", Store.volumeName(tree("1A2B-3C4D:PDF/miei")));
        assertEquals("primary", Store.volumeName(tree("primary:Download")));
    }

    /** A primary tree must resolve to a real directory, FileObserver needs a File. */
    @Test
    public void treePathMapsPrimaryStorage() {
        File path = Volumes.treePath(tree("primary:Download"));
        assertNotNull(path);
        assertEquals(new File(Environment.getExternalStorageDirectory(), "Download"), path);
    }

    /** Trees from other providers have no filesystem path: null, never a guess. */
    @Test
    public void treePathIgnoresNonExternalProviders() {
        Uri other = DocumentsContract.buildTreeDocumentUri("com.example.provider", "root-1");
        assertEquals(null, Volumes.treePath(other));
    }

    /** The log keeps the newest line first and stops at 30 entries. */
    /** Log entries are stored as keys, so they are re-read in the language in force now. */
    @Test
    @Config(sdk = 28, qualifiers = "de")
    public void logLinesFollowTheCurrentLanguage() {
        Store.log(RuntimeEnvironment.getApplication(), "copy_ok", "a.pdf");
        Store.log(RuntimeEnvironment.getApplication(), "copy_progress", 2, "a.pdf");
        String[] lines = Store.log(RuntimeEnvironment.getApplication());
        org.junit.Assert.assertTrue(lines[0], lines[0].contains("2"));
        org.junit.Assert.assertTrue(lines[0], lines[0].endsWith("a.pdf"));
        org.junit.Assert.assertTrue(lines[1], lines[1].contains("a.pdf"));
    }

    /** A week old line disappears on read, and the next write drops it from storage. */
    @Test
    public void logDropsLinesOlderThanAWeek() {
        Context c = RuntimeEnvironment.getApplication();
        long now = System.currentTimeMillis();
        long week = 7L * 24 * 60 * 60 * 1000;
        String fresh = (now - 60_000) + "\u001e10:01  recente";
        String old = (now - week - 60_000) + "\u001e10:00  vecchia";
        c.getSharedPreferences("pdf-to-usb", Context.MODE_PRIVATE).edit()
                .putString("log", old + "\n" + fresh).commit();

        String[] lines = Store.log(c);
        assertEquals(1, lines.length);
        org.junit.Assert.assertTrue(lines[0], lines[0].endsWith("recente"));

        Store.log(c, "copy_ok", "a.pdf");
        String stored = c.getSharedPreferences("pdf-to-usb", Context.MODE_PRIVATE)
                .getString("log", "");
        org.junit.Assert.assertFalse(stored, stored.contains("vecchia"));
    }

    @Test
    public void logKeepsNewestFirstAndIsCapped() {
        for (int i = 1; i <= 35; i++) Store.logRaw(RuntimeEnvironment.getApplication(), "line " + i);
        String[] lines = Store.log(RuntimeEnvironment.getApplication());
        assertEquals(30, lines.length);
        org.junit.Assert.assertTrue(lines[0], lines[0].endsWith("line 35"));
    }
}

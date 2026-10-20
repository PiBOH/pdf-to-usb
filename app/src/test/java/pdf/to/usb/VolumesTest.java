package pdf.to.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;
import android.provider.DocumentsContract;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Automatic detection: which stick becomes A and which one becomes B.
 *
 * The rules under test are the ones the screen relies on:
 * - A is the volume with the most PDFs (first one on a tie);
 * - B is the only other volume, and is left unset as soon as a third stick is plugged in;
 * - the tree URI of a volume is built from its UUID, so it can be preselected in the picker;
 * - the PDF count stops two levels below the volume root, to stay fast on a big stick.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VolumesTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** A fake removable volume; the directory is never touched by these tests. */
    private static Volumes.Volume vol(String uuid) {
        return new Volumes.Volume("USB " + uuid, new File("/storage/" + uuid), uuid);
    }

    @Test
    public void sourceIsTheStickWithMostPdfs() {
        Volumes.Volume empty = vol("AAAA-0001");
        Volumes.Volume documents = vol("BBBB-0002");
        Volumes.Volume few = vol("CCCC-0003");
        List<Volumes.Volume> found = Arrays.asList(empty, documents, few);
        assertEquals(documents, Volumes.source(found, new int[]{0, 12, 3}));
        // No PDF anywhere: no evidence to prefer one stick, the first one wins.
        assertEquals(empty, Volumes.source(found, new int[]{0, 0, 0}));
    }

    @Test
    public void destinationIsTheOnlyOtherStick() {
        Volumes.Volume a = vol("AAAA-0001");
        Volumes.Volume b = vol("BBBB-0002");
        Volumes.Volume c = vol("CCCC-0003");
        assertEquals(b, Volumes.destination(Arrays.asList(a, b), a));
        assertEquals(a, Volumes.destination(Arrays.asList(a, b), b));
        // Three sticks: guessing would be wrong, so nothing is proposed.
        assertNull(Volumes.destination(Arrays.asList(a, b, c), a));
    }

    /** A single stick must never be proposed as its own destination. */
    /** Every stick except B is watched, so the source is the one the PDF is opened from. */
    @Test
    public void sourcesAreEveryStickExceptTheDestination() {
        Volumes.Volume first = vol("AAAA-0001");
        Volumes.Volume second = vol("BBBB-0002");
        Volumes.Volume destination = vol("CCCC-0003");
        List<Volumes.Volume> all = Arrays.asList(first, second, destination);
        List<Volumes.Volume> watched = Volumes.sources(all, "CCCC-0003");
        assertEquals(Arrays.asList(first, second), watched);
        // Destination not mounted right now: its UUID must still be excluded.
        assertEquals(2, Volumes.sources(Arrays.asList(first, second), "CCCC-0003").size());
        // No destination known: everything is a possible source.
        assertEquals(3, Volumes.sources(all, null).size());
    }

    @Test
    public void destinationIgnoresTheSourceItself() {
        Volumes.Volume a = vol("AAAA-0001");
        assertEquals(a, Volumes.destination(Arrays.asList(a), null));
        assertNull(Volumes.destination(Arrays.asList(a), a));
    }

    /** The tree URI is built from the volume UUID, root document id "UUID:". */
    @Test
    public void treePointsAtTheVolumeRoot() {
        Uri tree = Volumes.tree(vol("1A2B-3C4D"));
        assertEquals("1A2B-3C4D:", DocumentsContract.getTreeDocumentId(tree));
        assertNull(Volumes.tree(new Volumes.Volume("no uuid", new File("/storage/x"), "")));
    }

    /** Deep folders are not scanned: the count only has to rank the sticks. */
    @Test
    public void pdfCountOnlyLooksAtTwoLevels() throws Exception {
        File root = temp.newFolder("stick");
        File sub = new File(root, "documents");
        File deep = new File(sub, "archive");
        File tooDeep = new File(deep, "old");
        tooDeep.mkdirs();
        new File(root, "a.pdf").createNewFile();
        new File(sub, "b.PDF").createNewFile();   // extension is case insensitive
        new File(root, "note.txt").createNewFile(); // not a PDF
        new File(deep, "c.pdf").createNewFile();    // depth 2: counted
        new File(tooDeep, "d.pdf").createNewFile(); // depth 3: not counted
        assertEquals(3, Volumes.pdfCount(root, 0));
    }
}

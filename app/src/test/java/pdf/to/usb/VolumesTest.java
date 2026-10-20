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

/** Rilevamento automatico: quale chiavetta è A, quale è B. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VolumesTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

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
        assertEquals(empty, Volumes.source(found, new int[]{0, 0, 0}));
    }

    @Test
    public void destinationIsTheOnlyOtherStick() {
        Volumes.Volume a = vol("AAAA-0001");
        Volumes.Volume b = vol("BBBB-0002");
        Volumes.Volume c = vol("CCCC-0003");
        assertEquals(b, Volumes.destination(Arrays.asList(a, b), a));
        assertEquals(a, Volumes.destination(Arrays.asList(a, b), b));
        assertNull(Volumes.destination(Arrays.asList(a, b, c), a));
    }

    @Test
    public void destinationIgnoresTheSourceItself() {
        Volumes.Volume a = vol("AAAA-0001");
        assertEquals(a, Volumes.destination(Arrays.asList(a), null));
        assertNull(Volumes.destination(Arrays.asList(a), a));
    }

    @Test
    public void treePointsAtTheVolumeRoot() {
        Uri tree = Volumes.tree(vol("1A2B-3C4D"));
        assertEquals("1A2B-3C4D:", DocumentsContract.getTreeDocumentId(tree));
        assertNull(Volumes.tree(new Volumes.Volume("senza uuid", new File("/storage/x"), "")));
    }

    @Test
    public void pdfCountOnlyLooksAtTwoLevels() throws Exception {
        File root = temp.newFolder("chiavetta");
        File sub = new File(root, "documenti");
        File deep = new File(sub, "archivio");
        File tooDeep = new File(deep, "vecchi");
        tooDeep.mkdirs();
        new File(root, "a.pdf").createNewFile();
        new File(sub, "b.PDF").createNewFile();
        new File(root, "note.txt").createNewFile();
        new File(deep, "c.pdf").createNewFile();
        new File(tooDeep, "d.pdf").createNewFile();
        assertEquals(3, Volumes.pdfCount(root, 0));
    }
}

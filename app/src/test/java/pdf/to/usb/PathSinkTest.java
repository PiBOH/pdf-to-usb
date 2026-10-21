package pdf.to.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

/** Destination B as a plain folder: the fallback for boards without a system folder picker. */
public class PathSinkTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File file, int bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[bytes]);
        }
    }

    /** The pdf-to-usb folder is created on B and the file lands inside it. */
    @Test
    public void createsFolderAndCopies() throws Exception {
        File volume = tmp.newFolder("1A2B-3C4D");
        File src = tmp.newFile("contratto.pdf");
        write(src, 100);

        assertEquals(Copy.Result.COPIED, Copy.run(src, new PathSink(volume)));
        File copied = new File(new File(volume, "pdf-to-usb"), "contratto.pdf");
        assertTrue(copied.isFile());
        assertEquals(100, copied.length());
    }

    /** Same name and same size: no second copy, like the SAF destination. */
    @Test
    public void sameFileIsNotCopiedTwice() throws Exception {
        File volume = tmp.newFolder("1A2B-3C4D");
        File src = tmp.newFile("a.pdf");
        write(src, 50);
        Copy.run(src, new PathSink(volume));

        assertEquals(Copy.Result.PRESENT, Copy.run(src, new PathSink(volume)));
        assertEquals(1, new File(volume, "pdf-to-usb").listFiles().length);
    }

    /** Same name, different size: the engine adds the (2) suffix instead of overwriting. */
    @Test
    public void differentFileGetsASuffix() throws Exception {
        File volume = tmp.newFolder("1A2B-3C4D");
        File first = tmp.newFile("a.pdf");
        write(first, 10);
        Copy.run(first, new PathSink(volume));

        File second = tmp.newFolder("altro").toPath().resolve("a.pdf").toFile();
        write(second, 99);
        assertEquals(Copy.Result.COPIED, Copy.run(second, new PathSink(volume)));
        assertTrue(new File(volume, "pdf-to-usb/a (2).pdf").isFile());
    }
}

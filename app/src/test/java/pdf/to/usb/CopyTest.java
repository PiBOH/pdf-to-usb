package pdf.to.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class CopyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void safeNameBlocksPathsAndAddsExtension() {
        assertEquals("a_b.pdf", Copy.safeName("a/b.pdf"));
        assertEquals("a_b.pdf", Copy.safeName("a\\b"));
        assertEquals("document.pdf", Copy.safeName("  "));
        assertEquals("x.pdf", Copy.safeName("x"));
    }

    @Test
    public void uniqueSkipsSameNameAndSize() {
        Map<String, Long> onB = new HashMap<>();
        assertEquals("a.pdf", Copy.unique("a.pdf", 10, onB));
        onB.put("a.pdf", 10L);
        assertNull(Copy.unique("a.pdf", 10, onB));
        assertEquals("a (2).pdf", Copy.unique("a.pdf", 11, onB));
        onB.put("a (2).pdf", 12L);
        assertEquals("a (3).pdf", Copy.unique("a.pdf", 11, onB));
    }

    @Test
    public void uniqueWithUnknownSizeKeepsName() {
        Map<String, Long> onB = new HashMap<>();
        onB.put("a.pdf", 10L);
        assertNull(Copy.unique("a.pdf", -1, onB));
    }

    @Test
    public void copyWritesThenSkipsExisting() throws IOException {
        File src = tmp.newFile("a.pdf");
        try (FileWriter w = new FileWriter(src)) {
            w.write("hello");
        }
        Sink sink = new Sink();
        assertEquals(Copy.Result.COPIED, Copy.run(src, sink));
        assertEquals("hello", sink.files.get("a.pdf"));
        assertEquals(Copy.Result.PRESENT, Copy.run(src, sink));
        assertEquals(1, sink.files.size());
    }

    @Test
    public void failedCopyLeavesNothingBehind() throws IOException {
        File src = tmp.newFile("b.pdf");
        try (FileWriter w = new FileWriter(src)) {
            w.write("hello");
        }
        Sink sink = new Sink();
        sink.fail = true;
        try {
            Copy.run(src, sink);
            fail("the copy was supposed to fail");
        } catch (IOException expected) {
            assertTrue(sink.files.isEmpty());
        }
    }

    private static final class Sink implements Copy.Sink {
        final Map<String, String> files = new HashMap<>();
        boolean fail;

        @Override
        public Map<String, Long> items() {
            Map<String, Long> out = new HashMap<>();
            for (Map.Entry<String, String> e : files.entrySet()) out.put(e.getKey(), (long) e.getValue().length());
            return out;
        }

        @Override
        public OutputStream create(String name) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    if (fail) throw new IOException("disk full");
                    buffer.write(b);
                }

                @Override
                public void close() {
                    files.put(name, buffer.toString());
                }
            };
        }

        @Override
        public void discard(String name) {
            files.remove(name);
        }
    }
}

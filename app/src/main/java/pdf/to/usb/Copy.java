package pdf.to.usb;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;

/**
 * Copy engine. Pure java.io: the destination is a {@link Sink}, so the logic (dedupe,
 * names, partial files) can be tested without a device.
 */
final class Copy {

    enum Result { COPIED, PRESENT }

    interface Sink {
        /** Files already in the destination folder: name -> size. */
        Map<String, Long> items() throws IOException;

        OutputStream create(String name) throws IOException;

        /** Deletes a half written file left by an error. */
        void discard(String name);
    }

    private Copy() {
    }

    static Result run(File src, Sink sink) throws IOException {
        String name = unique(safeName(src.getName()), src.length(), sink.items());
        if (name == null) return Result.PRESENT;
        InputStream in = new FileInputStream(src);
        try {
            return walk(in, name, sink);
        } catch (IOException e) {
            closeQuietly(in);
            throw e;
        }
    }

    static Result run(InputStream in, String rawName, long size, Sink sink) throws IOException {
        String name = unique(safeName(rawName), size, sink.items());
        if (name == null) return Result.PRESENT;
        return walk(in, name, sink);
    }

    private static Result walk(InputStream in, String name, Sink sink) throws IOException {
        OutputStream out;
        try {
            out = sink.create(name);
        } catch (IOException e) {
            closeQuietly(in);
            throw e;
        }
        boolean complete = false;
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            complete = true;
        } finally {
            closeQuietly(out);
            closeQuietly(in);
            if (!complete) sink.discard(name);
        }
        return Result.COPIED;
    }

    /** Name valid on B: no path separators, .pdf extension. */
    static String safeName(String raw) {
        String name = raw == null ? "" : raw.replace('/', '_').replace('\\', '_').trim();
        if (name.isEmpty()) return "document.pdf";
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) name += ".pdf";
        return name;
    }

    /** null = same name and same size already on B: nothing to do. */
    static String unique(String name, long size, Map<String, Long> existing) {
        Long found = existing.get(name);
        if (found == null) return name;
        // size -1 = destination that cannot report the size: the name alone decides
        if (size < 0 || found == size) return null;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String candidate;
        int i = 2;
        do {
            candidate = base + " (" + i++ + ")" + ext;
        } while (existing.containsKey(candidate));
        return candidate;
    }

    private static void closeQuietly(java.io.Closeable c) {
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }
}

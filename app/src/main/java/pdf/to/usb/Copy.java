package pdf.to.usb;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;

/**
 * Motore di copia. Solo java.io: la destinazione è un {@link Sink}, così la logica
 * (deduplica, nomi, file parziali) si può testare senza device.
 */
final class Copy {

    enum Result { COPIED, PRESENT }

    interface Sink {
        /** File già presenti nella cartella di destinazione: nome -> dimensione. */
        Map<String, Long> items() throws IOException;

        OutputStream create(String name) throws IOException;

        /** Cancella un file rimasto a metà dopo un errore. */
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

    /** Nome valido su B: niente separatori di percorso, estensione .pdf. */
    static String safeName(String raw) {
        String name = raw == null ? "" : raw.replace('/', '_').replace('\\', '_').trim();
        if (name.isEmpty()) return "documento.pdf";
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) name += ".pdf";
        return name;
    }

    /** null = stesso nome e stessa dimensione già su B: niente da fare. */
    static String unique(String name, long size, Map<String, Long> existing) {
        Long found = existing.get(name);
        if (found == null) return name;
        // size -1 = destinazione che non sa dire la dimensione: conta solo il nome
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

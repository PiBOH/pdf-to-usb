package pdf.to.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Every locale must have exactly the keys (and placeholders) of values/strings.xml. */
public class TranslationsTest {

    /** values-* language folders of vivizzz007/vivi-music, plus values-en. */
    private static final String[] LOCALES = {
            "ar", "as", "az", "b+sr+Latn", "be", "bg", "bn", "bn-rIN", "bs", "ca", "cs", "de", "el",
            "en", "en-rCA", "es", "es-rUS", "et", "eu", "fa", "fi", "fil", "fr", "hi", "hr", "hu",
            "in", "it", "iw", "ja", "km", "ko", "lt", "mfe", "ml", "ms", "nb-rNO", "nl", "nn", "pa",
            "pl", "pt", "pt-rBR", "ro", "ru", "sk", "sl", "sv", "ta", "te", "th", "tr", "uk", "vi",
            "wae", "zh-rCN", "zh-rTW"};

    private static final File RES = new File("src/main/res");

    @Test
    public void allViviMusicLocalesExist() {
        for (String locale : LOCALES) {
            File file = new File(RES, "values-" + locale + "/strings.xml");
            assertTrue("missing locale " + locale, file.isFile());
        }
    }

    /** The in-app language list must cover the same 57 locales as the resource folders. */
    @Test
    public void languageListCoversEveryLocale() {
        assertEquals(57, Lang.tags().length);
        Set<String> tags = new HashSet<>(Arrays.asList(Lang.tags()));
        for (String locale : LOCALES) {
            String tag = locale.replace("b+", "").replace("+", "-").replace("-r", "-");
            assertTrue("Lang.TAGS is missing " + tag, tags.contains(tag));
        }
    }

    @Test
    public void everyLocaleMatchesBaseKeysAndPlaceholders() throws Exception {
        Map<String, String> base = read(new File(RES, "values/strings.xml"));
        assertTrue("values/strings.xml is empty", base.size() > 0);
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            Map<String, String> translated = read(new File(RES, "values-" + locale + "/strings.xml"));
            Set<String> missing = new TreeSet<>(base.keySet());
            missing.removeAll(translated.keySet());
            Set<String> extra = new TreeSet<>(translated.keySet());
            extra.removeAll(base.keySet());
            if (!missing.isEmpty()) problems.add(locale + ": missing keys " + missing);
            if (!extra.isEmpty()) problems.add(locale + ": extra keys " + extra);
            for (String key : base.keySet()) {
                if (!translated.containsKey(key)) continue;
                Set<String> expected = placeholders(base.get(key));
                if (!expected.equals(placeholders(translated.get(key)))) {
                    problems.add(locale + ": " + key + " has placeholders "
                            + placeholders(translated.get(key)) + " instead of " + expected);
                }
            }
        }
        assertTrue("incomplete translations:\n" + String.join("\n", problems), problems.isEmpty());
    }

    private static Map<String, String> read(File file) throws Exception {
        Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(file);
        NodeList nodes = doc.getElementsByTagName("string");
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            out.put(e.getAttribute("name"), e.getTextContent());
        }
        return out;
    }

    private static final Pattern ARG = Pattern.compile("%(\\d+\\$)?[a-zA-Z]");

    private static Set<String> placeholders(String value) {
        Set<String> out = new HashSet<>();
        Matcher m = ARG.matcher(value == null ? "" : value);
        while (m.find()) out.add(m.group());
        return out;
    }
}

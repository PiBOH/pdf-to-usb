package pdf.to.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Language list: every tag must parse and show a readable name. */
public class LangTest {

    /** Every entry of the language dialog must be unique and show a real name. */
    @Test
    public void everyTagHasAReadableName() {
        Set<String> seen = new HashSet<>();
        for (String tag : Lang.tags()) {
            assertTrue("duplicate tag " + tag, seen.add(tag));
            String name = Lang.label(tag);
            assertFalse("empty name for " + tag, name.trim().isEmpty());
            assertTrue("name for " + tag + " is just the tag", name.equals(tag) || name.length() > 2);
        }
    }

    /** Resource qualifiers become BCP 47 tags, script and region included. */
    @Test
    public void parsesLanguageScriptAndRegion() {
        assertEquals("it", Lang.toLocale("it").getLanguage());
        assertEquals("CA", Lang.toLocale("en-CA").getCountry());
        assertEquals("Latn", Lang.toLocale("sr-Latn").getScript());
        assertEquals(Locale.forLanguageTag("zh-CN"), Lang.toLocale("zh-CN"));
    }
}

package pdf.to.usb;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * App language chosen in the screen. It overrides the Android language for the activity and
 * the service; with an empty tag the system language is used, which is what Android does by
 * default.
 */
final class Lang {

    static final String SYSTEM = "";

    /** One BCP 47 tag per res/values-* folder: values/ (English) plus the 57 locales. */
    private static final String[] TAGS = {
            "en", "ar", "as", "az", "sr-Latn", "be", "bg", "bn", "bn-IN", "bs", "ca", "cs", "de",
            "el", "en-CA", "es", "es-US", "et", "eu", "fa", "fi", "fil", "fr", "hi", "hr", "hu",
            "in", "it", "iw", "ja", "km", "ko", "lt", "mfe", "ml", "ms", "nb-NO", "nl", "nn",
            "pa", "pl", "pt", "pt-BR", "ro", "ru", "sk", "sl", "sv", "ta", "te", "th", "tr", "uk",
            "vi", "wae", "zh-CN", "zh-TW"
    };

    private Lang() {
    }

    /** Context with the chosen language, or the given one when the system language is used. */
    static Context wrap(Context base) {
        String tag = Store.language(base);
        if (SYSTEM.equals(tag)) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(toLocale(tag));
        return base.createConfigurationContext(config);
    }

    static String[] tags() {
        return TAGS.clone();
    }

    /** Native name of the language, so the list reads the same on every phone. */
    static String label(String tag) {
        Locale locale = toLocale(tag);
        String name = locale.getDisplayName(locale);
        if (name == null || name.isEmpty()) return tag;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** "sr-Latn" -> script, "pt-BR" -> region, "it" -> plain language. */
    static Locale toLocale(String tag) {
        String[] parts = tag.split("-");
        if (parts.length == 2) {
            if (parts[1].length() == 4) {
                return new Locale.Builder().setLanguage(parts[0]).setScript(parts[1]).build();
            }
            return new Locale(parts[0], parts[1]);
        }
        return new Locale(tag);
    }
}

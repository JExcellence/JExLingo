package de.jexcellence.lingo.language;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.config.LanguageSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageResolverTest {

    private static final LanguageCode DE = LanguageCode.of("de");
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageCode FR = LanguageCode.of("fr");
    private static final LanguageSettings LANGUAGES = new LanguageSettings(List.of(DE, EN), EN);

    @Test
    void storedPreferenceWins() {
        assertEquals(DE, LanguageResolver.resolve(DE, Locale.US, LANGUAGES));
    }

    @Test
    void clientLocaleIsUsedWithoutPreference() {
        assertEquals(DE, LanguageResolver.resolve(null, Locale.GERMANY, LANGUAGES));
    }

    @Test
    void unsupportedLocaleFallsBack() {
        assertEquals(EN, LanguageResolver.resolve(null, Locale.FRANCE, LANGUAGES));
    }

    @Test
    void preferenceThatIsNoLongerEnabledIsIgnored() {
        assertEquals(DE, LanguageResolver.resolve(FR, Locale.GERMANY, LANGUAGES));
    }

    @Test
    void unknownLocaleFallsBack() {
        assertEquals(EN, LanguageResolver.resolve(null, null, LANGUAGES));
        assertEquals(EN, LanguageResolver.resolve(null, Locale.ROOT, LANGUAGES));
    }
}

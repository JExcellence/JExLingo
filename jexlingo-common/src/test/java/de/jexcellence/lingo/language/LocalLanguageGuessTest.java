package de.jexcellence.lingo.language;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.config.LanguageSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalLanguageGuessTest {

    private static final LanguageCode DE = LanguageCode.of("de");
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageSettings BOTH = new LanguageSettings(List.of(DE, EN), EN);

    @Test
    void recognisesGermanFunctionWords() {
        assertEquals(Optional.of(DE), LocalLanguageGuess.guess("&#A1F3BE das ist genial", BOTH));
        assertEquals(Optional.of(DE), LocalLanguageGuess.guess("schau mal im discord", BOTH));
    }

    @Test
    void recognisesGermanLetters() {
        assertEquals(Optional.of(DE), LocalLanguageGuess.guess("Hallöchen", BOTH));
    }

    @Test
    void recognisesEnglishFunctionWords() {
        assertEquals(Optional.of(EN), LocalLanguageGuess.guess("this is awesome", BOTH));
        assertEquals(Optional.of(EN), LocalLanguageGuess.guess("does anyone have iron", BOTH));
    }

    @Test
    void staysUnsureWithoutHints() {
        assertEquals(Optional.empty(), LocalLanguageGuess.guess("hay", BOTH));
        assertEquals(Optional.empty(), LocalLanguageGuess.guess("lol xd", BOTH));
    }

    @Test
    void ignoresLanguagesThatAreNotEnabled() {
        LanguageSettings englishOnly = new LanguageSettings(List.of(EN), EN);

        assertEquals(Optional.empty(), LocalLanguageGuess.guess("das ist genial", englishOnly));
    }
}

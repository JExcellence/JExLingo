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
    private static final LanguageCode FR = LanguageCode.of("fr");
    private static final LanguageCode ES = LanguageCode.of("es");
    private static final LanguageCode IT = LanguageCode.of("it");
    private static final LanguageCode NL = LanguageCode.of("nl");
    private static final LanguageCode PT = LanguageCode.of("pt");
    private static final LanguageCode PL = LanguageCode.of("pl");
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
    void wordsSharedByBothLanguagesDoNotDecide() {
        assertEquals(Optional.of(DE), LocalLanguageGuess.guess("bin am spawn", BOTH));
        assertEquals(Optional.of(DE), LocalLanguageGuess.guess("es geht an die base", BOTH));
        assertEquals(Optional.of(EN), LocalLanguageGuess.guess("I am at spawn", BOTH));
        assertEquals(Optional.of(EN), LocalLanguageGuess.guess("what's this", BOTH));
    }

    @Test
    void knowsCommonWordsOfEveryLanguage() {
        assertEquals(true, LocalLanguageGuess.isCommonWord("Und"));
        assertEquals(true, LocalLanguageGuess.isCommonWord("the"));
        assertEquals(false, LocalLanguageGuess.isCommonWord("Spawn"));
    }

    @Test
    void ignoresLanguagesThatAreNotEnabled() {
        LanguageSettings englishOnly = new LanguageSettings(List.of(EN), EN);

        assertEquals(Optional.empty(), LocalLanguageGuess.guess("das ist genial", englishOnly));
    }

    @Test
    void recognisesTheOtherSupportedLanguages() {
        LanguageSettings all = new LanguageSettings(List.of(DE, EN, FR, ES, IT, NL, PT, PL), EN);

        assertEquals(Optional.of(FR), LocalLanguageGuess.guess("salut, quelqu'un veut venir avec moi", all));
        assertEquals(Optional.of(ES), LocalLanguageGuess.guess("hola, alguien tiene hierro?", all));
        assertEquals(Optional.of(IT), LocalLanguageGuess.guess("ciao, qualcuno ha del ferro?", all));
        assertEquals(Optional.of(NL), LocalLanguageGuess.guess("heeft iemand ijzer voor mij", all));
        assertEquals(Optional.of(PT), LocalLanguageGuess.guess("oi, alguém tem ferro?", all));
        assertEquals(Optional.of(PL), LocalLanguageGuess.guess("siema, ktoś ma żelazo?", all));
        assertEquals(Optional.of(DE), LocalLanguageGuess.guess("hat jemand eisen für mich", all));
    }

    @Test
    void sharedWordsOnlyCountWhenOneEnabledLanguageClaimsThem() {
        LanguageSettings spanishEnglish = new LanguageSettings(List.of(ES, EN), EN);
        LanguageSettings spanishPortuguese = new LanguageSettings(List.of(ES, PT), ES);

        assertEquals(Optional.of(ES), LocalLanguageGuess.guess("para que", spanishEnglish));
        assertEquals(Optional.empty(), LocalLanguageGuess.guess("para que", spanishPortuguese));
    }

    @Test
    void specialLettersHelpOnlyTheLanguageThatAloneUsesThem() {
        LanguageSettings frenchGerman = new LanguageSettings(List.of(FR, DE), DE);

        assertEquals(Optional.of(FR), LocalLanguageGuess.guess("garçon", frenchGerman));
        assertEquals(Optional.of(DE), LocalLanguageGuess.guess("schön", frenchGerman));
    }
}

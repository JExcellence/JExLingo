package de.jexcellence.lingo.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenMaskerTest {

    @Test
    void protectsUrlsMentionsChatTokensAndPlaceholders() {
        TokenMasker.MaskedText masked = TokenMasker.mask(
                "look at https://example.com/x @Steve [item] {coins}", MaskRules.EMPTY, List.of());

        assertEquals("look at Q0Q Q1Q Q2Q Q3Q", masked.text());
        assertEquals(List.of("https://example.com/x", "@Steve", "[item]", "{coins}"), masked.restores());
    }

    @Test
    void protectsPlayerNamesAndKeepTermsIgnoringCase() {
        TokenMasker.MaskedText masked = TokenMasker.mask("NOTCH hat eine oneblock Insel",
                MaskRules.keep(List.of("OneBlock")), List.of("Notch"));

        assertEquals("Q0Q hat eine Q1Q Insel", masked.text());
        assertEquals(List.of("NOTCH", "oneblock"), masked.restores());
    }

    @Test
    void namesThatAreOrdinaryWordsStayTranslatableInLowerCase() {
        TokenMasker.MaskedText masked = TokenMasker.mask("can someone help me at the base, Base?",
                MaskRules.EMPTY, List.of("Help", "Base"));

        assertEquals("can someone help me at the base, Q0Q?", masked.text());
    }

    @Test
    void alwaysProtectsEveryNameAndMentionsOnlyAtNames() {
        String line = "ask max or @Steve_42 about the base";
        List<String> names = List.of("Max", "Steve_42");

        assertEquals("ask Q0Q or Q1Q about the base",
                TokenMasker.mask(line, MaskRules.EMPTY, names, NameProtection.ALWAYS).text());
        assertEquals("ask max or Q0Q about the base",
                TokenMasker.mask(line, MaskRules.EMPTY, names, NameProtection.MENTIONS).text());
    }

    @Test
    void typicalUserNamesAreProtectedInAnyCase() {
        TokenMasker.MaskedText masked = TokenMasker.mask("ask steve_42 or darkknight",
                MaskRules.EMPTY, List.of("Steve_42", "DarkKnight"));

        assertEquals("ask Q0Q or Q1Q", masked.text());
        assertTrue(TokenMasker.isPlainWordName("Max"));
        assertFalse(TokenMasker.isPlainWordName("DarkKnight"));
        assertFalse(TokenMasker.isPlainWordName("Steve_42"));
    }

    @Test
    void segmentsSplitTextAroundTokens() {
        List<TokenMasker.Segment> parts = TokenMasker.segments("hi Q0Q, wie gehts Q1Q!");

        assertEquals(List.of("hi ", "Q0Q", ", wie gehts ", "Q1Q", "!"),
                parts.stream().map(TokenMasker.Segment::text).toList());
        assertEquals(List.of(true, false, true, false, false),
                parts.stream().map(TokenMasker.Segment::translatable).toList());
    }

    @Test
    void doesNotMatchTermsInsideOtherWords() {
        TokenMasker.MaskedText masked = TokenMasker.mask("gggg gg", MaskRules.keep(List.of("gg")), List.of());

        assertEquals("gggg Q0Q", masked.text());
    }

    @Test
    void replacesForceTermsWithTheirTranslation() {
        MaskRules rules = new MaskRules(List.of(), Map.of("Insel", "Island"));
        TokenMasker.MaskedText masked = TokenMasker.mask("meine insel ist toll", rules, List.of());

        assertTrue(masked.forced());
        assertEquals(Optional.of("my Island is great"), TokenMasker.unmask("my Q0Q is great", masked));
    }

    @Test
    void unmaskToleratesSpacesAndCaseInsideTokens() {
        TokenMasker.MaskedText masked = TokenMasker.mask("hi @Alex", MaskRules.EMPTY, List.of());

        assertEquals(Optional.of("hallo @Alex"), TokenMasker.unmask("hallo Q 0 Q", masked));
        assertEquals(Optional.of("hallo @Alex"), TokenMasker.unmask("hallo q0q", masked));
    }

    @Test
    void tokensGluedToWordsGetASpaceThatIsRemovedAgain() {
        TokenMasker.MaskedText masked = TokenMasker.mask("&aHallo @Alex!", MaskRules.EMPTY, List.of());

        assertEquals("Q0Q Hallo Q1Q!", masked.text());
        assertEquals(Optional.of("&aHello @Alex!"), TokenMasker.unmask("Q0Q Hello Q1Q!", masked));
    }

    @Test
    void textThatLooksLikeATokenIsProtectedItself() {
        TokenMasker.MaskedText masked = TokenMasker.mask("code Q7Q please", MaskRules.EMPTY, List.of());

        assertEquals("code Q0Q please", masked.text());
        assertEquals(Optional.of("Code Q7Q bitte"), TokenMasker.unmask("Code Q0Q bitte", masked));
    }

    @Test
    void unmaskFailsWhenATokenIsLostDuplicatedOrInvented() {
        TokenMasker.MaskedText masked = TokenMasker.mask("@Al and @Bo", MaskRules.EMPTY, List.of());

        assertTrue(TokenMasker.unmask("Q0Q und", masked).isEmpty());
        assertTrue(TokenMasker.unmask("Q0Q Q0Q Q1Q", masked).isEmpty());
        assertTrue(TokenMasker.unmask("Q0Q Q1Q Q2Q", masked).isEmpty());
    }

    @Test
    void plainTextPassesThrough() {
        TokenMasker.MaskedText masked = TokenMasker.mask("hello there", MaskRules.EMPTY, List.of());

        assertTrue(masked.isPlain());
        assertTrue(masked.hasTranslatableText());
        assertEquals(Optional.of("hallo"), TokenMasker.unmask("hallo", masked));
    }

    @Test
    void textMadeOnlyOfProtectedPartsHasNothingToTranslate() {
        TokenMasker.MaskedText masked = TokenMasker.mask("gg @Alex", MaskRules.keep(List.of("gg")), List.of());

        assertFalse(masked.hasTranslatableText());
    }
}

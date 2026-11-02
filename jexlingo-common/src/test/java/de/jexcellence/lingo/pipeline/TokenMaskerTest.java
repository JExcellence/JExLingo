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

        assertEquals("look at {0} {1} {2} {3}", masked.text());
        assertEquals(List.of("https://example.com/x", "@Steve", "[item]", "{coins}"), masked.restores());
    }

    @Test
    void protectsPlayerNamesAndKeepTermsIgnoringCase() {
        TokenMasker.MaskedText masked = TokenMasker.mask("NOTCH hat eine oneblock Insel",
                MaskRules.keep(List.of("OneBlock")), List.of("Notch"));

        assertEquals("{0} hat eine {1} Insel", masked.text());
        assertEquals(List.of("NOTCH", "oneblock"), masked.restores());
    }

    @Test
    void namesThatAreOrdinaryWordsStayTranslatableInLowerCase() {
        TokenMasker.MaskedText masked = TokenMasker.mask("can someone help me at the base, Base?",
                MaskRules.EMPTY, List.of("Help", "Base"));

        assertEquals("can someone help me at the base, {0}?", masked.text());
    }

    @Test
    void typicalUserNamesAreProtectedInAnyCase() {
        TokenMasker.MaskedText masked = TokenMasker.mask("ask steve_42 or darkknight",
                MaskRules.EMPTY, List.of("Steve_42", "DarkKnight"));

        assertEquals("ask {0} or {1}", masked.text());
        assertTrue(TokenMasker.isPlainWordName("Max"));
        assertFalse(TokenMasker.isPlainWordName("DarkKnight"));
        assertFalse(TokenMasker.isPlainWordName("Steve_42"));
    }

    @Test
    void segmentsSplitTextAroundTokens() {
        List<TokenMasker.Segment> parts = TokenMasker.segments("hi {0}, wie gehts {1}!");

        assertEquals(List.of("hi ", "{0}", ", wie gehts ", "{1}", "!"),
                parts.stream().map(TokenMasker.Segment::text).toList());
        assertEquals(List.of(true, false, true, false, false),
                parts.stream().map(TokenMasker.Segment::translatable).toList());
    }

    @Test
    void doesNotMatchTermsInsideOtherWords() {
        TokenMasker.MaskedText masked = TokenMasker.mask("gggg gg", MaskRules.keep(List.of("gg")), List.of());

        assertEquals("gggg {0}", masked.text());
    }

    @Test
    void replacesForceTermsWithTheirTranslation() {
        MaskRules rules = new MaskRules(List.of(), Map.of("Insel", "Island"));
        TokenMasker.MaskedText masked = TokenMasker.mask("meine insel ist toll", rules, List.of());

        assertTrue(masked.forced());
        assertEquals(Optional.of("my Island is great"), TokenMasker.unmask("my {0} is great", masked));
    }

    @Test
    void unmaskToleratesSpacesInsideTokens() {
        TokenMasker.MaskedText masked = TokenMasker.mask("hi @Alex", MaskRules.EMPTY, List.of());

        assertEquals(Optional.of("hallo @Alex"), TokenMasker.unmask("hallo { 0 }", masked));
    }

    @Test
    void unmaskFailsWhenATokenIsLostDuplicatedOrInvented() {
        TokenMasker.MaskedText masked = TokenMasker.mask("@Al and @Bo", MaskRules.EMPTY, List.of());

        assertTrue(TokenMasker.unmask("{0} und", masked).isEmpty());
        assertTrue(TokenMasker.unmask("{0} {0} {1}", masked).isEmpty());
        assertTrue(TokenMasker.unmask("{0} {1} {2}", masked).isEmpty());
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

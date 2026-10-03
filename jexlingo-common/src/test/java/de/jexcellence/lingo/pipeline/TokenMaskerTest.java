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
        TokenMasker.MaskedText masked = TokenMasker.mask("notch hat eine oneblock Insel",
                MaskRules.keep(List.of("OneBlock")), List.of("Notch"));

        assertEquals("{0} hat eine {1} Insel", masked.text());
        assertEquals(List.of("notch", "oneblock"), masked.restores());
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

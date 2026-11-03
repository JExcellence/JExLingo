package de.jexcellence.lingo.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FormatCodesTest {

    @Test
    void splitsLeadingHexAndLegacyCodes() {
        FormatCodes.Split split = FormatCodes.split("&#A1F3BE&l das ist genial");

        assertEquals("&#A1F3BE&l ", split.prefix());
        assertEquals("das ist genial", split.body());
    }

    @Test
    void splitsLeadingMiniMessageTags() {
        FormatCodes.Split split = FormatCodes.split("<gold><bold>hallo zusammen");

        assertEquals("<gold><bold>", split.prefix());
        assertEquals("hallo zusammen", split.body());
    }

    @Test
    void keepsTextWithoutCodes() {
        FormatCodes.Split split = FormatCodes.split("ich hab dich <3");

        assertFalse(split.hasPrefix());
        assertEquals("ich hab dich <3", split.body());
    }

    @Test
    void stripsEveryCode() {
        assertEquals("cool gemacht ^^", FormatCodes.strip("&#A1F3BE cool &agemacht</green> ^^"));
        assertEquals("rot", FormatCodes.strip("&x&f&f&0&0&0&0rot"));
    }

    @Test
    void masksCodesInsideTheText() {
        TokenMasker.MaskedText masked = TokenMasker.mask("das ist &cwirklich gut", MaskRules.EMPTY, List.of());

        assertEquals("das ist Q0Q wirklich gut", masked.text());
        assertEquals(Optional.of("this is &creally good"),
                TokenMasker.unmask("this is Q0Q really good", masked));
        assertEquals(List.of("&c"), masked.restores());
    }
}

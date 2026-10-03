package de.jexcellence.lingo.glossary;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.pipeline.LanguagePair;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlossarySeedTest {

    private static final LanguageCode DE = LanguageCode.of("de");
    private static final LanguageCode EN = LanguageCode.of("en");

    @Test
    void readsKeepAndForceTermsAndReportsBrokenEntries() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                keep: [OneBlock, gg, ""]
                force:
                  - { term: Insel, replacement: Island, source: de, target: en }
                  - { term: Truhe }
                """);
        List<String> warnings = new ArrayList<>();

        List<GlossaryTerm> terms = GlossarySeed.parse(yaml, warnings::add);

        assertEquals(3, terms.size());
        assertEquals(GlossaryMode.KEEP, terms.getFirst().mode());
        GlossaryTerm insel = terms.get(2);
        assertTrue(insel.forces());
        assertTrue(insel.appliesTo(new LanguagePair(DE, EN)));
        assertFalse(insel.appliesTo(new LanguagePair(EN, DE)));
        assertEquals(1, warnings.size());
    }

    @Test
    void termsWithoutLanguagesApplyEverywhere() {
        GlossaryTerm keep = new GlossaryTerm(1L, "gg", null, GlossaryMode.KEEP, null, null);

        assertTrue(keep.appliesTo(new LanguagePair(DE, EN)));
        assertTrue(keep.appliesTo(new LanguagePair(EN, DE)));
        assertFalse(keep.forces());
    }
}

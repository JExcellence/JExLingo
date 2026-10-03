package de.jexcellence.lingo.learning;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.pipeline.LanguagePair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainingExportServiceTest {

    @Test
    void writesHeaderAndOneEscapedLinePerEntry() {
        LanguagePair pair = new LanguagePair(LanguageCode.of("de"), LanguageCode.of("en"));
        MemoryEntry entry = new MemoryEntry(1L, pair, "Hallo\tdu", "Hello\nyou", MemoryStatus.APPROVED, null, null);

        List<String> lines = TrainingExportService.lines(List.of(entry));

        assertEquals("source_language\ttarget_language\tsource\ttarget", lines.getFirst());
        assertEquals("de\ten\tHallo\\tdu\tHello\\nyou", lines.get(1));
    }

    @Test
    void escapesBackslashesFirst() {
        assertEquals("a\\\\b\\r", TrainingExportService.escape("a\\b\r"));
    }
}

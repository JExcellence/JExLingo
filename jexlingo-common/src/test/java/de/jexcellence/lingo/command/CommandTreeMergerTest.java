package de.jexcellence.lingo.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTreeMergerTest {

    private static final List<String> BUNDLED = List.of(
            "name: lingo",
            "subcommands:",
            "  - name: glossary",
            "    permission: \"jexlingo.admin.glossary\"",
            "    subcommands:",
            "      - name: add",
            "        senders: [player, console]",
            "      - name: import",
            "        description: \"Add new terms\"",
            "        senders: [player, console]",
            "",
            "  - name: terms",
            "    permission: \"jexlingo.review\"");

    @Test
    void addsMissingSubcommandsAndKeepsOwnerEdits() {
        List<String> live = new ArrayList<>(List.of(
                "name: lingo",
                "subcommands:",
                "  - name: glossary",
                "    permission: \"myserver.glossary\"",
                "    subcommands:",
                "      - name: add",
                "        senders: [player]",
                ""));

        List<String> added = CommandTreeMerger.merge(BUNDLED, live);

        assertEquals(List.of("glossary.import", "terms"), added);
        assertTrue(live.contains("    permission: \"myserver.glossary\""));
        assertTrue(live.contains("        senders: [player]"));
        int importLine = live.indexOf("      - name: import");
        assertEquals("        description: \"Add new terms\"", live.get(importLine + 1));
        assertTrue(importLine < live.indexOf("  - name: terms"));
    }

    @Test
    void leavesAnEntryAloneWhenTheOwnerRemovedItsSubcommands() {
        List<String> live = new ArrayList<>(List.of(
                "name: lingo",
                "subcommands:",
                "  - name: glossary",
                "    permission: \"jexlingo.admin.glossary\"",
                "  - name: terms",
                "    permission: \"jexlingo.review\""));

        assertTrue(CommandTreeMerger.merge(BUNDLED, live).isEmpty());
    }
}

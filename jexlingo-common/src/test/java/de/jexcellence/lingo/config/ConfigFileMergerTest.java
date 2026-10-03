package de.jexcellence.lingo.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileMergerTest {

    @Test
    void addsMissingKeysWithCommentsAndKeepsOwnerValues() throws InvalidConfigurationException {
        YamlConfiguration bundled = yaml("""
                config-version: 2
                chat:
                  mode: INLINE
                learning:
                  # Approve without staff.
                  auto-approve-votes: 3
                """);
        YamlConfiguration live = yaml("""
                config-version: 1
                chat:
                  mode: FOLLOW_UP
                """);

        List<String> added = ConfigFileMerger.merge(bundled, live);

        assertEquals(List.of("learning.auto-approve-votes"), added);
        assertEquals("FOLLOW_UP", live.getString("chat.mode"));
        assertEquals(3, live.getInt("learning.auto-approve-votes"));
        assertEquals(List.of("Approve without staff."), live.getComments("learning.auto-approve-votes"));
        assertEquals(2, live.getInt("config-version"));
    }

    @Test
    void completeFileIsLeftAlone() throws InvalidConfigurationException {
        YamlConfiguration bundled = yaml("config-version: 2\nchat:\n  mode: INLINE\n");
        YamlConfiguration live = yaml("config-version: 2\nchat:\n  mode: INLINE\n");

        assertTrue(ConfigFileMerger.merge(bundled, live).isEmpty());
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.loadFromString(text);
        return yaml;
    }
}

package de.jexcellence.lingo.config;

import de.jexcellence.lingo.api.LanguageCode;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LingoConfigLoaderTest {

    @Test
    void emptyFileGivesTheDefaults() throws InvalidConfigurationException {
        LingoConfigLoader.LoadResult result = LingoConfigLoader.load(yaml(""), Map.of());
        LingoConfig config = result.config();

        assertEquals("libretranslate", config.provider().type());
        assertEquals("http://127.0.0.1:5000", config.provider().url().toString());
        assertNull(config.provider().apiKey());
        assertEquals(List.of(LanguageCode.of("de"), LanguageCode.of("en")), config.languages().enabled());
        assertEquals(LanguageCode.of("en"), config.languages().fallback());
        assertEquals(ChatMode.INLINE, config.chat().mode());
        assertEquals(Duration.ofMillis(400), config.chat().inlineWait());
        assertFalse(config.detection().enabled());
        assertTrue(config.bedrock().showOriginalLine());
    }

    @Test
    void environmentWinsOverTheFileForUrlAndKey() throws InvalidConfigurationException {
        YamlConfiguration yaml = yaml("provider:\n  url: http://10.0.0.5:5000\n  api-key: from-file\n");
        Map<String, String> env = Map.of(LingoConfigLoader.ENV_PROVIDER_URL, "https://lt.example.com",
                LingoConfigLoader.ENV_API_KEY, "from-env");

        ProviderSettings provider = LingoConfigLoader.load(yaml, env).config().provider();

        assertEquals("https://lt.example.com", provider.url().toString());
        assertEquals("from-env", provider.apiKey());
        assertFalse(provider.toString().contains("from-env"));
    }

    @Test
    void invalidValuesFallBackWithWarnings() throws InvalidConfigurationException {
        YamlConfiguration yaml = yaml("""
                provider:
                  url: not a url
                  request-timeout-ms: 999999
                languages:
                  enabled: [de, xx1, en, de]
                  fallback: fr
                chat:
                  mode: SOMETIMES
                """);

        LingoConfigLoader.LoadResult result = LingoConfigLoader.load(yaml, Map.of());
        LingoConfig config = result.config();

        assertEquals("http://127.0.0.1:5000", config.provider().url().toString());
        assertEquals(Duration.ofMillis(60_000), config.provider().requestTimeout());
        assertEquals(List.of(LanguageCode.of("de"), LanguageCode.of("en")), config.languages().enabled());
        assertEquals(LanguageCode.of("de"), config.languages().fallback());
        assertEquals(ChatMode.INLINE, config.chat().mode());
        assertEquals(5, result.warnings().size());
    }

    @Test
    void followUpModeAndCustomLanguagesAreRead() throws InvalidConfigurationException {
        YamlConfiguration yaml = yaml("""
                languages:
                  enabled: [en, fr, es]
                  fallback: es
                chat:
                  mode: follow-up
                """);

        LingoConfig config = LingoConfigLoader.load(yaml, Map.of()).config();

        assertEquals(ChatMode.FOLLOW_UP, config.chat().mode());
        assertEquals(LanguageCode.of("es"), config.languages().fallback());
        assertEquals(3, config.languages().enabled().size());
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }
}

package de.jexcellence.lingo.pipeline;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationContext;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import de.jexcellence.lingo.api.provider.TranslationProvider;
import de.jexcellence.lingo.config.CacheSettings;
import de.jexcellence.lingo.provider.CircuitBreaker;
import de.jexcellence.lingo.provider.ProviderGateway;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTextPreparerTest {

    private static final LanguageCode DE = LanguageCode.of("de");
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final SlangDictionary SLANG = new SlangDictionary(Map.of(
            EN, Map.of("u", "you", "idk", "I don't know"),
            DE, Map.of("vllt", "vielleicht", "kp", "keine Ahnung")));

    private final ChatTextPreparer preparer = new ChatTextPreparer(SLANG);

    @Test
    void expandsSlangOfTheSourceLanguageOnly() {
        assertEquals("you there? I don't know", preparer.prepare("u there? idk", EN).text());
        assertEquals("u there?", preparer.prepare("u there?", DE).text());
        assertEquals("Vielleicht morgen, keine Ahnung", preparer.prepare("Vllt morgen, kp", DE).text());
    }

    @Test
    void doesNotExpandInsideWordsOrTokens() {
        assertEquals("run {0} umbrella", preparer.prepare("run {0} umbrella", EN).text());
    }

    @Test
    void shortensLetterSpam() {
        assertEquals("helloo friend", preparer.prepare("hellooooo friend", EN).text());
    }

    @Test
    void lowersShoutingAndRaisesTheAnswer() {
        ChatTextPreparer.Prepared prepared = preparer.prepare("HILF MIR {0}", DE);

        assertTrue(prepared.shouting());
        assertEquals("hilf mir {0}", prepared.text());
        assertEquals("HELP ME {0}", preparer.finish("help me {0}", prepared));
    }

    @Test
    void shortCapsAreNoShouting() {
        assertFalse(preparer.prepare("OK gg", EN).shouting());
        assertFalse(preparer.prepare("GG", EN).shouting());
    }

    @Test
    void readsSlangFileAndSkipsUnknownSections() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("en:\n  u: you\n  ur: your\nnot a language:\n  x: y\n");
        List<String> warnings = new ArrayList<>();

        SlangDictionary dictionary = SlangDictionary.parse(yaml, warnings::add);

        assertEquals(2, dictionary.size());
        assertEquals(1, warnings.size());
    }

    @Test
    void pipelineSendsThePreparedTextAndKeepsNamesIntact() {
        AtomicReference<String> sent = new AtomicReference<>();
        TranslationProvider provider = new EchoProvider(sent);
        ProviderGateway gateway = new ProviderGateway(provider, new CircuitBreaker(5, Duration.ofSeconds(5)), 4,
                Duration.ofSeconds(2));
        PipelineLayers layers = new PipelineLayers(TranslationLookup.NONE, TranslationLookup.NONE,
                pair -> MaskRules.EMPTY, ProviderResultListener.NONE, preparer);
        TranslationPipeline pipeline = new TranslationPipeline(gateway,
                new TranslationCache(new CacheSettings(10, Duration.ofMinutes(1))), layers, new SkipRules(2, 256, "!"));

        TranslationResult result = pipeline.translate(
                new TranslationRequest("KP WO NOTCH IST", null, EN, TranslationContext.CHAT), DE,
                new TranslateOptions(true, List.of("Notch"))).join();

        assertEquals("keine Ahnung wo {0} ist", sent.get());
        assertEquals("KEINE AHNUNG WO NOTCH IST", result.text());
    }

    private record EchoProvider(AtomicReference<String> sent) implements TranslationProvider {

        @Override
        public @NotNull String id() {
            return "echo";
        }

        @Override
        public @NotNull CompletableFuture<String> translate(@NotNull String text, @NotNull LanguageCode source,
                                                            @NotNull LanguageCode target) {
            sent.set(text);
            return CompletableFuture.completedFuture(text);
        }

        @Override
        public @NotNull CompletableFuture<Set<LanguageCode>> languages() {
            return CompletableFuture.completedFuture(Set.of());
        }

        @Override
        public @NotNull CompletableFuture<Optional<DetectedLanguage>> detect(@NotNull String text) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}

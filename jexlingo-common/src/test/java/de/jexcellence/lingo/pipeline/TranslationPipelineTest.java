package de.jexcellence.lingo.pipeline;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationContext;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import de.jexcellence.lingo.api.provider.TranslationProvider;
import de.jexcellence.lingo.config.CacheSettings;
import de.jexcellence.lingo.provider.CircuitBreaker;
import de.jexcellence.lingo.provider.ProviderGateway;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TranslationPipelineTest {

    private static final LanguageCode DE = LanguageCode.of("de");
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final SkipRules RULES = new SkipRules(2, 256, "!");

    private final FakeProvider provider = new FakeProvider();

    @Test
    void sameLanguageIsNotTranslated() {
        TranslationResult result = pipeline(PipelineLayers.glossaryOnly(pair -> MaskRules.EMPTY))
                .translate(chat("Hallo Welt", DE), DE, TranslateOptions.DEFAULT).join();

        assertEquals(TranslationOrigin.SAME_LANGUAGE, result.origin());
        assertEquals(0, provider.calls.get());
    }

    @Test
    void skipPrefixAndNumbersAreLeftAlone() {
        TranslationPipeline pipeline = pipeline(PipelineLayers.glossaryOnly(pair -> MaskRules.EMPTY));

        assertEquals(TranslationOrigin.SKIPPED,
                pipeline.translate(chat("!nicht übersetzen", EN), DE, TranslateOptions.DEFAULT).join().origin());
        assertEquals(TranslationOrigin.SKIPPED,
                pipeline.translate(chat("123 456", EN), DE, TranslateOptions.DEFAULT).join().origin());
    }

    @Test
    void providerResultIsCachedForTheNextLine() {
        TranslationPipeline pipeline = pipeline(PipelineLayers.glossaryOnly(pair -> MaskRules.EMPTY));

        TranslationResult first = pipeline.translate(chat("Wie geht es", EN), DE, TranslateOptions.DEFAULT).join();
        TranslationResult second = pipeline.translate(chat("wie  geht es", EN), DE, TranslateOptions.DEFAULT).join();

        assertEquals(TranslationOrigin.PROVIDER, first.origin());
        assertEquals("[en] Wie geht es", first.text());
        assertEquals(TranslationOrigin.CACHE, second.origin());
        assertEquals(1, provider.calls.get());
    }

    @Test
    void memoryWinsOverEverythingElse() {
        TranslationLookup memory = (pair, key) -> "wie geht es".equals(key)
                ? Optional.of("How are you") : Optional.empty();
        TranslationPipeline pipeline = pipeline(new PipelineLayers(memory, TranslationLookup.NONE,
                pair -> MaskRules.EMPTY, ProviderResultListener.NONE, ChatTextPreparer.withoutSlang()));

        TranslationResult result = pipeline.translate(chat("Wie geht es", EN), DE, TranslateOptions.DEFAULT).join();

        assertEquals(TranslationOrigin.MEMORY, result.origin());
        assertEquals("How are you", result.text());
        assertEquals(0, provider.calls.get());
    }

    @Test
    void glossaryOnlyLinesNeverReachTheProvider() {
        Function<LanguagePair, MaskRules> glossary = pair -> new MaskRules(List.of("gg"), Map.of("insel", "Island"));
        TranslationPipeline pipeline = pipeline(PipelineLayers.glossaryOnly(glossary));

        TranslationResult result = pipeline.translate(chat("gg Insel", EN), DE, TranslateOptions.DEFAULT).join();

        assertEquals(TranslationOrigin.GLOSSARY, result.origin());
        assertEquals("gg Island", result.text());
        assertEquals(0, provider.calls.get());
    }

    @Test
    void maskedPartsSurviveTheProvider() {
        TranslationPipeline pipeline = pipeline(PipelineLayers.glossaryOnly(pair -> MaskRules.keep(List.of("OneBlock"))));

        TranslationResult result = pipeline.translate(chat("Meine OneBlock Insel", EN), DE,
                new TranslateOptions(true, List.of())).join();

        assertEquals("[en] Meine OneBlock Insel", result.text());
        assertEquals("Meine Q0Q Insel", provider.lastText);
    }

    @Test
    void providerFailureFallsBackToTheOriginal() {
        provider.fail = true;
        TranslationPipeline pipeline = pipeline(PipelineLayers.glossaryOnly(pair -> MaskRules.EMPTY));

        TranslationResult result = pipeline.translate(chat("Hallo zusammen", EN), DE, TranslateOptions.DEFAULT)
                .join();

        assertEquals(TranslationOrigin.FALLBACK, result.origin());
        assertEquals("Hallo zusammen", result.text());
        assertFalse(result.translated());
    }

    @Test
    void cooldownBlocksOnlyTheProvider() {
        TranslationPipeline pipeline = pipeline(PipelineLayers.glossaryOnly(pair -> MaskRules.EMPTY));

        TranslationResult result = pipeline.translate(chat("Hallo zusammen", EN), DE,
                new TranslateOptions(false, List.of())).join();

        assertEquals(TranslationOrigin.FALLBACK, result.origin());
        assertEquals(0, provider.calls.get());
    }

    @Test
    void resultsAreCountedByOrigin() {
        TranslationPipeline pipeline = pipeline(PipelineLayers.glossaryOnly(pair -> MaskRules.EMPTY));
        pipeline.translate(chat("Hallo zusammen", EN), DE, TranslateOptions.DEFAULT).join();
        pipeline.translate(chat("Hallo zusammen", DE), DE, TranslateOptions.DEFAULT).join();

        assertEquals(1L, pipeline.stats().snapshot().get(TranslationOrigin.PROVIDER));
        assertEquals(1L, pipeline.stats().snapshot().get(TranslationOrigin.SAME_LANGUAGE));
    }

    @Test
    void shoutedAndNormalLinesDoNotShareACacheEntry() {
        TranslationPipeline pipeline = pipeline(PipelineLayers.glossaryOnly(pair -> MaskRules.EMPTY));

        TranslationResult loud = pipeline.translate(chat("HILF MIR BITTE", EN), DE, TranslateOptions.DEFAULT).join();
        TranslationResult quiet = pipeline.translate(chat("hilf mir bitte", EN), DE, TranslateOptions.DEFAULT).join();
        TranslationResult loudAgain = pipeline.translate(chat("HILF MIR BITTE", EN), DE, TranslateOptions.DEFAULT)
                .join();

        assertEquals("[EN] HILF MIR BITTE", loud.text());
        assertEquals("[en] hilf mir bitte", quiet.text());
        assertEquals(TranslationOrigin.PROVIDER, quiet.origin());
        assertEquals(TranslationOrigin.CACHE, loudAgain.origin());
        assertEquals("[EN] HILF MIR BITTE", loudAgain.text());
    }

    @Test
    void shoutedLinesDoNotTeachPhrases() {
        List<String> learned = new ArrayList<>();
        TranslationPipeline pipeline = pipeline(new PipelineLayers(TranslationLookup.NONE, TranslationLookup.NONE,
                pair -> MaskRules.EMPTY, (pair, key, translation) -> learned.add(translation),
                ChatTextPreparer.withoutSlang()));

        pipeline.translate(chat("HALLO ZUSAMMEN", EN), DE, TranslateOptions.DEFAULT).join();
        pipeline.translate(chat("hallo zusammen", EN), DE, TranslateOptions.DEFAULT).join();

        assertEquals(List.of("[en] hallo zusammen"), learned);
    }

    @Test
    void brokenTokensAreRecoveredPieceByPiece() {
        provider.dropTokens = true;
        TranslationPipeline pipeline = pipeline(PipelineLayers.glossaryOnly(pair -> MaskRules.EMPTY));

        TranslationResult result = pipeline.translate(chat("hallo @Alex wie gehts", EN), DE,
                TranslateOptions.DEFAULT).join();

        assertEquals(TranslationOrigin.PROVIDER, result.origin());
        assertEquals("[en] hallo @Alex [en] wie gehts", result.text());
        assertEquals(1L, pipeline.stats().tokenLosses());
        assertEquals(1L, pipeline.stats().recoveries());
    }

    private TranslationPipeline pipeline(PipelineLayers layers) {
        ProviderGateway gateway = new ProviderGateway(provider, new CircuitBreaker(5, Duration.ofSeconds(30)), 4,
                Duration.ofSeconds(2));
        return new TranslationPipeline(gateway, new TranslationCache(new CacheSettings(100, Duration.ofMinutes(5))),
                layers, RULES);
    }

    private static TranslationRequest chat(String text, LanguageCode target) {
        return new TranslationRequest(text, null, target, TranslationContext.CHAT);
    }

    private static final class FakeProvider implements TranslationProvider {

        private final AtomicInteger calls = new AtomicInteger();
        private volatile boolean fail;
        private volatile boolean dropTokens;
        private volatile String lastText;

        @Override
        public @NotNull String id() {
            return "fake";
        }

        @Override
        public @NotNull CompletableFuture<String> translate(@NotNull String text, @NotNull LanguageCode source,
                                                            @NotNull LanguageCode target) {
            calls.incrementAndGet();
            lastText = text;
            if (fail) {
                return CompletableFuture.failedFuture(new IllegalStateException("down"));
            }
            String answer = dropTokens ? text.replaceAll("Q\\d+Q", "") : text;
            return CompletableFuture.completedFuture("[" + target.code() + "] " + answer);
        }

        @Override
        public @NotNull CompletableFuture<Set<LanguageCode>> languages() {
            return CompletableFuture.completedFuture(Set.of(DE, EN));
        }

        @Override
        public @NotNull CompletableFuture<Optional<DetectedLanguage>> detect(@NotNull String text) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}

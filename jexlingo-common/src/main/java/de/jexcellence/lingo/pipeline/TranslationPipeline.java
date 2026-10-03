package de.jexcellence.lingo.pipeline;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.provider.ProviderGateway;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Turns one text into one target language. The layers run in a fixed order and the first hit wins:
 *
 * <ol>
 *     <li>same language or skip rule: text unchanged</li>
 *     <li>translation memory (approved corrections)</li>
 *     <li>pinned phrases</li>
 *     <li>cache of earlier provider results</li>
 *     <li>glossary and masking; a text made only of protected parts never reaches the provider</li>
 *     <li>provider, shared by identical requests running at the same time</li>
 * </ol>
 *
 * The returned future never fails: any problem ends in {@link TranslationOrigin#FALLBACK} with the original text.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class TranslationPipeline {

    private final ProviderGateway gateway;
    private final TranslationCache cache;
    private final PipelineLayers layers;
    private final AtomicReference<SkipRules> skipRules;
    private final InFlightRegistry<String> inFlight = new InFlightRegistry<>();
    private final PipelineStats stats = new PipelineStats();

    /**
     * Creates the pipeline.
     *
     * @param gateway   the provider gateway
     * @param cache     the result cache
     * @param layers    memory, phrases, glossary and result listener
     * @param skipRules the skip rules
     */
    public TranslationPipeline(@NotNull ProviderGateway gateway, @NotNull TranslationCache cache,
                               @NotNull PipelineLayers layers, @NotNull SkipRules skipRules) {
        this.gateway = gateway;
        this.cache = cache;
        this.layers = layers;
        this.skipRules = new AtomicReference<>(skipRules);
    }

    /**
     * Translates a text.
     *
     * @param request what to translate; its source is ignored in favour of {@code source}
     * @param source  the resolved source language
     * @param options per-call options
     * @return the result; never completes exceptionally
     */
    public @NotNull CompletableFuture<TranslationResult> translate(@NotNull TranslationRequest request,
                                                                   @NotNull LanguageCode source,
                                                                   @NotNull TranslateOptions options) {
        long started = System.nanoTime();
        String text = request.text();
        LanguagePair pair = new LanguagePair(source, request.target());
        if (source.equals(request.target())) {
            return done(TranslationResult.unchanged(text, source, request.target(), TranslationOrigin.SAME_LANGUAGE));
        }
        if (skipRules.get().skips(text, request.context())) {
            return done(TranslationResult.unchanged(text, source, request.target(), TranslationOrigin.SKIPPED));
        }
        String key = TextNormalizer.key(text);
        Optional<TranslationResult> stored = storedResult(text, pair, key, started);
        if (stored.isPresent()) {
            return done(stored.get());
        }
        TokenMasker.MaskedText masked = TokenMasker.mask(text, layers.glossary().apply(pair),
                options.protectedNames());
        if (!masked.hasTranslatableText()) {
            return done(glossaryOnly(text, pair, masked, started));
        }
        if (!options.providerAllowed()) {
            return done(TranslationResult.unchanged(text, source, request.target(), TranslationOrigin.FALLBACK));
        }
        return fromProvider(text, pair, key, masked, started);
    }

    /**
     * Replaces the skip rules after a reload.
     *
     * @param rules the new rules
     */
    public void setSkipRules(@NotNull SkipRules rules) {
        skipRules.set(rules);
    }

    /**
     * Returns result counters by origin.
     *
     * @return result counters by origin
     */
    public @NotNull PipelineStats stats() {
        return stats;
    }

    /**
     * Returns the result cache.
     *
     * @return the result cache
     */
    public @NotNull TranslationCache cache() {
        return cache;
    }

    /**
     * Returns provider calls currently running.
     *
     * @return provider calls currently running
     */
    public int inFlight() {
        return inFlight.size();
    }

    private @NotNull Optional<TranslationResult> storedResult(@NotNull String text, @NotNull LanguagePair pair,
                                                              @NotNull String key, long started) {
        Optional<String> memory = layers.memory().find(pair, key);
        if (memory.isPresent()) {
            return Optional.of(result(memory.get(), text, pair, TranslationOrigin.MEMORY, started));
        }
        Optional<String> phrase = layers.phrases().find(pair, key);
        if (phrase.isPresent()) {
            return Optional.of(result(phrase.get(), text, pair, TranslationOrigin.PHRASE, started));
        }
        Optional<String> cached = cache.get(pair, key);
        if (cached.isPresent()) {
            layers.listener().onResult(pair, key, cached.get());
            return Optional.of(result(cached.get(), text, pair, TranslationOrigin.CACHE, started));
        }
        return Optional.empty();
    }

    private @NotNull TranslationResult glossaryOnly(@NotNull String text, @NotNull LanguagePair pair,
                                                    @NotNull TokenMasker.MaskedText masked, long started) {
        String restored = TokenMasker.unmask(masked.text(), masked).orElse(text);
        TranslationOrigin origin = masked.forced() ? TranslationOrigin.GLOSSARY : TranslationOrigin.SKIPPED;
        return result(restored, text, pair, origin, started);
    }

    private @NotNull CompletableFuture<TranslationResult> fromProvider(@NotNull String text,
                                                                       @NotNull LanguagePair pair,
                                                                       @NotNull String key,
                                                                       @NotNull TokenMasker.MaskedText masked,
                                                                       long started) {
        String flightKey = pair.key() + '\u0000' + masked.text();
        return inFlight.join(flightKey, () -> gateway.translate(masked.text(), pair.source(), pair.target()))
                .thenApply(translated -> {
                    Optional<String> restored = TokenMasker.unmask(translated, masked);
                    if (restored.isEmpty() || restored.get().isBlank()) {
                        return result(text, text, pair, TranslationOrigin.FALLBACK, started);
                    }
                    cache.put(pair, key, restored.get());
                    layers.listener().onResult(pair, key, restored.get());
                    return result(restored.get(), text, pair, TranslationOrigin.PROVIDER, started);
                })
                .exceptionally(error -> result(text, text, pair, TranslationOrigin.FALLBACK, started))
                .thenApply(this::count);
    }

    private @NotNull CompletableFuture<TranslationResult> done(@NotNull TranslationResult result) {
        return CompletableFuture.completedFuture(count(result));
    }

    private @NotNull TranslationResult count(@NotNull TranslationResult result) {
        stats.record(result.origin());
        return result;
    }

    private static @NotNull TranslationResult result(@NotNull String text, @NotNull String original,
                                                     @NotNull LanguagePair pair, @NotNull TranslationOrigin origin,
                                                     long startedNanos) {
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        return new TranslationResult(text, original, pair.source(), pair.target(), origin, millis);
    }
}

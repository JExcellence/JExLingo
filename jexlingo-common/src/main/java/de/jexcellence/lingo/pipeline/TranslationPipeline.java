package de.jexcellence.lingo.pipeline;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.provider.ProviderGateway;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Turns one text into one target language. The layers run in a fixed order and the first hit wins:
 *
 * <ol>
 *     <li>same language or skip rule: text unchanged</li>
 *     <li>translation memory (approved corrections)</li>
 *     <li>pinned phrases</li>
 *     <li>glossary and masking; a text made only of protected parts never reaches the provider</li>
 *     <li>text preparation: letter spam, shouting and chat slang ({@link ChatTextPreparer})</li>
 *     <li>cache of earlier provider results; shouted lines have their own entries</li>
 *     <li>provider, shared by identical requests running at the same time; when the provider breaks a token,
 *     the text between the tokens is translated piece by piece</li>
 * </ol>
 *
 * The returned future never fails: any problem ends in {@link TranslationOrigin#FALLBACK} with the original text.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class TranslationPipeline {

    private static final String SHOUTED = "\u0000shouted";
    private static final int MAX_RECOVERY_PIECES = 4;

    private final ProviderGateway gateway;
    private final TranslationCache cache;
    private final PipelineLayers layers;
    private final AtomicReference<SkipRules> skipRules;
    private final AtomicReference<NameProtection> nameProtection = new AtomicReference<>(NameProtection.SMART);
    private final InFlightRegistry<String> inFlight = new InFlightRegistry<>();
    private final PipelineStats stats = new PipelineStats();
    private final AtomicReference<Consumer<TranslationResult>> resultSink = new AtomicReference<>();
    private final AtomicReference<PipelineQualitySink> qualitySink = new AtomicReference<>();

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
        FormatCodes.Split split = FormatCodes.split(request.text());
        if (split.hasPrefix() && !split.body().isBlank()) {
            TranslationRequest body = new TranslationRequest(split.body(), request.source(), request.target(),
                    request.context());
            return translate(body, source, options).thenApply(result -> new TranslationResult(
                    split.prefix() + result.text(), request.text(), result.source(), result.target(),
                    result.origin(), result.latencyMillis()));
        }
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
                options.protectedNames(), nameProtection.get());
        if (!masked.hasTranslatableText()) {
            return done(glossaryOnly(text, pair, masked, started));
        }
        ChatTextPreparer.Prepared prepared = layers.preparer().prepare(masked.text(), pair.source());
        String cacheKey = prepared.shouting() ? key + SHOUTED : key;
        Optional<String> cached = cache.get(pair, cacheKey);
        if (cached.isPresent()) {
            remember(pair, key, cached.get(), prepared);
            return done(result(cached.get(), text, pair, TranslationOrigin.CACHE, started));
        }
        if (!options.mayCallProvider()) {
            return done(TranslationResult.unchanged(text, source, request.target(), TranslationOrigin.FALLBACK));
        }
        return fromProvider(new Job(text, pair, key, cacheKey, masked, prepared, started));
    }

    /**
     * Sets a receiver for every result, for example the daily statistics.
     *
     * @param sink the receiver, or {@code null} to remove it
     */
    public void setResultSink(@Nullable Consumer<TranslationResult> sink) {
        resultSink.set(sink);
    }

    /**
     * Sets a receiver for broken tokens and saved lines per language pair, for example the daily statistics.
     *
     * @param sink the receiver, or {@code null} to remove it
     */
    public void setQualitySink(@Nullable PipelineQualitySink sink) {
        qualitySink.set(sink);
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
     * Sets which online player names are kept out of the translator ({@code chat.protect-names}).
     *
     * @param protection the rule
     */
    public void setNameProtection(@NotNull NameProtection protection) {
        nameProtection.set(protection);
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
     * Returns the text preparer (slang, shouting, letter spam).
     *
     * @return the text preparer
     */
    public @NotNull ChatTextPreparer preparer() {
        return layers.preparer();
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
        boolean shouting = ChatTextPreparer.isShouting(TokenMasker.withoutTokens(text));
        Optional<String> memory = layers.memory().find(pair, key).map(found -> styled(found, shouting));
        if (memory.isPresent()) {
            return Optional.of(result(memory.get(), text, pair, TranslationOrigin.MEMORY, started));
        }
        Optional<String> phrase = layers.phrases().find(pair, key).map(found -> styled(found, shouting));
        return phrase.map(found -> result(found, text, pair, TranslationOrigin.PHRASE, started));
    }

    /** Corrections and pins are stored in normal case; a shouted line gets them in capitals like a fresh answer. */
    private static @NotNull String styled(@NotNull String stored, boolean shouting) {
        return shouting ? stored.toUpperCase(Locale.ROOT) : stored;
    }

    /** Only lines in normal case teach phrases, so a shouted answer is never pinned in capitals. */
    private void remember(@NotNull LanguagePair pair, @NotNull String key, @NotNull String translation,
                          @NotNull ChatTextPreparer.Prepared prepared) {
        if (!prepared.shouting()) {
            layers.listener().onResult(pair, key, translation);
        }
    }

    private @NotNull TranslationResult glossaryOnly(@NotNull String text, @NotNull LanguagePair pair,
                                                    @NotNull TokenMasker.MaskedText masked, long started) {
        String restored = TokenMasker.unmask(masked.text(), masked).orElse(text);
        TranslationOrigin origin = masked.forced() ? TranslationOrigin.GLOSSARY : TranslationOrigin.SKIPPED;
        return result(restored, text, pair, origin, started);
    }

    private @NotNull CompletableFuture<TranslationResult> fromProvider(@NotNull Job job) {
        LanguagePair pair = job.pair();
        String sent = job.prepared().text();
        String flightKey = pair.key() + '\u0000' + sent;
        return inFlight.join(flightKey, () -> gateway.translate(sent, pair.source(), pair.target()))
                .thenCompose(translated -> {
                    Optional<String> restored = restore(translated, job);
                    if (restored.isPresent() || job.masked().isPlain()) {
                        return CompletableFuture.completedFuture(restored);
                    }
                    stats.recordTokenLoss();
                    PipelineQualitySink sink = qualitySink.get();
                    if (sink != null) {
                        sink.tokenLoss(pair);
                    }
                    return recover(job);
                })
                .thenApply(restored -> restored.map(text -> stored(job, text)).orElseGet(() -> fallback(job)))
                .exceptionally(error -> fallback(job))
                .thenApply(this::count);
    }

    private @NotNull Optional<String> restore(@NotNull String translated, @NotNull Job job) {
        String finished = layers.preparer().finish(translated, job.prepared(), job.pair().target());
        return TokenMasker.unmask(finished, job.masked()).filter(text -> !text.isBlank());
    }

    /**
     * Second try after the translator broke a token: the text between the tokens is translated piece by piece,
     * so every token stays exactly where it was. Lines with many pieces are left untranslated instead.
     */
    private @NotNull CompletableFuture<Optional<String>> recover(@NotNull Job job) {
        List<TokenMasker.Segment> parts = TokenMasker.segments(job.prepared().text());
        long translatable = parts.stream().filter(TokenMasker.Segment::translatable).count();
        if (translatable == 0L || translatable > MAX_RECOVERY_PIECES) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        List<CompletableFuture<String>> pieces = parts.stream()
                .map(part -> part.translatable()
                        ? translatePiece(part.text(), job.pair())
                        : CompletableFuture.completedFuture(part.text()))
                .toList();
        return CompletableFuture.allOf(pieces.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            String joined = pieces.stream().map(CompletableFuture::join).collect(Collectors.joining());
            Optional<String> restored = restore(joined, job);
            restored.ifPresent(text -> {
                stats.recordRecovery();
                PipelineQualitySink sink = qualitySink.get();
                if (sink != null) {
                    sink.recovery(job.pair());
                }
            });
            return restored;
        });
    }

    private @NotNull CompletableFuture<String> translatePiece(@NotNull String piece, @NotNull LanguagePair pair) {
        String core = piece.strip();
        int start = piece.indexOf(core);
        String leading = piece.substring(0, start);
        String trailing = piece.substring(start + core.length());
        return gateway.translate(core, pair.source(), pair.target())
                .thenApply(translated -> leading + translated.strip() + trailing);
    }

    private @NotNull TranslationResult stored(@NotNull Job job, @NotNull String translation) {
        cache.put(job.pair(), job.cacheKey(), translation);
        remember(job.pair(), job.key(), translation, job.prepared());
        return result(translation, job.text(), job.pair(), TranslationOrigin.PROVIDER, job.started());
    }

    private static @NotNull TranslationResult fallback(@NotNull Job job) {
        return result(job.text(), job.text(), job.pair(), TranslationOrigin.FALLBACK, job.started());
    }

    private @NotNull CompletableFuture<TranslationResult> done(@NotNull TranslationResult result) {
        return CompletableFuture.completedFuture(count(result));
    }

    private @NotNull TranslationResult count(@NotNull TranslationResult result) {
        stats.record(result.origin());
        Consumer<TranslationResult> sink = resultSink.get();
        if (sink != null) {
            sink.accept(result);
        }
        return result;
    }

    /**
     * One line on its way to the provider.
     *
     * @param text     the original line
     * @param pair     the language pair
     * @param key      the normalized line, for memory and phrases
     * @param cacheKey the cache key; shouted lines have their own entry
     * @param masked   the masked line
     * @param prepared what goes to the provider
     * @param started  start time in nanoseconds
     */
    private record Job(@NotNull String text, @NotNull LanguagePair pair, @NotNull String key,
                       @NotNull String cacheKey, @NotNull TokenMasker.MaskedText masked,
                       @NotNull ChatTextPreparer.Prepared prepared, long started) {
    }

    private static @NotNull TranslationResult result(@NotNull String text, @NotNull String original,
                                                     @NotNull LanguagePair pair, @NotNull TranslationOrigin origin,
                                                     long startedNanos) {
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        return new TranslationResult(text, original, pair.source(), pair.target(), origin, millis);
    }
}

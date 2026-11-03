package de.jexcellence.lingo.provider;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationContext;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.config.BreakerSettings;
import de.jexcellence.lingo.config.CacheSettings;
import de.jexcellence.lingo.config.ProviderSettings;
import de.jexcellence.lingo.pipeline.ChatTextPreparer;
import de.jexcellence.lingo.pipeline.MaskRules;
import de.jexcellence.lingo.pipeline.PipelineLayers;
import de.jexcellence.lingo.pipeline.ProviderResultListener;
import de.jexcellence.lingo.pipeline.SkipRules;
import de.jexcellence.lingo.pipeline.TranslateOptions;
import de.jexcellence.lingo.pipeline.TranslationCache;
import de.jexcellence.lingo.pipeline.TranslationLookup;
import de.jexcellence.lingo.pipeline.TranslationPipeline;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs real chat lines through the full pipeline against a running LibreTranslate with the {@code en} and
 * {@code de} models. Skipped unless {@code JEXLINGO_IT_URL} is set, for example:
 *
 * <pre>
 * docker run -d -p 127.0.0.1:5055:5000 libretranslate/libretranslate --load-only en,de
 * JEXLINGO_IT_URL=http://127.0.0.1:5055 ./gradlew :JExLingo:jexlingo-common:test --tests "*LibreTranslateIntegrationTest"
 * </pre>
 *
 * Prints how many lines came back translated, how many needed the piece-by-piece retry and how many fell back. It
 * fails when a translated line lost a protected part, which must never reach chat.
 */
class LibreTranslateIntegrationTest {

    private static final LanguageCode DE = LanguageCode.of("de");
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final List<String> NAMES = List.of("Steve_42", "Notch", "DarkKnight");

    /** German chat lines and the parts that must survive translation unchanged. */
    private static final List<Line> GERMAN = List.of(
            new Line("Hat jemand Eisen zu verkaufen?"),
            new Line("Hat jemand @Steve_42 gesehen?", "@Steve_42"),
            new Line("Kommst du heute zu Notch auf die Insel?", "Notch"),
            new Line("Danke DarkKnight, du bist der Beste", "DarkKnight"),
            new Line("Notch hat mir Diamanten geschenkt", "Notch"),
            new Line("schau mal auf https://example.com/shop vorbei", "https://example.com/shop"),
            new Line("ich verkaufe [item] für 20 Coins", "[item]"),
            new Line("&aHallo zusammen, wie gehts?", "&a"),
            new Line("HILFE ICH STERBE GLEICH"),
            new Line("kp wo die base ist, vllt fragst du Notch", "Notch"),
            new Line("lol das war knapp"),
            new Line("Wer will mit auf OneBlock?", "OneBlock"),
            new Line("meine Insel ist jetzt auf Level 5"),
            new Line("gg, gute Runde"),
            new Line("Steve_42 und DarkKnight sind afk", "Steve_42", "DarkKnight"));

    private record Line(String text, String... kept) {
    }

    @Test
    void realChatLinesKeepEveryProtectedPart() {
        String url = System.getenv("JEXLINGO_IT_URL");
        assumeTrue(url != null && !url.isBlank(), "JEXLINGO_IT_URL is not set");

        ProviderSettings settings = new ProviderSettings(LibreTranslateProvider.ID, URI.create(url),
                System.getenv("JEXLINGO_IT_KEY"), Duration.ofSeconds(3), Duration.ofSeconds(20), 4,
                new BreakerSettings(50, Duration.ofSeconds(5), Duration.ofMinutes(1)));
        LibreTranslateProvider provider = new LibreTranslateProvider(settings);
        ProviderGateway gateway = new ProviderGateway(provider, new CircuitBreaker(50, Duration.ofSeconds(5)), 4,
                Duration.ofSeconds(20));
        PipelineLayers layers = new PipelineLayers(TranslationLookup.NONE, TranslationLookup.NONE,
                pair -> MaskRules.keep(List.of("OneBlock", "gg", "afk")), ProviderResultListener.NONE,
                ChatTextPreparer.withoutSlang());
        TranslationPipeline pipeline = new TranslationPipeline(gateway,
                new TranslationCache(new CacheSettings(100, Duration.ofMinutes(1))), layers, new SkipRules(2, 256, "!"));

        Map<TranslationOrigin, Integer> origins = new EnumMap<>(TranslationOrigin.class);
        List<String> broken = new ArrayList<>();
        try {
            for (Line line : GERMAN) {
                TranslationResult result = pipeline.translate(new TranslationRequest(line.text(), DE, EN,
                        TranslationContext.CHAT), DE, new TranslateOptions(true, NAMES)).join();
                origins.merge(result.origin(), 1, Integer::sum);
                System.out.printf("%-10s %s%n           %s%n", result.origin(), line.text(), result.text());
                for (String kept : line.kept()) {
                    if (result.translated() && !result.text().contains(kept)) {
                        broken.add(line.text() + " -> " + result.text() + " (lost " + kept + ")");
                    }
                }
            }
        } finally {
            provider.close();
        }
        System.out.printf("lines %d, origins %s, broken tokens %d, saved piece by piece %d%n", GERMAN.size(),
                origins, pipeline.stats().tokenLosses(), pipeline.stats().recoveries());

        assertTrue(broken.isEmpty(), () -> "Protected parts lost: " + broken);
    }
}

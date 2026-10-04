package de.jexcellence.lingo.language;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.config.LanguageSettings;
import de.jexcellence.lingo.pipeline.FormatCodes;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Guesses the language of a chat line without a provider call, from common function words and German letters.
 * Takes no time, so it runs for every line: a German player with an English client who writes German is translated
 * from German. Words that are common in more than one language ({@code in}, {@code was}, {@code die}) are left out.
 * Without a clear winner the caller keeps the player's own language.
 *
 * @author JExcellence
 * @since 0.4.1
 */
public final class LocalLanguageGuess {

    private static final Pattern WORD = Pattern.compile("[^\\p{L}']+");
    private static final Pattern GERMAN_LETTERS = Pattern.compile("[äöüß]");
    private static final LanguageCode GERMAN = LanguageCode.of("de");

    private static final Map<LanguageCode, Set<String>> WORDS = Map.of(
            GERMAN, Set.of(
                    "ich", "du", "er", "sie", "wir", "ihr", "mich", "dich", "mir", "dir", "uns", "euch",
                    "der", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer",
                    "und", "oder", "aber", "nicht", "kein", "keine", "auch", "noch", "schon", "nur", "mal", "doch",
                    "ist", "sind", "bin", "bist", "habe", "hast", "haben", "wird", "werden", "kann", "kannst",
                    "muss", "willst", "gibt", "gibts", "mach", "machen", "geht", "gehts",
                    "wer", "wie", "wo", "warum", "wann", "welche", "jetzt", "heute", "morgen", "hier", "dort",
                    "mit", "von", "zu", "zum", "zur", "auf", "aus", "bei", "nach", "für", "über", "unter",
                    "ja", "nein", "nee", "danke", "bitte", "hallo", "moin", "servus", "gut", "sehr", "viel",
                    "alle", "alles", "nichts", "etwas", "mein", "dein", "sein", "wenn", "dann", "weil", "dass"),
            LanguageCode.of("en"), Set.of(
                    "i", "you", "he", "she", "we", "they", "me", "him", "her", "us", "them", "my", "your",
                    "the", "a", "an", "and", "or", "but", "not", "no", "yes", "yeah", "too", "only", "just",
                    "is", "are", "am", "be", "been", "have", "has", "do", "does", "did", "can", "could",
                    "would", "should", "it", "this", "that", "these", "those", "there", "here",
                    "who", "what", "where", "why", "when", "how", "which", "now", "today", "tomorrow",
                    "with", "of", "to", "for", "from", "on", "at", "by", "about", "into",
                    "thanks", "thank", "please", "hello", "hi", "hey", "good", "very", "much", "all",
                    "nothing", "something", "if", "then", "because", "anyone", "someone", "lets", "let's"));

    private LocalLanguageGuess() {
    }

    /**
     * Guesses the language of a line.
     *
     * @param text      the chat line, codes allowed
     * @param languages enabled languages
     * @return the language with clearly the most hits, or empty when unsure
     */
    public static @NotNull Optional<LanguageCode> guess(@NotNull String text, @NotNull LanguageSettings languages) {
        String lower = FormatCodes.strip(text).toLowerCase(Locale.ROOT);
        LanguageCode best = null;
        int bestScore = 0;
        int secondScore = 0;
        for (Map.Entry<LanguageCode, Set<String>> entry : WORDS.entrySet()) {
            int score = languages.isEnabled(entry.getKey()) ? score(lower, entry.getKey(), entry.getValue()) : 0;
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = entry.getKey();
            } else {
                secondScore = Math.max(secondScore, score);
            }
        }
        return bestScore > 0 && bestScore >= secondScore * 2 ? Optional.ofNullable(best) : Optional.empty();
    }

    private static int score(@NotNull String lower, @NotNull LanguageCode language, @NotNull Set<String> words) {
        int score = 0;
        for (String word : WORD.split(lower)) {
            if (words.contains(word)) {
                score += 2;
            }
        }
        if (GERMAN.equals(language) && GERMAN_LETTERS.matcher(lower).find()) {
            score += 2;
        }
        return score;
    }
}

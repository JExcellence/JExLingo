package de.jexcellence.lingo.learning;

import de.jexcellence.lingo.glossary.GlossaryTerm;
import de.jexcellence.lingo.language.LocalLanguageGuess;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Learns glossary terms from approved corrections (Premium). A correction answers only its own line, but when
 * players keep the same source word unchanged in several corrections ({@code Spawn} instead of {@code Laichen},
 * {@code OneBlock} instead of a translation), that word most likely belongs in the glossary as a keep term. Staff
 * see these proposals with {@code /lingo terms} and add them with one click; nothing is added on its own.
 *
 * @author JExcellence
 * @since 0.5.0
 */
public final class TermProposals {

    private static final Pattern WORD_SPLIT = Pattern.compile("[^\\p{L}\\p{N}_]+");
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");
    private static final int MIN_WORD_LENGTH = 3;

    private final TranslationMemoryService memory;
    private final IntSupplier votesNeeded;

    /**
     * A proposed keep term.
     *
     * @param term        the word as written in the first correction
     * @param corrections approved corrections that kept it
     */
    public record Proposal(@NotNull String term, int corrections) {
    }

    /**
     * Creates the service.
     *
     * @param memory      the translation memory
     * @param votesNeeded corrections a word needs before it is proposed
     */
    public TermProposals(@NotNull TranslationMemoryService memory, @NotNull IntSupplier votesNeeded) {
        this.memory = memory;
        this.votesNeeded = votesNeeded;
    }

    /**
     * Returns the current proposals, most corrections first.
     *
     * @param glossary the glossary terms, which are never proposed again
     * @return the proposals
     */
    public @NotNull CompletableFuture<List<Proposal>> proposals(@NotNull Collection<GlossaryTerm> glossary) {
        Set<String> known = glossary.stream()
                .map(term -> term.term().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        int needed = votesNeeded.getAsInt();
        return memory.approvedEntries().thenApply(entries -> find(entries, known, needed));
    }

    /**
     * Finds words that were kept as written in at least {@code needed} approved corrections.
     *
     * @param entries approved corrections
     * @param known   lower-case words already in the glossary
     * @param needed  corrections a word needs
     * @return the proposals, most corrections first
     */
    public static @NotNull List<Proposal> find(@NotNull List<MemoryEntry> entries, @NotNull Set<String> known,
                                               int needed) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> spelling = new LinkedHashMap<>();
        for (MemoryEntry entry : entries) {
            Set<String> target = lowerWords(entry.targetText());
            for (String word : candidates(entry.sourceText())) {
                String lower = word.toLowerCase(Locale.ROOT);
                if (target.contains(lower) && !known.contains(lower)) {
                    counts.merge(lower, 1, Integer::sum);
                    spelling.putIfAbsent(lower, word);
                }
            }
        }
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= Math.max(1, needed))
                .map(entry -> new Proposal(spelling.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingInt(Proposal::corrections).reversed()
                        .thenComparing(proposal -> proposal.term().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static @NotNull Collection<String> candidates(@NotNull String text) {
        Map<String, String> distinct = new LinkedHashMap<>();
        Arrays.stream(WORD_SPLIT.split(text))
                .filter(word -> word.length() >= MIN_WORD_LENGTH)
                .filter(word -> HAS_LETTER.matcher(word).find())
                .filter(word -> !LocalLanguageGuess.isCommonWord(word))
                .forEach(word -> distinct.putIfAbsent(word.toLowerCase(Locale.ROOT), word));
        return distinct.values();
    }

    private static @NotNull Set<String> lowerWords(@NotNull String text) {
        return Arrays.stream(WORD_SPLIT.split(text.toLowerCase(Locale.ROOT)))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}

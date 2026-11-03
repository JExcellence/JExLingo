package de.jexcellence.lingo.learning;

import de.jexcellence.lingo.glossary.GlossaryTerm;
import de.jexcellence.lingo.language.LocalLanguageGuess;
import de.jexcellence.lingo.pipeline.LanguagePair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
     * A proposed force term for one language pair: players replaced the same machine word with the same word in
     * several corrections, and those lines share exactly one uncommon source word.
     *
     * @param pair        the language pair
     * @param term        the source word, as written in the first correction
     * @param replacement the word players used instead of the machine's
     * @param corrections approved corrections behind the proposal
     */
    public record ForceProposal(@NotNull LanguagePair pair, @NotNull String term, @NotNull String replacement,
                                int corrections) {
    }

    /** One word replaced by another; the grouping key of force proposals. */
    private record Swap(@NotNull LanguagePair pair, @NotNull String machine, @NotNull String corrected) {
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
     * Returns the current force proposals, most corrections first.
     *
     * @param glossary the glossary terms, which are never proposed again
     * @return the proposals
     */
    public @NotNull CompletableFuture<List<ForceProposal>> forceProposals(@NotNull Collection<GlossaryTerm> glossary) {
        Set<String> known = glossary.stream()
                .map(term -> term.term().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        int needed = votesNeeded.getAsInt();
        return memory.approvedCorrections().thenApply(corrections -> findForce(corrections, known, needed));
    }

    /**
     * Finds repeated one-word corrections and the source word behind them. A correction counts when the machine
     * answer and the correction have the same words except one; the source word is the only uncommon word that every
     * line of the group contains.
     *
     * @param corrections approved corrections with their machine answers
     * @param known       lower-case words already in the glossary
     * @param needed      corrections a replacement needs
     * @return the proposals, most corrections first
     */
    public static @NotNull List<ForceProposal> findForce(@NotNull List<TranslationMemoryService.Correction> corrections,
                                                         @NotNull Set<String> known, int needed) {
        Map<Swap, List<TranslationMemoryService.Correction>> groups = new LinkedHashMap<>();
        Map<Swap, String> spelling = new HashMap<>();
        for (TranslationMemoryService.Correction correction : corrections) {
            swapOf(correction).ifPresent(swap -> {
                Swap key = new Swap(swap.pair(), swap.machine().toLowerCase(Locale.ROOT),
                        swap.corrected().toLowerCase(Locale.ROOT));
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(correction);
                spelling.putIfAbsent(key, swap.corrected());
            });
        }
        List<ForceProposal> proposals = new ArrayList<>();
        groups.forEach((swap, group) -> {
            if (group.size() >= Math.max(1, needed)) {
                sharedSourceWord(group)
                        .filter(word -> !known.contains(word.toLowerCase(Locale.ROOT)))
                        .ifPresent(word -> proposals.add(new ForceProposal(swap.pair(), word, spelling.get(swap),
                                group.size())));
            }
        });
        proposals.sort(Comparator.comparingInt(ForceProposal::corrections).reversed()
                .thenComparing(proposal -> proposal.term().toLowerCase(Locale.ROOT)));
        return proposals;
    }

    private static @NotNull Optional<Swap> swapOf(@NotNull TranslationMemoryService.Correction correction) {
        List<String> machine = words(correction.machine());
        List<String> corrected = words(correction.corrected());
        if (machine.size() != corrected.size()) {
            return Optional.empty();
        }
        int changed = -1;
        for (int i = 0; i < machine.size(); i++) {
            if (!machine.get(i).equalsIgnoreCase(corrected.get(i))) {
                if (changed >= 0) {
                    return Optional.empty();
                }
                changed = i;
            }
        }
        return changed < 0 ? Optional.empty()
                : Optional.of(new Swap(correction.pair(), machine.get(changed), corrected.get(changed)));
    }

    private static @NotNull Optional<String> sharedSourceWord(
            @NotNull List<TranslationMemoryService.Correction> group) {
        Map<String, String> shared = null;
        for (TranslationMemoryService.Correction correction : group) {
            Map<String, String> words = new LinkedHashMap<>();
            candidates(correction.sourceText())
                    .forEach(word -> words.putIfAbsent(word.toLowerCase(Locale.ROOT), word));
            if (shared == null) {
                shared = words;
            } else {
                shared.keySet().retainAll(words.keySet());
            }
        }
        return shared != null && shared.size() == 1 ? shared.values().stream().findFirst() : Optional.empty();
    }

    private static @NotNull List<String> words(@NotNull String text) {
        return Arrays.stream(WORD_SPLIT.split(text)).filter(word -> !word.isEmpty()).toList();
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

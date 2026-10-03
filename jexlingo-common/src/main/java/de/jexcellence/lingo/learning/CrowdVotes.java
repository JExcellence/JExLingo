package de.jexcellence.lingo.learning;

import de.jexcellence.lingo.pipeline.TextNormalizer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Decides when players agree on a correction. Pending suggestions for the same original are grouped by their
 * normalized text; a group wins when that many different players submitted it. One player suggesting the same text
 * twice counts once, so nobody can approve their own wording alone.
 *
 * @author JExcellence
 * @since 0.2.0
 */
public final class CrowdVotes {

    private CrowdVotes() {
    }

    /**
     * The result of a vote.
     *
     * @param winner     the entry to approve (the oldest of the winning group)
     * @param duplicates the other entries of the winning group, to reject as merged
     */
    public record Outcome(@NotNull MemoryEntry winner, @NotNull List<MemoryEntry> duplicates) {
    }

    /**
     * Counts the votes.
     *
     * @param pending pending suggestions for one original and language pair, oldest first
     * @param needed  distinct players needed; 0 or less disables crowd approval
     * @return the winning group, or empty
     */
    public static @NotNull Optional<Outcome> count(@NotNull List<MemoryEntry> pending, int needed) {
        if (needed <= 0) {
            return Optional.empty();
        }
        Map<String, List<MemoryEntry>> groups = new LinkedHashMap<>();
        for (MemoryEntry entry : pending) {
            groups.computeIfAbsent(TextNormalizer.key(entry.targetText()), ignored -> new ArrayList<>())
                    .add(entry);
        }
        for (List<MemoryEntry> group : groups.values()) {
            Set<UUID> voters = new HashSet<>();
            group.stream().map(MemoryEntry::submittedBy).filter(Objects::nonNull).forEach(voters::add);
            if (voters.size() >= needed) {
                return Optional.of(new Outcome(group.getFirst(), List.copyOf(group.subList(1, group.size()))));
            }
        }
        return Optional.empty();
    }
}

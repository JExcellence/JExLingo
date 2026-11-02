package de.jexcellence.lingo.learning;

import de.jexcellence.lingo.pipeline.LanguagePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A frequent phrase with a kept translation.
 *
 * @param id         the database id
 * @param pair       the language pair
 * @param sourceKey  the normalized original
 * @param targetText the translation
 * @param pinnedAt   when it was pinned, {@code null} until stored
 * @author JExcellence
 * @since 0.1.0
 */
public record PinnedPhrase(long id, @NotNull LanguagePair pair, @NotNull String sourceKey,
                           @NotNull String targetText, @Nullable Instant pinnedAt) {
}

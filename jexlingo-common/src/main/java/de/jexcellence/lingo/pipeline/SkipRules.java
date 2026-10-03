package de.jexcellence.lingo.pipeline;

import de.jexcellence.lingo.api.TranslationContext;
import de.jexcellence.lingo.config.ChatSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Decides which texts are never sent to the provider: too short or too long, without letters, or starting with the
 * chat skip prefix. The prefix only applies to public chat; other plugins decide themselves what to translate.
 *
 * @param minLength  shortest translated text
 * @param maxLength  longest translated text
 * @param skipPrefix chat prefix that marks a message as not to translate; empty disables it
 * @author JExcellence
 * @since 0.1.0
 */
public record SkipRules(int minLength, int maxLength, @NotNull String skipPrefix) {

    /**
     * Rules from the chat settings.
     *
     * @param chat the chat settings
     * @return the rules
     */
    public static @NotNull SkipRules from(@NotNull ChatSettings chat) {
        return new SkipRules(chat.minLength(), chat.maxLength(), chat.skipPrefix());
    }

    /**
     * Whether a text is left as it is.
     *
     * @param text    the text
     * @param context where the text comes from
     * @return {@code true} when the text must not be translated
     */
    public boolean skips(@NotNull String text, @NotNull TranslationContext context) {
        String trimmed = text.trim();
        if (trimmed.length() < minLength || trimmed.length() > maxLength) {
            return true;
        }
        if (context == TranslationContext.CHAT && !skipPrefix.isEmpty() && trimmed.startsWith(skipPrefix)) {
            return true;
        }
        return !TextNormalizer.hasLetters(trimmed);
    }
}

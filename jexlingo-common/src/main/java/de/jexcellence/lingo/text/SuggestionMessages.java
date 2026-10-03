package de.jexcellence.lingo.text;

import de.jexcellence.lingo.learning.SuggestionService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * The chat feedback for every suggestion outcome, shared by the command and the Bedrock form.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class SuggestionMessages {

    private static final String KEY = "lingo.suggest.";

    private SuggestionMessages() {
    }

    /**
     * Sends the feedback for an outcome.
     *
     * @param player the player
     * @param result the outcome
     */
    public static void send(@NotNull Player player, @NotNull SuggestionService.Result result) {
        String key = switch (result) {
            case SAVED -> "saved";
            case APPROVED_BY_VOTES -> "approved_by_votes";
            case UNKNOWN_MESSAGE -> "unknown_message";
            case NOT_TRANSLATED -> "not_translated";
            case UNCHANGED -> "unchanged";
            case INVALID -> "invalid";
            case LIMIT -> "limit";
            case PLAYTIME -> "playtime";
            case BLOCKED -> "blocked";
            default -> throw new IllegalStateException("Unexpected suggestion result: " + result);
        };
        SafeText.msg(KEY + key).with("max", SuggestionService.MAX_LENGTH).prefix().send(player);
    }
}

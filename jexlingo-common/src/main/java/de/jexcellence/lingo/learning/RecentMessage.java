package de.jexcellence.lingo.learning;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A recent chat message with its translations, kept in memory for a few minutes so a viewer can suggest a better
 * translation. Never written to disk.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class RecentMessage {

    private final String id;
    private final UUID sender;
    private final String senderName;
    private final String original;
    private final AtomicReference<LanguageCode> source;
    private final Map<LanguageCode, String> translations = new ConcurrentHashMap<>();

    /**
     * Creates the entry.
     *
     * @param id         short id used in {@code /lingo suggest <id>}
     * @param sender     the sender's UUID
     * @param senderName the sender's name
     * @param original   the message as written
     * @param source     the assumed source language; updated when detection finishes
     */
    public RecentMessage(@NotNull String id, @NotNull UUID sender, @NotNull String senderName,
                         @NotNull String original, @NotNull LanguageCode source) {
        this.id = id;
        this.sender = sender;
        this.senderName = senderName;
        this.original = original;
        this.source = new AtomicReference<>(source);
    }

    /**
     * Returns the short id.
     *
     * @return the short id
     */
    public @NotNull String id() {
        return id;
    }

    /**
     * Returns the sender's UUID.
     *
     * @return the sender's UUID
     */
    public @NotNull UUID sender() {
        return sender;
    }

    /**
     * Returns the sender's name.
     *
     * @return the sender's name
     */
    public @NotNull String senderName() {
        return senderName;
    }

    /**
     * Returns the message as written.
     *
     * @return the message as written
     */
    public @NotNull String original() {
        return original;
    }

    /**
     * Returns the source language.
     *
     * @return the source language
     */
    public @NotNull LanguageCode source() {
        return source.get();
    }

    /**
     * Sets the detected source language.
     *
     * @param language the language
     */
    public void setSource(@NotNull LanguageCode language) {
        source.set(language);
    }

    /**
     * Records a translation shown to viewers.
     *
     * @param target      the target language
     * @param translation the shown text
     */
    public void putTranslation(@NotNull LanguageCode target, @NotNull String translation) {
        translations.put(target, translation);
    }

    /**
     * The translation shown in a language.
     *
     * @param target the target language
     * @return the shown text, or empty when no translation was shown
     */
    public @NotNull Optional<String> translation(@NotNull LanguageCode target) {
        return Optional.ofNullable(translations.get(target));
    }
}

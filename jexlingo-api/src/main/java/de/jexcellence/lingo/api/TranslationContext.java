package de.jexcellence.lingo.api;

/**
 * Where a text to translate comes from. Plugins pass it so limits and statistics can tell public chat apart from
 * other sources.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public enum TranslationContext {

    /** Public chat. */
    CHAT,

    /** A private message between two players. */
    PRIVATE_MESSAGE,

    /** A message relayed from or to Discord. */
    DISCORD,

    /** Any other plugin call through {@link JExLingoApi}. */
    API
}

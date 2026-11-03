package de.jexcellence.lingo.pipeline;

/**
 * Which online player names are kept out of the translator ({@code chat.protect-names}).
 *
 * @author JExcellence
 * @since 0.5.0
 */
public enum NameProtection {

    /**
     * Every name in any case, except names that are ordinary words ({@code Max}, {@code Help}): those only when
     * written as the player is called, capitalised or in capitals.
     */
    SMART,

    /** Every online name in any case, also when it is an ordinary word. */
    ALWAYS,

    /** Only names written as {@code @name}; other names go to the translator like any word. */
    MENTIONS
}

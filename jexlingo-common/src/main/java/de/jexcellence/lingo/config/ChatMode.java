package de.jexcellence.lingo.config;

/**
 * How a translated chat line reaches the viewer.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public enum ChatMode {

    /** Wait a short time for the translation and show one line; fall back to the original on timeout. */
    INLINE,

    /** Show the original at once and send the translation as an extra line when it is ready. */
    FOLLOW_UP
}

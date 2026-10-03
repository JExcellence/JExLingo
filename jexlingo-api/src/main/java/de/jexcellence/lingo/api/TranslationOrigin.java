package de.jexcellence.lingo.api;

/**
 * Which layer produced a {@link TranslationResult}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public enum TranslationOrigin {

    /** Source and target language are the same; the text is unchanged. */
    SAME_LANGUAGE,

    /** A skip rule matched (too short, numbers only, skip prefix, opt-out); the text is unchanged. */
    SKIPPED,

    /** The whole text consisted of glossary terms. */
    GLOSSARY,

    /** An approved correction from the translation memory. */
    MEMORY,

    /** A pinned frequent phrase. */
    PHRASE,

    /** The in-memory cache of earlier provider results. */
    CACHE,

    /** The translation provider. */
    PROVIDER,

    /** The provider failed, timed out or is paused; the text is unchanged. */
    FALLBACK;

    /**
     * Returns whether this origin changes the text.
     *
     * @return whether this origin changes the text
     */
    public boolean translates() {
        return this != SAME_LANGUAGE && this != SKIPPED && this != FALLBACK;
    }
}

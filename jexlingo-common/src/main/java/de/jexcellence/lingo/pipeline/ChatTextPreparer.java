package de.jexcellence.lingo.pipeline;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Cleans chat text before it reaches the provider, which is trained on written text:
 *
 * <ul>
 *     <li>letter spam is shortened ({@code hellooooo} -> {@code helloo})</li>
 *     <li>shouting is lowered for the translator and raised again in the result ({@code HELP ME} -> {@code HILF MIR})</li>
 *     <li>chat abbreviations of the source language are expanded ({@link SlangDictionary})</li>
 * </ul>
 *
 * Runs on the masked text, so protected parts (names, URLs, glossary terms) are tokens and stay untouched.
 *
 * @author JExcellence
 * @since 0.2.0
 */
public final class ChatTextPreparer {

    private static final Pattern REPEATS = Pattern.compile("(\\p{L})\\1{2,}");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final int SHOUT_MIN_LETTERS = 4;

    private final AtomicReference<SlangDictionary> slang;

    /**
     * The text sent to the provider and how to finish its answer.
     *
     * @param text     the cleaned text
     * @param shouting whether the original was written in capitals
     */
    public record Prepared(@NotNull String text, boolean shouting) {
    }

    /**
     * Creates a preparer.
     *
     * @param slang the slang dictionary
     */
    public ChatTextPreparer(@NotNull SlangDictionary slang) {
        this.slang = new AtomicReference<>(slang);
    }

    /**
     * Returns a preparer without slang expansion.
     *
     * @return a preparer without slang expansion
     */
    public static @NotNull ChatTextPreparer withoutSlang() {
        return new ChatTextPreparer(SlangDictionary.EMPTY);
    }

    /**
     * Cleans masked text for the provider.
     *
     * @param masked the masked text
     * @param source the source language
     * @return the cleaned text
     */
    public @NotNull Prepared prepare(@NotNull String masked, @NotNull LanguageCode source) {
        String text = REPEATS.matcher(masked).replaceAll("$1$1");
        boolean shouting = isShouting(text);
        if (shouting) {
            text = text.toLowerCase(Locale.ROOT);
        }
        return new Prepared(slang.get().expand(text, source), shouting);
    }

    /**
     * Applies the original's style to the provider's answer.
     *
     * @param translated the provider's answer, still masked
     * @param prepared   what was sent
     * @return the finished text
     */
    public @NotNull String finish(@NotNull String translated, @NotNull Prepared prepared) {
        return prepared.shouting() ? translated.toUpperCase(Locale.ROOT) : translated;
    }

    /**
     * Replaces the dictionary after a reload.
     *
     * @param dictionary the new dictionary
     */
    public void setSlang(@NotNull SlangDictionary dictionary) {
        slang.set(dictionary);
    }

    /**
     * Returns the active slang dictionary.
     *
     * @return the active slang dictionary
     */
    public @NotNull SlangDictionary slang() {
        return slang.get();
    }

    private static boolean isShouting(@NotNull String text) {
        long letters = LETTER.matcher(text).results().count();
        return letters >= SHOUT_MIN_LETTERS && text.equals(text.toUpperCase(Locale.ROOT))
                && !text.equals(text.toLowerCase(Locale.ROOT));
    }
}

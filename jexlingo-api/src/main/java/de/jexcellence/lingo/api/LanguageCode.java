package de.jexcellence.lingo.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A language as a lower-case ISO 639 code ({@code de}, {@code en}). Region parts of a locale
 * ({@code de_DE}, {@code en-US}) are dropped, so a client locale and a configured language compare equal.
 *
 * @param code the lower-case two- or three-letter code
 * @author JExcellence
 * @since 0.1.0
 */
public record LanguageCode(@NotNull String code) {

    private static final Pattern VALID = Pattern.compile("[a-z]{2,3}");

    /**
     * Validates the code.
     *
     * @param code the lower-case code
     * @throws IllegalArgumentException when the code is not two or three lower-case letters
     */
    public LanguageCode {
        if (!VALID.matcher(code).matches()) {
            throw new IllegalArgumentException("Not a language code: " + code);
        }
    }

    /**
     * Parses a code or locale string such as {@code DE}, {@code de_DE} or {@code en-US}.
     *
     * @param raw the raw text, may be {@code null}
     * @return the language, or empty when the text is no valid code
     */
    public static @NotNull Optional<LanguageCode> parse(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        int separator = indexOfSeparator(trimmed);
        String language = separator < 0 ? trimmed : trimmed.substring(0, separator);
        if (!VALID.matcher(language).matches()) {
            return Optional.empty();
        }
        return Optional.of(new LanguageCode(language));
    }

    /**
     * Parses a code and fails on invalid input.
     *
     * @param raw the raw code
     * @return the language
     * @throws IllegalArgumentException when the text is no valid code
     */
    public static @NotNull LanguageCode of(@NotNull String raw) {
        return parse(raw).orElseThrow(() -> new IllegalArgumentException("Not a language code: " + raw));
    }

    /**
     * The language of a Java locale.
     *
     * @param locale the locale
     * @return the language, or empty for the root locale
     */
    public static @NotNull Optional<LanguageCode> fromLocale(@NotNull Locale locale) {
        return parse(locale.getLanguage());
    }

    /**
     * Returns the code in upper case for compact labels such as {@code DE}.
     *
     * @return the code in upper case for compact labels such as {@code DE}
     */
    public @NotNull String upper() {
        return code.toUpperCase(Locale.ROOT);
    }

    @Override
    public @NotNull String toString() {
        return code;
    }

    private static int indexOfSeparator(@NotNull String text) {
        int underscore = text.indexOf('_');
        int dash = text.indexOf('-');
        if (underscore < 0) {
            return dash;
        }
        if (dash < 0) {
            return underscore;
        }
        return Math.min(underscore, dash);
    }
}

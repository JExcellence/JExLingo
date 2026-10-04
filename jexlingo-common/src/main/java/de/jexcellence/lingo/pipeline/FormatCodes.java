package de.jexcellence.lingo.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colour and format codes players type into chat: legacy {@code &a} / {@code §a}, hex {@code &#A1F3BE},
 * {@code &x&a&1&f&3&b&e}, {@code <#A1F3BE>} and MiniMessage tags such as {@code <bold>} or {@code </red>}. The
 * translator never sees them: a leading run is cut off and put back in front of the translation, codes inside the
 * text are protected by {@link TokenMasker}, and lines shown to players without the chat formatter use
 * {@link #strip(String)}.
 *
 * @author JExcellence
 * @since 0.4.1
 */
public final class FormatCodes {

    /** One code, as a regex fragment for {@link TokenMasker}. */
    static final String CODE = "[&§]x(?:[&§][0-9A-Fa-f]){6}"
            + "|[&§]#[0-9A-Fa-f]{6}"
            + "|[&§][0-9A-Fa-fK-Ok-oRr]"
            + "|<#[0-9A-Fa-f]{6}>"
            + "|</?[A-Za-z_#!][A-Za-z0-9_#:!.\\-]{0,47}>";

    private static final Pattern ANY = Pattern.compile(CODE);
    private static final Pattern LEADING = Pattern.compile("^(?:\\s*(?:" + CODE + "))+\\s*");

    private FormatCodes() {
    }

    /**
     * A text split into its leading codes and the rest.
     *
     * @param prefix the leading codes with surrounding spaces, or an empty string
     * @param body   the text after them
     */
    public record Split(@NotNull String prefix, @NotNull String body) {

        /**
         * Returns whether the text started with a code.
         *
         * @return whether the text started with a code
         */
        public boolean hasPrefix() {
            return !prefix.isEmpty();
        }
    }

    /**
     * Splits off the codes at the start of a text.
     *
     * @param text the chat text
     * @return the leading codes and the rest
     */
    public static @NotNull Split split(@NotNull String text) {
        Matcher matcher = LEADING.matcher(text);
        if (!matcher.find()) {
            return new Split("", text);
        }
        return new Split(matcher.group(), text.substring(matcher.end()));
    }

    /**
     * Removes every code.
     *
     * @param text the chat text
     * @return the text without codes
     */
    public static @NotNull String strip(@NotNull String text) {
        return ANY.matcher(text).replaceAll("").strip();
    }
}

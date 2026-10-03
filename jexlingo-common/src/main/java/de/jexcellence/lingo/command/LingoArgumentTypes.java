package de.jexcellence.lingo.command;

import com.raindropcentral.commands.v2.argument.ArgumentType;
import com.raindropcentral.commands.v2.argument.ArgumentTypeRegistry;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.glossary.GlossaryMode;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.settings.IncomingMode;
import de.jexcellence.lingo.stats.StatsPeriod;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The plugin's command argument types: a language choice ({@code auto} or an enabled language), any language code,
 * an incoming mode, an {@code enable}/{@code disable} switch and a glossary mode.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoArgumentTypes {

    /** Value of the language argument that means "follow the client language". */
    public static final String AUTO = "auto";

    /** Value that switches an option on. */
    public static final String ENABLE = "enable";

    /** Value that switches an option off. */
    public static final String DISABLE = "disable";

    private static final String VALUE = "value";

    private LingoArgumentTypes() {
    }

    /**
     * Registers every type.
     *
     * @param registry the registry
     * @param resolver the language resolver, for the enabled languages
     * @return the same registry
     */
    public static @NotNull ArgumentTypeRegistry register(@NotNull ArgumentTypeRegistry registry,
                                                         @NotNull LanguageResolver resolver) {
        registry.register(ArgumentType.custom("lingo_language", String.class,
                (sender, raw) -> parseLanguageChoice(raw, resolver),
                (sender, partial) -> filter(languageChoices(resolver), partial)));
        registry.register(ArgumentType.custom("lingo_any_language", LanguageCode.class,
                (sender, raw) -> LanguageCode.parse(raw)
                        .map(ArgumentType.ParseResult::ok)
                        .orElseGet(() -> ArgumentType.ParseResult.err("lingo.error.unknown_language",
                                Map.of(VALUE, raw))),
                (sender, partial) -> filter(resolver.languages().enabled().stream().map(LanguageCode::code)
                        .toList(), partial)));
        registry.register(ArgumentType.custom("lingo_incoming", IncomingMode.class,
                (sender, raw) -> IncomingMode.parse(raw)
                        .map(ArgumentType.ParseResult::ok)
                        .orElseGet(() -> ArgumentType.ParseResult.err("lingo.error.unknown_incoming",
                                Map.of(VALUE, raw))),
                (sender, partial) -> filter(Stream.of(IncomingMode.values()).map(IncomingMode::id).toList(),
                        partial)));
        registry.register(ArgumentType.custom("lingo_period", String.class,
                (sender, raw) -> StatsPeriod.parse(raw)
                        .map(period -> ArgumentType.ParseResult.ok(period.id()))
                        .orElseGet(() -> ArgumentType.ParseResult.err("lingo.error.unknown_period",
                                Map.of(VALUE, raw))),
                (sender, partial) -> filter(Stream.of(StatsPeriod.values()).map(StatsPeriod::id).toList(),
                        partial)));
        registry.register(ArgumentType.custom("lingo_toggle", Boolean.class,
                LingoArgumentTypes::parseToggle,
                (sender, partial) -> filter(List.of(ENABLE, DISABLE), partial)));
        registry.register(ArgumentType.custom("lingo_glossary_mode", GlossaryMode.class,
                (sender, raw) -> GlossaryMode.find(raw)
                        .map(ArgumentType.ParseResult::ok)
                        .orElseGet(() -> ArgumentType.ParseResult.err("lingo.error.unknown_mode",
                                Map.of(VALUE, raw))),
                (sender, partial) -> filter(Stream.of(GlossaryMode.values())
                        .map(mode -> mode.name().toLowerCase(Locale.ROOT)).toList(), partial)));
        return registry;
    }

    private static @NotNull ArgumentType.ParseResult<String> parseLanguageChoice(@NotNull String raw,
                                                                                 @NotNull LanguageResolver resolver) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (languageChoices(resolver).contains(value)) {
            return ArgumentType.ParseResult.ok(value);
        }
        return ArgumentType.ParseResult.err("lingo.error.language_not_enabled", Map.of(VALUE, raw));
    }

    private static @NotNull ArgumentType.ParseResult<Boolean> parseToggle(@NotNull CommandSender sender,
                                                                          @NotNull String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (ENABLE.equals(value)) {
            return ArgumentType.ParseResult.ok(Boolean.TRUE);
        }
        if (DISABLE.equals(value)) {
            return ArgumentType.ParseResult.ok(Boolean.FALSE);
        }
        return ArgumentType.ParseResult.err("lingo.error.unknown_toggle", Map.of(VALUE, raw));
    }

    private static @NotNull List<String> languageChoices(@NotNull LanguageResolver resolver) {
        List<String> choices = new ArrayList<>();
        choices.add(AUTO);
        resolver.languages().enabled().forEach(language -> choices.add(language.code()));
        return choices;
    }

    private static @NotNull List<String> filter(@NotNull List<String> values, @NotNull String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(lower)).toList();
    }
}

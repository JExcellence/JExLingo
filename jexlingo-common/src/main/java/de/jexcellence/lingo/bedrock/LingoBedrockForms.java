package de.jexcellence.lingo.bedrock;

import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.jextranslate.MessageBuilder;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.glossary.GlossaryService;
import de.jexcellence.lingo.glossary.GlossaryTerm;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.learning.MemoryEntry;
import de.jexcellence.lingo.learning.RecentMessage;
import de.jexcellence.lingo.learning.RecentMessageBuffer;
import de.jexcellence.lingo.learning.SuggestionService;
import de.jexcellence.lingo.learning.TranslationMemoryService;
import de.jexcellence.lingo.settings.PlayerLanguageSettings;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import de.jexcellence.lingo.text.SafeText;
import de.jexcellence.lingo.text.SuggestionMessages;
import de.jexcellence.lingo.view.LingoSettingsView;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Bedrock mirrors of every JExLingo menu, sent as Cumulus forms through Floodgate: settings, suggesting a better
 * translation (Bedrock players cannot click chat), the review queue and the glossary. Form callbacks arrive on
 * Floodgate's thread, so every game action is scheduled onto the player's thread.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoBedrockForms {

    private static final String KEY = "bedrock.";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BedrockFormBridge bridge;
    private final FormServices services;

    /**
     * The services the forms need.
     *
     * @param settings    player settings
     * @param resolver    language resolver
     * @param recent      recent message buffer
     * @param suggestions suggestion service, or {@code null} in the free edition
     * @param memory      translation memory, or {@code null} in the free edition
     * @param glossary    the glossary
     * @param scheduler   platform scheduler
     */
    public record FormServices(
            @NotNull PlayerSettingsService settings,
            @NotNull LanguageResolver resolver,
            @NotNull RecentMessageBuffer recent,
            @Nullable SuggestionService suggestions,
            @Nullable TranslationMemoryService memory,
            @NotNull GlossaryService glossary,
            @NotNull PlatformScheduler scheduler
    ) {
    }

    /**
     * Creates the forms.
     *
     * @param bridge   the Floodgate bridge
     * @param services the services
     */
    public LingoBedrockForms(@NotNull BedrockFormBridge bridge, @NotNull FormServices services) {
        this.bridge = bridge;
        this.services = services;
    }

    /**
     * Whether a player joined through Geyser.
     *
     * @param player a player
     * @return whether the player joined through Geyser
     */
    public boolean isBedrock(@NotNull Player player) {
        return bridge.isBedrockPlayer(player);
    }

    /**
     * The settings form.
     *
     * @param player the Bedrock player
     */
    public void openSettings(@NotNull Player player) {
        PlayerLanguageSettings current = services.settings().get(player.getUniqueId());
        List<LanguageCode> enabled = services.resolver().languages().enabled();
        List<String> choices = new ArrayList<>();
        choices.add(text(player, "lingo_settings.value.auto"));
        enabled.forEach(language -> choices.add(LingoSettingsView.languageName(player, language)));
        int selected = current.language() == null ? 0 : enabled.indexOf(current.language()) + 1;
        CustomForm form = CustomForm.builder()
                .title(text(player, KEY + "settings.title"))
                .label(text(player, KEY + "settings.intro"))
                .dropdown(text(player, "lingo_settings.option.language.label"), choices, Math.max(0, selected))
                .toggle(text(player, "lingo_settings.option.incoming.label"), current.translateIncoming())
                .toggle(text(player, "lingo_settings.option.outgoing.label"), current.translateOutgoing())
                .toggle(text(player, "lingo_settings.option.original.label"), current.showOriginal())
                .validResultHandler(response -> applySettings(player, enabled, response))
                .build();
        bridge.sendForm(player, form);
    }

    /**
     * The suggestion flow: pick one of the last translated lines, then type the better translation.
     *
     * @param player the Bedrock player
     */
    public void openSuggest(@NotNull Player player) {
        SuggestionService suggestions = services.suggestions();
        if (suggestions == null) {
            return;
        }
        LanguageCode language = services.resolver().resolve(player);
        List<RecentMessage> seen = services.recent().seenBy(player.getUniqueId()).stream()
                .filter(message -> message.translation(language).isPresent())
                .toList();
        if (seen.isEmpty()) {
            SafeText.msg("lingo.suggest.nothing_recent").prefix().send(player);
            return;
        }
        SimpleForm.Builder form = SimpleForm.builder()
                .title(text(player, KEY + "suggest.title"))
                .content(text(player, KEY + "suggest.pick"));
        seen.forEach(message -> form.button(userLine(player, KEY + "suggest.entry",
                Map.of("name", message.senderName(), "text", message.original()))));
        form.validResultHandler(response -> openSuggestInput(player, seen.get(response.clickedButtonId()),
                language, suggestions));
        bridge.sendForm(player, form.build());
    }

    /**
     * The review queue.
     *
     * @param player the Bedrock staff member
     */
    public void openReview(@NotNull Player player) {
        TranslationMemoryService memory = services.memory();
        if (memory == null) {
            return;
        }
        memory.pending().thenAccept(entries -> {
            SimpleForm.Builder form = SimpleForm.builder()
                    .title(text(player, KEY + "review.title"))
                    .content(entries.isEmpty() ? text(player, "lingo_review.empty.description")
                            : text(player, KEY + "review.pick"));
            entries.forEach(entry -> form.button(userLine(player, KEY + "review.entry",
                    Map.of("text", entry.sourceText()))));
            form.validResultHandler(response -> openReviewDecision(player, entries.get(response.clickedButtonId()),
                    memory));
            bridge.sendForm(player, form.build());
        });
    }

    /**
     * The glossary list; picking a term asks whether to remove it.
     *
     * @param player the Bedrock staff member
     */
    public void openGlossary(@NotNull Player player) {
        List<GlossaryTerm> terms = services.glossary().terms();
        SimpleForm.Builder form = SimpleForm.builder()
                .title(text(player, KEY + "glossary.title"))
                .content(terms.isEmpty() ? text(player, "lingo_glossary.empty.description")
                        : text(player, KEY + "glossary.pick"));
        terms.forEach(term -> form.button(term.term()));
        form.validResultHandler(response -> confirmRemove(player, terms.get(response.clickedButtonId())));
        bridge.sendForm(player, form.build());
    }

    private void applySettings(@NotNull Player player, @NotNull List<LanguageCode> enabled,
                               @NotNull CustomFormResponse response) {
        int languageIndex = response.asDropdown(1);
        LanguageCode language = languageIndex <= 0 || languageIndex > enabled.size()
                ? null : enabled.get(languageIndex - 1);
        boolean incoming = response.asToggle(2);
        boolean outgoing = response.asToggle(3);
        boolean original = response.asToggle(4);
        services.settings().update(player.getUniqueId(), current -> current.withLanguage(language)
                        .withIncoming(incoming).withOutgoing(outgoing).withShowOriginal(original))
                .thenRun(() -> services.scheduler().runAtEntity(player,
                        () -> SafeText.msg("lingo.settings.saved").prefix().send(player)));
    }

    private void openSuggestInput(@NotNull Player player, @NotNull RecentMessage message,
                                  @NotNull LanguageCode language, @NotNull SuggestionService suggestions) {
        String current = message.translation(language).orElse("");
        CustomForm form = CustomForm.builder()
                .title(text(player, KEY + "suggest.title"))
                .label(userLine(player, KEY + "suggest.original", Map.of("text", message.original())))
                .input(text(player, KEY + "suggest.input"), current, current)
                .validResultHandler(response -> {
                    String suggestion = response.asInput(1);
                    services.scheduler().runAtEntity(player, () -> suggestions
                            .suggest(player, message.id(), suggestion == null ? "" : suggestion)
                            .thenAccept(result -> services.scheduler().runAtEntity(player,
                                    () -> SuggestionMessages.send(player, result))));
                })
                .build();
        bridge.sendForm(player, form);
    }

    private void openReviewDecision(@NotNull Player player, @NotNull MemoryEntry entry,
                                    @NotNull TranslationMemoryService memory) {
        ModalForm form = ModalForm.builder()
                .title(text(player, KEY + "review.title"))
                .content(userLine(player, KEY + "review.detail",
                        Map.of("original", entry.sourceText(), "suggestion", entry.targetText())))
                .button1(text(player, KEY + "review.approve"))
                .button2(text(player, KEY + "review.reject"))
                .validResultHandler(response -> {
                    boolean approve = response.clickedFirst();
                    CompletableFuture<Boolean> decision = approve
                            ? memory.approve(entry.id(), null).thenApply(Optional::isPresent)
                            : memory.reject(entry.id());
                    decision.thenAccept(done -> services.scheduler().runAtEntity(player, () -> SafeText
                            .msg(approve ? "lingo.review.approved" : "lingo.review.rejected")
                            .with("id", entry.id()).prefix().send(player)));
                })
                .build();
        bridge.sendForm(player, form);
    }

    private void confirmRemove(@NotNull Player player, @NotNull GlossaryTerm term) {
        ModalForm form = ModalForm.builder()
                .title(text(player, KEY + "glossary.title"))
                .content(userLine(player, KEY + "glossary.confirm", Map.of("term", term.term())))
                .button1(text(player, KEY + "glossary.remove"))
                .button2(text(player, KEY + "glossary.keep"))
                .validResultHandler(response -> {
                    if (response.clickedFirst()) {
                        services.glossary().removeById(term.id()).thenAccept(done -> services.scheduler()
                                .runAtEntity(player, () -> SafeText.msg("lingo.glossary.removed_one").prefix()
                                        .send(player)));
                    }
                })
                .build();
        bridge.sendForm(player, form);
    }

    private static @NotNull String text(@NotNull Player player, @NotNull String key) {
        return msg(key).toPlainString(player);
    }

    private static @NotNull String userLine(@NotNull Player player, @NotNull String key,
                                            @NotNull Map<String, String> userValues) {
        return PLAIN.serialize(SafeText.component(msg(key), player, userValues));
    }

    private static @NotNull MessageBuilder msg(@NotNull String key) {
        return SafeText.msg(key);
    }
}

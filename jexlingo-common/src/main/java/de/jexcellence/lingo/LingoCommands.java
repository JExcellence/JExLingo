package de.jexcellence.lingo;

import com.raindropcentral.commands.CommandFactory;
import com.raindropcentral.commands.v2.CommandHandler;
import com.raindropcentral.commands.v2.argument.ArgumentTypeRegistry;
import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.bedrock.LingoBedrockForms;
import de.jexcellence.lingo.chat.OnDemandTranslator;
import de.jexcellence.lingo.command.AdminServices;
import de.jexcellence.lingo.command.LingoAdminHandler;
import de.jexcellence.lingo.command.LingoArgumentTypes;
import de.jexcellence.lingo.command.LingoCommandHandler;
import de.jexcellence.lingo.command.R18nCommandMessages;
import de.jexcellence.lingo.command.Replies;
import de.jexcellence.lingo.command.StatusReport;
import de.jexcellence.lingo.learning.SuggestionService;
import de.jexcellence.lingo.learning.TrainingExportService;
import de.jexcellence.lingo.view.GlossaryView;
import de.jexcellence.lingo.view.LingoBaseView;
import de.jexcellence.lingo.view.LingoSettingsView;
import de.jexcellence.lingo.view.SuggestionReviewView;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Builds the menus and the {@code /lingo} command tree from {@code commands/lingo.yml} in the data folder, so
 * owners can rename the command, change aliases or permissions.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoCommands {

    /** The command tree file, relative to the data folder. */
    public static final String COMMAND_FILE = "commands/lingo.yml";

    private final JavaPlugin plugin;
    private final LingoCore core;
    private final PlatformScheduler scheduler;

    /**
     * Creates the builder.
     *
     * @param plugin    the plugin
     * @param core      the services
     * @param scheduler the platform scheduler
     */
    public LingoCommands(@NotNull JavaPlugin plugin, @NotNull LingoCore core, @NotNull PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.core = core;
        this.scheduler = scheduler;
    }

    /**
     * Registers menus and commands.
     *
     * @param worker      executor for file work
     * @param reload      reloads the plugin
     * @param suggestions suggestion service, or {@code null} in the free edition
     * @param forms       Bedrock forms, or {@code null} without Floodgate
     * @param onDemand    the translator behind the translate button
     */
    public void register(@NotNull Executor worker, @NotNull Runnable reload, @Nullable SuggestionService suggestions,
                         @Nullable LingoBedrockForms forms, @NotNull OnDemandTranslator onDemand) {
        LingoSettingsView settingsView = new LingoSettingsView(core.settings(), core.resolver(), scheduler);
        GlossaryView glossaryView = new GlossaryView(core.glossary(), core.edition(), scheduler);
        SuggestionReviewView reviewView = core.memory() == null ? null
                : new SuggestionReviewView(core.memory(), scheduler);
        registerViews(settingsView, glossaryView, reviewView);

        Replies replies = new Replies(scheduler);
        LingoCommandHandler player = new LingoCommandHandler(core.settings(), core.resolver(), settingsView, replies);
        player.setForms(forms);
        player.setSuggestions(suggestions);
        player.setOnDemand(onDemand);

        TrainingExportService export = core.memory() == null ? null
                : new TrainingExportService(core.memory(), plugin.getDataFolder().toPath().resolve("exports"), worker);
        StatusReport status = new StatusReport(core.edition(), core.gateway(), core.health(), core.pipeline());
        status.setLearning(core.memory(), core.phrases());
        LingoAdminHandler admin = new LingoAdminHandler(new AdminServices(core.pipeline(), core.glossary(),
                core.settings(), core.resolver(), core.memory(), export, reload), status, glossaryView, replies);
        admin.setReviewView(reviewView);
        admin.setForms(forms);

        Map<String, CommandHandler> handlers = new HashMap<>(player.handlerMap());
        handlers.putAll(admin.handlerMap());
        ArgumentTypeRegistry registry = LingoArgumentTypes.register(ArgumentTypeRegistry.defaults(), core.resolver());
        CommandFactory factory = new CommandFactory(plugin, this);
        factory.registerTree(new File(plugin.getDataFolder(), COMMAND_FILE), handlers, new R18nCommandMessages(),
                registry);
        factory.registerAllCommandsAndListeners();
    }

    private void registerViews(@NotNull LingoBaseView settingsView, @NotNull LingoBaseView glossaryView,
                               @Nullable LingoBaseView reviewView) {
        List<LingoBaseView> views = new ArrayList<>(List.of(settingsView, glossaryView));
        if (reviewView != null) {
            views.add(reviewView);
        }
        views.forEach(view -> Bukkit.getPluginManager().registerEvents(view, plugin));
    }
}

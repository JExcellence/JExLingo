package de.jexcellence.lingo;

import com.raindropcentral.commands.v2.CommandHandler;
import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.bedrock.BedrockFormBridge;
import de.jexcellence.lingo.bedrock.LingoBedrockForms;
import de.jexcellence.lingo.bedrock.StatsForm;
import de.jexcellence.lingo.command.AdminServices;
import de.jexcellence.lingo.command.LingoAdminHandler;
import de.jexcellence.lingo.command.LingoOpsHandler;
import de.jexcellence.lingo.command.Replies;
import de.jexcellence.lingo.command.StatusReport;
import de.jexcellence.lingo.learning.TrainingExportService;
import de.jexcellence.lingo.stats.StatsSources;
import de.jexcellence.lingo.view.GlossaryView;
import de.jexcellence.lingo.view.LingoBaseView;
import de.jexcellence.lingo.view.StatsView;
import de.jexcellence.lingo.view.SuggestionReviewView;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Builds the staff part of {@code /lingo}: glossary, review queue, statistics and operations, with their menus
 * and Bedrock forms.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class LingoStaffCommands {

    private final JavaPlugin plugin;
    private final LingoCore core;
    private final LingoOperations operations;
    private final PlatformScheduler scheduler;
    private final Replies replies;

    /**
     * Creates the builder.
     *
     * @param plugin     the plugin
     * @param core       the services
     * @param operations statistics and pause switch
     * @param scheduler  platform scheduler
     * @param replies    reply helper
     */
    public LingoStaffCommands(@NotNull JavaPlugin plugin, @NotNull LingoCore core,
                              @NotNull LingoOperations operations, @NotNull PlatformScheduler scheduler,
                              @NotNull Replies replies) {
        this.plugin = plugin;
        this.core = core;
        this.operations = operations;
        this.scheduler = scheduler;
        this.replies = replies;
    }

    /**
     * Registers the staff menus and returns the staff command handlers.
     *
     * @param worker  executor for file work
     * @param reload  reloads the plugin
     * @param forms   Bedrock forms, or {@code null} without Floodgate
     * @param bedrock the Floodgate bridge, or {@code null} without Floodgate
     * @return handlers by tree path
     */
    public @NotNull Map<String, CommandHandler> handlers(@NotNull Executor worker, @NotNull Runnable reload,
                                                        @Nullable LingoBedrockForms forms,
                                                        @Nullable BedrockFormBridge bedrock) {
        StatsSources sources = operations.sources();
        GlossaryView glossaryView = new GlossaryView(core.glossary(), core.edition(), scheduler);
        StatsView statsView = new StatsView(sources, core.edition(), scheduler);
        SuggestionReviewView reviewView = core.memory() == null ? null
                : new SuggestionReviewView(core.memory(), scheduler);
        registerViews(List.of(glossaryView, statsView), reviewView);

        TrainingExportService export = core.memory() == null ? null
                : new TrainingExportService(core.memory(), plugin.getDataFolder().toPath().resolve("exports"), worker);
        StatusReport status = new StatusReport(core.edition(), core.gateway(), core.health(), core.pipeline());
        status.setLearning(core.memory(), core.phrases());
        LingoAdminHandler admin = new LingoAdminHandler(new AdminServices(core.pipeline(), core.glossary(),
                core.settings(), core.resolver(), core.memory(), export, reload), status, glossaryView, replies);
        admin.setReviewView(reviewView);
        admin.setForms(forms);
        admin.setPhrases(core.phrases());

        LingoOpsHandler ops = new LingoOpsHandler(sources, core.edition(), core.settings(), core.resolver(),
                statsView, replies);
        if (bedrock != null && bedrock.isAvailable()) {
            ops.setStatsForm(new StatsForm(bedrock, sources, core.edition(), scheduler));
        }

        Map<String, CommandHandler> handlers = new HashMap<>(admin.handlerMap());
        handlers.putAll(ops.handlerMap());
        return handlers;
    }

    private void registerViews(@NotNull List<LingoBaseView> views, @Nullable LingoBaseView optional) {
        List<LingoBaseView> all = new ArrayList<>(views);
        if (optional != null) {
            all.add(optional);
        }
        all.forEach(view -> Bukkit.getPluginManager().registerEvents(view, plugin));
    }
}

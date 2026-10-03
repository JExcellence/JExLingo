package de.jexcellence.lingo;

import com.raindropcentral.commands.CommandFactory;
import com.raindropcentral.commands.v2.CommandHandler;
import com.raindropcentral.commands.v2.argument.ArgumentTypeRegistry;
import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.bedrock.BedrockFormBridge;
import de.jexcellence.lingo.bedrock.LingoBedrockForms;
import de.jexcellence.lingo.chat.OnDemandTranslator;
import de.jexcellence.lingo.command.LingoArgumentTypes;
import de.jexcellence.lingo.command.LingoCommandHandler;
import de.jexcellence.lingo.command.R18nCommandMessages;
import de.jexcellence.lingo.command.Replies;
import de.jexcellence.lingo.learning.SuggestionService;
import de.jexcellence.lingo.view.LingoSettingsView;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Builds the player menu and the {@code /lingo} command tree from {@code commands/lingo.yml} in the data folder,
 * so owners can rename the command, change aliases or permissions. Staff commands come from
 * {@link LingoStaffCommands}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoCommands {

    /** The command tree file, relative to the data folder. */
    public static final String COMMAND_FILE = "commands/lingo.yml";

    private final JavaPlugin plugin;
    private final LingoCore core;
    private final LingoOperations operations;
    private final PlatformScheduler scheduler;

    /**
     * Creates the builder.
     *
     * @param plugin     the plugin
     * @param core       the services
     * @param operations statistics and pause switch
     * @param scheduler  the platform scheduler
     */
    public LingoCommands(@NotNull JavaPlugin plugin, @NotNull LingoCore core, @NotNull LingoOperations operations,
                         @NotNull PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.core = core;
        this.operations = operations;
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
     * @param bedrock     the Floodgate bridge
     */
    public void register(@NotNull Executor worker, @NotNull Runnable reload, @Nullable SuggestionService suggestions,
                         @Nullable LingoBedrockForms forms, @NotNull OnDemandTranslator onDemand,
                         @NotNull BedrockFormBridge bedrock) {
        LingoSettingsView settingsView = new LingoSettingsView(core.settings(), core.resolver(), scheduler);
        Bukkit.getPluginManager().registerEvents(settingsView, plugin);

        Replies replies = new Replies(scheduler);
        LingoCommandHandler player = new LingoCommandHandler(core.settings(), core.resolver(), settingsView, replies);
        player.setForms(forms);
        player.setSuggestions(suggestions);
        player.setOnDemand(onDemand);

        Map<String, CommandHandler> handlers = new HashMap<>(player.handlerMap());
        handlers.putAll(new LingoStaffCommands(plugin, core, operations, scheduler, replies)
                .handlers(worker, reload, forms, bedrock));
        ArgumentTypeRegistry registry = LingoArgumentTypes.register(ArgumentTypeRegistry.defaults(), core.resolver());
        CommandFactory factory = new CommandFactory(plugin, this);
        factory.registerTree(new File(plugin.getDataFolder(), COMMAND_FILE), handlers, new R18nCommandMessages(),
                registry);
        factory.registerAllCommandsAndListeners();
    }
}

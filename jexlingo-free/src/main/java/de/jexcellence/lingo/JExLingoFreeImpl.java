package de.jexcellence.lingo;

import de.jexcellence.dependency.delegate.AbstractPluginDelegate;
import org.jetbrains.annotations.NotNull;

/**
 * Wires the Free edition into the shared {@link JExLingo} lifecycle.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class JExLingoFreeImpl extends AbstractPluginDelegate<JExLingoFree> {

    private JExLingo lingo;

    public JExLingoFreeImpl(@NotNull JExLingoFree plugin) {
        super(plugin);
    }

    @Override
    public void onLoad() {
        this.lingo = new JExLingo(getPlugin(), new LingoEdition.FreeEdition());
        this.lingo.onLoad();
    }

    @Override
    public void onEnable() {
        if (this.lingo == null) {
            getLogger().severe("JExLingo failed to load - disabling");
            getPlugin().getServer().getPluginManager().disablePlugin(getPlugin());
            return;
        }
        this.lingo.onEnable();
    }

    @Override
    public void onDisable() {
        if (this.lingo != null) {
            this.lingo.onDisable();
        }
    }
}

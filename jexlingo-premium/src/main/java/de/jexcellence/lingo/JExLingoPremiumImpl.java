package de.jexcellence.lingo;

import de.jexcellence.dependency.delegate.AbstractPluginDelegate;
import org.jetbrains.annotations.NotNull;

/**
 * Wires the Premium edition into the shared {@link JExLingo} lifecycle.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class JExLingoPremiumImpl extends AbstractPluginDelegate<JExLingoPremium> {

    private JExLingo lingo;

    public JExLingoPremiumImpl(@NotNull JExLingoPremium plugin) {
        super(plugin);
    }

    @Override
    public void onLoad() {
        this.lingo = new JExLingo(getPlugin(), new LingoEdition.PremiumEdition());
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

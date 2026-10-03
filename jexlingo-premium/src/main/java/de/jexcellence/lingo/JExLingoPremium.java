package de.jexcellence.lingo;

import de.jexcellence.dependency.JEDependency;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Bukkit entry point of the Premium edition. Provisions the runtime libraries first, then hands the lifecycle to
 * {@link JExLingoPremiumImpl}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class JExLingoPremium extends JavaPlugin {

    private JExLingoPremiumImpl implementation;

    @Override
    public void onLoad() {
        try {
            JEDependency.initializeWithRemapping(this, JExLingoPremium.class);
            this.implementation = new JExLingoPremiumImpl(this);
            this.implementation.onLoad();
        } catch (Exception exception) {
            this.getLogger().log(Level.SEVERE, "[JExLingo-Premium] Failed to load", exception);
            this.implementation = null;
        }
    }

    @Override
    public void onEnable() {
        if (this.implementation != null) {
            this.implementation.onEnable();
        }
    }

    @Override
    public void onDisable() {
        if (this.implementation != null) {
            this.implementation.onDisable();
        }
    }
}

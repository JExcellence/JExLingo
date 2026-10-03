package de.jexcellence.lingo;

import de.jexcellence.dependency.JEDependency;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Bukkit entry point of the Free edition. Provisions the runtime libraries first, then hands the lifecycle to
 * {@link JExLingoFreeImpl}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class JExLingoFree extends JavaPlugin {

    private JExLingoFreeImpl implementation;

    @Override
    public void onLoad() {
        try {
            JEDependency.initializeWithRemapping(this, JExLingoFree.class);
            this.implementation = new JExLingoFreeImpl(this);
            this.implementation.onLoad();
        } catch (Exception exception) {
            this.getLogger().log(Level.SEVERE, "[JExLingo-Free] Failed to load", exception);
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

package de.jexcellence.lingo.bedrock;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Soft bridge to the Floodgate API - detects Bedrock players and sends
 * Cumulus forms without a hard compile dependency. When Floodgate is not
 * installed the bridge degrades silently: {@link #isBedrockPlayer} always
 * returns {@code false} and {@link #sendForm} is a no-op.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class BedrockFormBridge {

    private static final Logger LOGGER = Logger.getLogger(BedrockFormBridge.class.getName());

    private final boolean available;
    private Object floodgateApi;

    public BedrockFormBridge() {
        boolean ok = false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = apiClass.getMethod("getInstance").invoke(null);
            ok = floodgateApi != null;
            if (ok) {
                LOGGER.log(Level.INFO, "[lingo] Floodgate detected - Bedrock forms enabled");
            }
        } catch (ClassNotFoundException ignored) {
            // Floodgate not installed
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, () -> "[lingo] Floodgate probe failed: " + e.getMessage());
        }
        this.available = ok;
    }

    /**
     * Whether Floodgate is installed and answered the probe.
     *
     * @return {@code true} when forms can be sent
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Whether a player joined through Geyser.
     *
     * @param player the player
     * @return {@code true} for Bedrock players
     */
    public boolean isBedrockPlayer(@NotNull Player player) {
        if (!available) {
            return false;
        }
        try {
            Object result = floodgateApi.getClass()
                    .getMethod("isFloodgatePlayer", UUID.class)
                    .invoke(floodgateApi, player.getUniqueId());
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, e, () -> "[lingo] Floodgate player check failed");
            return false;
        }
    }

    /**
     * Sends a Cumulus form to a Bedrock player. The {@code form} must be an
     * instance of {@code org.geysermc.cumulus.form.Form}.
     */
    public void sendForm(@NotNull Player player, @NotNull Object form) {
        if (!available) {
            return;
        }
        try {
            floodgateApi.getClass()
                    .getMethod("sendForm", UUID.class, form.getClass().getInterfaces()[0])
                    .invoke(floodgateApi, player.getUniqueId(), form);
        } catch (NoSuchMethodException e) {
            try {
                Class<?> formClass = Class.forName("org.geysermc.cumulus.form.Form");
                floodgateApi.getClass()
                        .getMethod("sendForm", UUID.class, formClass)
                        .invoke(floodgateApi, player.getUniqueId(), form);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, () -> "[lingo] Failed to send Bedrock form: " + ex.getMessage());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, () -> "[lingo] Failed to send Bedrock form: " + e.getMessage());
        }
    }
}

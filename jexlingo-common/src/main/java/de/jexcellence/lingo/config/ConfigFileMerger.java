package de.jexcellence.lingo.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * {@code config.yml} is copied once and never overwritten, so options added by an update would stay invisible. On
 * every start this writes each key the bundled file has and the live file lacks into the live file, with the
 * bundled comment, and raises {@code config-version}. Existing values and the owner's own comments stay untouched;
 * a timestamped backup is written before the file changes. The plugin works without this step because every key
 * has a default in code; the merge only makes new options visible.
 *
 * @author JExcellence
 * @since 0.2.0
 */
public final class ConfigFileMerger {

    private static final String VERSION_KEY = "config-version";

    private ConfigFileMerger() {
    }

    /**
     * Adds the missing bundled keys to a live YAML file.
     *
     * @param plugin       the plugin
     * @param resourcePath the bundled resource, also the path in the data folder
     */
    public static void addMissingKeys(@NotNull JavaPlugin plugin, @NotNull String resourcePath) {
        File live = new File(plugin.getDataFolder(), resourcePath.replace('/', File.separatorChar));
        if (!live.isFile()) {
            return;
        }
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return;
            }
            YamlConfiguration bundled = load(in);
            YamlConfiguration current = new YamlConfiguration();
            current.options().parseComments(true);
            current.load(live);
            List<String> added = merge(bundled, current);
            if (added.isEmpty()) {
                return;
            }
            File backup = new File(live.getPath() + ".bak-" + System.currentTimeMillis());
            Files.copy(live.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            current.save(live);
            plugin.getLogger().log(Level.INFO, () -> "Added new options " + added + " to " + resourcePath
                    + " (backup: " + backup.getName() + ")");
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.WARNING, ex, () -> "Could not update " + resourcePath);
        }
    }

    /**
     * Copies missing leaf keys with their comments and raises the version; package-visible for tests.
     *
     * @param bundled the bundled file
     * @param current the live file
     * @return the added keys
     */
    static @NotNull List<String> merge(@NotNull YamlConfiguration bundled, @NotNull YamlConfiguration current) {
        List<String> added = new ArrayList<>();
        for (String key : bundled.getKeys(true)) {
            if (!bundled.isConfigurationSection(key) && !current.contains(key) && !VERSION_KEY.equals(key)) {
                current.set(key, bundled.get(key));
                current.setComments(key, bundled.getComments(key));
                added.add(key);
            }
        }
        int bundledVersion = bundled.getInt(VERSION_KEY, 1);
        if (!added.isEmpty() || current.getInt(VERSION_KEY, 1) < bundledVersion) {
            current.set(VERSION_KEY, Math.max(bundledVersion, current.getInt(VERSION_KEY, 1)));
        }
        return added;
    }

    private static @NotNull YamlConfiguration load(@NotNull InputStream in) throws IOException {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().parseComments(true);
            yaml.load(reader);
            return yaml;
        } catch (InvalidConfigurationException ex) {
            throw new IOException("The bundled file is invalid", ex);
        }
    }
}

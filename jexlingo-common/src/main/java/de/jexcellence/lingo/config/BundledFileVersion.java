package de.jexcellence.lingo.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces a copied default file when the plugin ships a newer layout. Bundled command trees and translation files
 * carry a {@code # file-version: N} line; when the copy in the data folder has a lower number (or none), it is
 * backed up as {@code .bak-<time>} and replaced, so changed arguments and texts reach existing servers. Files with the
 * same or a higher number are left alone and only get new keys from the mergers.
 *
 * @author JExcellence
 * @since 0.4.1
 */
public final class BundledFileVersion {

    private static final Pattern VERSION = Pattern.compile("(?m)^#\\s*file-version:\\s*(\\d+)\\s*$");

    private BundledFileVersion() {
    }

    /**
     * Replaces the live file when the bundled one is newer.
     *
     * @param plugin       the plugin
     * @param resourcePath path inside the jar and the data folder
     * @return whether the file was replaced
     */
    public static boolean replaceIfOutdated(@NotNull JavaPlugin plugin, @NotNull String resourcePath) {
        File live = new File(plugin.getDataFolder(), resourcePath.replace('/', File.separatorChar));
        if (!live.isFile()) {
            return false;
        }
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return false;
            }
            byte[] bundled = in.readAllBytes();
            int bundledVersion = versionOf(new String(bundled, StandardCharsets.UTF_8));
            int liveVersion = versionOf(Files.readString(live.toPath(), StandardCharsets.UTF_8));
            if (bundledVersion <= liveVersion) {
                return false;
            }
            File backup = new File(live.getPath() + ".bak-" + System.currentTimeMillis());
            Files.copy(live.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.write(live.toPath(), bundled);
            plugin.getLogger().log(Level.INFO, () -> "Replaced " + resourcePath + " with file version "
                    + bundledVersion + " (was " + liveVersion + ", backup: " + backup.getName() + ")");
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, ex, () -> "Could not update " + resourcePath);
            return false;
        }
    }

    /**
     * Reads the file version of a text.
     *
     * @param content the file content
     * @return the version, or {@code 0} without a version line
     */
    static int versionOf(@NotNull String content) {
        Matcher matcher = VERSION.matcher(content);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}

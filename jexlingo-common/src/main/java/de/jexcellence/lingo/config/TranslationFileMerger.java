package de.jexcellence.lingo.config;

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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Translation files are copied into the data folder once, so an update never reaches them and new menu texts show
 * as raw keys. Before the translation system loads, this writes every key the bundled file has and the server copy
 * lacks into the server copy. Existing texts, including an owner's own wording, are never changed; a timestamped
 * backup is written before a file changes.
 */
public final class TranslationFileMerger {

    private static final String DIRECTORY = "translations";
    private static final String FALLBACK_LOCALE = "en_US";
    private static final String YML = ".yml";
    private static final Pattern LOCALE_FILE = Pattern.compile("[a-z]{2,3}_[A-Z]{2}\\.yml");
    private static final Pattern BOOLEAN_KEY = Pattern.compile(
            "(?m)^([ \\t]*)(on|off|yes|no|y|n|true|false|On|Off|Yes|No|True|False|ON|OFF|YES|NO|TRUE|FALSE)(:(?:[ \\t]|$))");

    private TranslationFileMerger() {
    }

    /**
     * Finds the language files in a translations folder: every {@code xx_YY.yml}, so a server owner can add a
     * language by dropping in a file. {@code config.yml} and backups are skipped.
     *
     * @param directory the translations folder
     * @return the locales, sorted, for example {@code [de_DE, en_US, fr_FR]}
     */
    public static @NotNull List<String> discoverLocales(@NotNull File directory) {
        String[] names = directory.list((dir, name) -> LOCALE_FILE.matcher(name).matches());
        if (names == null) {
            return List.of();
        }
        return Arrays.stream(names).map(name -> name.substring(0, name.length() - YML.length())).sorted().toList();
    }

    /** Adds the missing bundled keys to each existing {@code translations/<locale>.yml}. */
    public static void addMissingKeys(@NotNull JavaPlugin plugin, @NotNull List<String> locales) {
        for (String locale : locales) {
            mergeLocale(plugin, locale);
        }
    }

    private static void mergeLocale(@NotNull JavaPlugin plugin, @NotNull String locale) {
        String resourcePath = DIRECTORY + "/" + locale + ".yml";
        File live = new File(new File(plugin.getDataFolder(), DIRECTORY), locale + ".yml");
        if (!live.isFile()) {
            return;
        }
        quoteBooleanKeys(plugin, live);
        InputStream own = plugin.getResource(resourcePath);
        // A language the jar does not ship (an owner's fr_FR.yml) is completed from the English file, so every
        // text exists and the English lines show what is still to translate.
        try (InputStream in = own != null ? own : plugin.getResource(DIRECTORY + "/" + FALLBACK_LOCALE + ".yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration bundled = load(in);
            YamlConfiguration current = YamlConfiguration.loadConfiguration(live);
            List<String> added = missingLeaves(bundled, current);
            if (added.isEmpty()) {
                return;
            }
            for (String key : added) {
                current.set(key, bundled.get(key));
            }
            File backup = new File(live.getPath() + ".bak-" + System.currentTimeMillis());
            Files.copy(live.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            current.save(live);
            final int count = added.size();
            plugin.getLogger().log(Level.INFO, () -> "Added " + count + " new translation keys to " + resourcePath
                    + " (backup: " + backup.getName() + ")");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, ex, () -> "Could not update " + resourcePath);
        }
    }

    /**
     * YAML reads unquoted keys such as {@code on}, {@code off}, {@code yes} or {@code no} as booleans, and one such
     * key makes the whole translation file fail to load. Quotes them in place so the file loads again.
     */
    private static void quoteBooleanKeys(@NotNull JavaPlugin plugin, @NotNull File live) {
        try {
            String raw = Files.readString(live.toPath(), StandardCharsets.UTF_8);
            String fixed = quoteBooleanKeys(raw);
            if (!fixed.equals(raw)) {
                File backup = new File(live.getPath() + ".bak-" + System.currentTimeMillis());
                Files.copy(live.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(live.toPath(), fixed, StandardCharsets.UTF_8);
                plugin.getLogger().log(Level.WARNING, () -> "Quoted boolean-like keys in " + live.getName()
                        + " so it loads again (backup: " + backup.getName() + ")");
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, ex, () -> "Could not check " + live.getName());
        }
    }

    /** Quotes unquoted boolean-like mapping keys; package-visible for tests. */
    static @NotNull String quoteBooleanKeys(@NotNull String yaml) {
        return BOOLEAN_KEY.matcher(yaml).replaceAll("$1'$2'$3");
    }

    private static @NotNull YamlConfiguration load(@NotNull InputStream in) throws IOException {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    /** Leaf keys of {@code bundled} that {@code current} has no value for; package-visible for tests. */
    static @NotNull List<String> missingLeaves(@NotNull YamlConfiguration bundled, @NotNull YamlConfiguration current) {
        List<String> missing = new ArrayList<>();
        for (String key : bundled.getKeys(true)) {
            if (!bundled.isConfigurationSection(key) && !current.contains(key)) {
                missing.add(key);
            }
        }
        return missing;
    }
}

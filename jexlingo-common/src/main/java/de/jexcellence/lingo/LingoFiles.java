package de.jexcellence.lingo;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.config.BundledFileVersion;
import de.jexcellence.lingo.config.ConfigFileMerger;
import de.jexcellence.lingo.config.LanguageSettings;
import de.jexcellence.lingo.config.LingoConfig;
import de.jexcellence.lingo.config.LingoConfigLoader;
import de.jexcellence.lingo.config.TranslationFileMerger;
import de.jexcellence.lingo.glossary.GlossarySeed;
import de.jexcellence.lingo.glossary.GlossaryTerm;
import de.jexcellence.lingo.pipeline.SlangDictionary;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The files in the data folder: copies the defaults once, merges new keys of updates into existing files and reads
 * config, glossary seed and slang dictionary.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class LingoFiles {

    /** Locale every text exists in. */
    public static final String DEFAULT_LOCALE = "en_US";

    /** Further locales shipped in the jar. */
    public static final String[] EXTRA_LOCALES = {"de_DE"};

    private static final String CONFIG_FILE = "config.yml";
    private static final String GLOSSARY_FILE = "glossary.yml";
    private static final String SLANG_FILE = "slang.yml";
    private static final String DATABASE_FILE = "database/hibernate.properties";
    private static final String TRANSLATIONS = "translations";

    private final JavaPlugin plugin;
    private final LingoEdition edition;
    private final Logger logger;
    private List<String> extraLocales = List.of(EXTRA_LOCALES);

    /**
     * Creates the file helper.
     *
     * @param plugin  the plugin
     * @param edition the edition, for the language limit
     * @param logger  the plugin logger
     */
    public LingoFiles(@NotNull JavaPlugin plugin, @NotNull LingoEdition edition, @NotNull Logger logger) {
        this.plugin = plugin;
        this.edition = edition;
        this.logger = logger;
    }

    /** Copies {@code config.yml} once and adds options of newer versions to it. */
    public void prepareConfig() {
        saveDefault(CONFIG_FILE);
        ConfigFileMerger.addMissingKeys(plugin, CONFIG_FILE);
    }

    /** Copies the other default files once and merges new translation keys; JExCommand keeps the command tree. */
    public void prepareRest() {
        saveDefault(DATABASE_FILE);
        saveDefault(GLOSSARY_FILE);
        saveDefault(SLANG_FILE);
        List<String> bundled = new ArrayList<>(List.of(EXTRA_LOCALES));
        bundled.addFirst(DEFAULT_LOCALE);
        bundled.forEach(locale -> BundledFileVersion.replaceIfOutdated(plugin, TRANSLATIONS + "/" + locale + ".yml"));
        List<String> added = TranslationFileMerger.discoverLocales(new File(plugin.getDataFolder(), TRANSLATIONS))
                .stream()
                .filter(locale -> !bundled.contains(locale))
                .toList();
        if (!added.isEmpty()) {
            logger.log(Level.INFO, () -> "Using your own language files too: " + added
                    + ". Missing texts are added in English; translate them in the file.");
        }
        List<String> all = new ArrayList<>(bundled);
        all.addAll(added);
        TranslationFileMerger.addMissingKeys(plugin, all);
        extraLocales = all.stream().filter(locale -> !locale.equals(DEFAULT_LOCALE)).toList();
    }

    /**
     * Returns every language of JExLingo's own texts except English: the bundled ones and every {@code xx_YY.yml}
     * the server owner put into {@code translations/}. Filled by {@link #prepareRest()}.
     *
     * @return the locales
     */
    public @NotNull String[] extraLocales() {
        return extraLocales.toArray(String[]::new);
    }

    /**
     * Reads {@code config.yml} with the edition's language limit applied.
     *
     * @return the config
     */
    public @NotNull LingoConfig readConfig() {
        File file = new File(plugin.getDataFolder(), CONFIG_FILE);
        LingoConfigLoader.LoadResult result = LingoConfigLoader.load(YamlConfiguration.loadConfiguration(file),
                System.getenv());
        result.warnings().forEach(warning -> logger.log(Level.WARNING, () -> "config.yml: " + warning));
        LingoConfig loaded = result.config();
        List<LanguageCode> allowed = edition.limitLanguages(loaded.languages().enabled());
        if (allowed.size() == loaded.languages().enabled().size()) {
            return loaded;
        }
        final int max = edition.maxLanguages();
        logger.log(Level.WARNING, () -> "The Free edition translates up to " + max + " languages; only "
                + allowed + " are used. JExLingo Premium has no language limit.");
        LanguageCode fallback = loaded.languages().fallback();
        LanguageSettings limited = new LanguageSettings(allowed,
                allowed.contains(fallback) ? fallback : allowed.getFirst());
        return new LingoConfig(loaded.provider(), limited, loaded.detection(), loaded.chat(), loaded.storage(),
                loaded.learning(), loaded.bedrock());
    }

    /**
     * Reads the default glossary terms.
     *
     * @return the terms
     */
    public @NotNull List<GlossaryTerm> readGlossarySeed() {
        File file = new File(plugin.getDataFolder(), GLOSSARY_FILE);
        return GlossarySeed.parse(YamlConfiguration.loadConfiguration(file),
                warning -> logger.log(Level.WARNING, warning));
    }

    /**
     * Reads the slang dictionary.
     *
     * @return the dictionary, empty when the file is missing
     */
    public @NotNull SlangDictionary readSlang() {
        File file = new File(plugin.getDataFolder(), SLANG_FILE);
        if (!file.isFile()) {
            return SlangDictionary.EMPTY;
        }
        return SlangDictionary.parse(YamlConfiguration.loadConfiguration(file),
                warning -> logger.log(Level.WARNING, warning));
    }

    private void saveDefault(@NotNull String resourcePath) {
        File target = new File(plugin.getDataFolder(), resourcePath.replace('/', File.separatorChar));
        if (!target.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }
}

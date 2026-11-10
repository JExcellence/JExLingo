package de.jexcellence.lingo.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks every bundled language file against {@code en_US.yml}: the same keys, the same {@code {placeholders}} and
 * the same MiniMessage tags in each text. Translators run it with
 * {@code ./gradlew :JExLingo:jexlingo-common:test --tests "*TranslationFilesTest"}; every problem is listed with its
 * file and key.
 */
class TranslationFilesTest {

    private static final File DIRECTORY = new File("src/main/resources/translations");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z0-9_-]+}");
    private static final Pattern TAG = Pattern.compile("</?[#A-Za-z_][^<>]*>");

    @Test
    void everyLanguageFileMatchesTheEnglishOne() {
        YamlConfiguration english = YamlConfiguration.loadConfiguration(new File(DIRECTORY, "en_US.yml"));
        List<String> locales = TranslationFileMerger.discoverLocales(DIRECTORY);
        assertTrue(locales.contains("en_US"), "en_US.yml is bundled");

        List<String> problems = new ArrayList<>();
        for (String locale : locales) {
            if (!locale.equals("en_US")) {
                YamlConfiguration other = YamlConfiguration.loadConfiguration(new File(DIRECTORY, locale + ".yml"));
                problems.addAll(compare(locale, english, other));
            }
        }

        assertTrue(problems.isEmpty(), () -> problems.size() + " problems:\n" + String.join("\n", problems));
    }

    @Test
    void theCheckFindsMissingKeysPlaceholdersAndTags() {
        YamlConfiguration english = new YamlConfiguration();
        english.set("a", "<red>Hello {name}</red>");
        english.set("b", "Bye");
        YamlConfiguration broken = new YamlConfiguration();
        broken.set("a", "Hallo {player}");

        List<String> problems = compare("xx_XX", english, broken);

        assertFalse(problems.isEmpty());
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("missing") && problem.contains("b")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("placeholders")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("tags")));
    }

    static List<String> compare(String locale, YamlConfiguration english, YamlConfiguration other) {
        List<String> problems = new ArrayList<>();
        for (String key : english.getKeys(true)) {
            if (english.isConfigurationSection(key)) {
                continue;
            }
            if (!other.contains(key)) {
                problems.add(locale + ": missing " + key);
            } else {
                String source = english.getString(key, "");
                String translated = other.getString(key, "");
                if (!counts(PLACEHOLDER, source).equals(counts(PLACEHOLDER, translated))) {
                    problems.add(locale + ": " + key + " has other placeholders than English "
                            + counts(PLACEHOLDER, source).keySet());
                }
                if (!counts(TAG, source).equals(counts(TAG, translated))) {
                    problems.add(locale + ": " + key + " has other tags than English");
                }
            }
        }
        for (String key : other.getKeys(true)) {
            if (!other.isConfigurationSection(key) && !english.contains(key)) {
                problems.add(locale + ": " + key + " is not in en_US.yml");
            }
        }
        return problems;
    }

    private static Map<String, Long> counts(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found.stream().collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
    }
}

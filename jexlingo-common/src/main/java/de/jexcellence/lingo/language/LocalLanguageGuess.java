package de.jexcellence.lingo.language;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.config.LanguageSettings;
import de.jexcellence.lingo.pipeline.FormatCodes;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Guesses the language of a chat line without a provider call, from common function words and special letters.
 * Takes no time, so it runs for every line: a German player with an English client who writes German is translated
 * from German.
 *
 * <p>Knows German, English, French, Spanish, Italian, Dutch, Portuguese and Polish. A word or letter only counts when
 * exactly one of the server's enabled languages claims it, so shared words ({@code en}, {@code no}, {@code ne},
 * {@code do}) decide nothing on a server where both languages are on, but still help where only one of them is.
 * Words that look like function words in one language but are ordinary words in another ({@code die}, {@code was},
 * {@code man}, {@code im}, {@code will}) are left out entirely. Without a clear winner the caller keeps the player's
 * own language.
 *
 * @author JExcellence
 * @since 0.4.1
 */
public final class LocalLanguageGuess {

    private static final Pattern WORD = Pattern.compile("[^\\p{L}']+");
    private static final int HIT = 2;

    private static final List<Profile> PROFILES = List.of(
            new Profile(LanguageCode.of("de"), "äöüß", Set.of(
                    "ich", "du", "er", "sie", "wir", "ihr", "mich", "dich", "mir", "dir", "uns", "euch",
                    "der", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer",
                    "und", "oder", "aber", "nicht", "kein", "keine", "auch", "noch", "schon", "nur", "mal", "doch",
                    "ist", "sind", "bin", "bist", "habe", "hast", "haben", "wird", "werden", "kann", "kannst",
                    "muss", "willst", "gibt", "gibts", "mach", "machen", "geht", "gehts",
                    "wer", "wie", "wo", "warum", "wann", "welche", "jetzt", "heute", "morgen", "hier", "dort",
                    "mit", "von", "zu", "zum", "zur", "auf", "aus", "bei", "nach", "für", "über", "unter",
                    "ja", "nein", "nee", "danke", "bitte", "hallo", "moin", "servus", "gut", "sehr", "viel",
                    "alle", "alles", "nichts", "etwas", "mein", "dein", "sein", "wenn", "dann", "weil", "dass",
                    "es", "hat", "hab", "nen", "ne", "denn", "wieso", "weiß", "weiss", "jemand", "leute", "sich",
                    "einfach", "vielleicht", "vllt", "komm", "kommt", "brauch", "brauche", "suche", "kaufe",
                    "verkaufe", "geh", "gehe", "isses", "an", "am")),
            new Profile(LanguageCode.of("en"), "", Set.of(
                    "i", "you", "he", "she", "we", "they", "me", "him", "her", "us", "them", "my", "your",
                    "the", "a", "an", "and", "or", "but", "not", "no", "yes", "yeah", "too", "only", "just",
                    "is", "are", "am", "be", "been", "have", "has", "do", "does", "did", "can", "could",
                    "would", "should", "it", "this", "that", "these", "those", "there", "here",
                    "who", "what", "where", "why", "when", "how", "which", "now", "today", "tomorrow",
                    "with", "of", "to", "for", "from", "on", "at", "by", "about", "into",
                    "thanks", "thank", "please", "hello", "hi", "hey", "good", "very", "much", "all",
                    "nothing", "something", "if", "then", "because", "anyone", "someone", "lets", "let's",
                    "i'm", "it's", "don't", "dont", "can't", "cant", "isn't", "what's", "you're", "want", "need",
                    "know", "think", "going", "anybody", "everyone", "guys", "pls", "plz", "thx")),
            new Profile(LanguageCode.of("fr"), "çœêëîïûùâè", Set.of(
                    "je", "tu", "il", "elle", "nous", "vous", "ils", "elles", "le", "la", "les", "un", "une",
                    "des", "du", "et", "ou", "mais", "pas", "ne", "oui", "non", "est", "sont", "suis", "es",
                    "avec", "pour", "dans", "sur", "qui", "quoi", "que", "comment", "pourquoi", "quand", "ici",
                    "bonjour", "salut", "merci", "stp", "svp", "bien", "très", "tout", "rien", "quelqu'un", "ça",
                    "c'est", "j'ai", "mon", "ton", "mes", "tes", "veux", "peux", "fait", "aussi", "encore",
                    "déjà", "en")),
            new Profile(LanguageCode.of("es"), "ñ¿¡áéíóú", Set.of(
                    "yo", "tú", "él", "ella", "nosotros", "ustedes", "ellos", "el", "los", "las", "una", "unos",
                    "y", "pero", "sí", "no", "es", "está", "están", "estoy", "soy", "con", "para", "por", "que",
                    "qué", "cómo", "porque", "cuando", "dónde", "aquí", "hola", "gracias", "bien", "muy", "todo",
                    "nada", "alguien", "algo", "mi", "tu", "su", "quiero", "puedo", "tengo", "hay", "también",
                    "ya", "vale", "jaja", "pues", "eso", "esto", "del", "al", "en")),
            new Profile(LanguageCode.of("it"), "àèéìòù", Set.of(
                    "io", "lui", "lei", "noi", "voi", "loro", "il", "lo", "gli", "uno", "una", "e", "ma", "non",
                    "sì", "è", "sono", "sei", "con", "per", "che", "chi", "cosa", "come", "perché", "quando",
                    "dove", "qui", "ciao", "grazie", "bene", "molto", "tutto", "niente", "qualcuno", "qualcosa",
                    "mio", "tuo", "voglio", "posso", "ho", "hai", "anche", "già", "ancora", "della", "nel",
                    "dei", "delle", "in")),
            new Profile(LanguageCode.of("nl"), "", Set.of(
                    "ik", "jij", "je", "hij", "zij", "wij", "jullie", "het", "een", "en", "maar", "niet", "geen",
                    "ja", "nee", "is", "zijn", "ben", "bent", "heb", "hebt", "met", "voor", "van", "op", "wie",
                    "wat", "hoe", "waarom", "wanneer", "waar", "hier", "hallo", "hoi", "dank", "bedankt", "goed",
                    "heel", "alles", "niets", "iemand", "iets", "mijn", "jouw", "wil", "kan", "ook", "nog", "dat",
                    "deze", "gewoon", "effe", "even")),
            new Profile(LanguageCode.of("pt"), "ãõçáâêôàéíóú", Set.of(
                    "eu", "você", "voce", "ele", "ela", "nós", "eles", "elas", "o", "os", "as", "um", "uma", "e",
                    "mas", "não", "nao", "sim", "é", "são", "sou", "estou", "com", "para", "por", "que", "quem",
                    "como", "porque", "quando", "onde", "aqui", "olá", "oi", "obrigado", "obrigada", "bem",
                    "muito", "tudo", "nada", "alguém", "algo", "meu", "minha", "quero", "posso", "tenho", "tem",
                    "também", "já", "ainda", "do", "da", "dos", "das", "no", "na", "vc", "kkk")),
            new Profile(LanguageCode.of("pl"), "ąćęłńóśźż", Set.of(
                    "ja", "ty", "on", "ona", "my", "wy", "oni", "i", "a", "ale", "nie", "tak", "jest", "są",
                    "jestem", "z", "w", "na", "do", "dla", "kto", "co", "jak", "dlaczego", "kiedy", "gdzie", "tu",
                    "tutaj", "cześć", "dzięki", "dziękuję", "dobrze", "bardzo", "wszystko", "nic", "ktoś", "coś",
                    "mój", "twój", "chcę", "mogę", "mam", "masz", "też", "już", "jeszcze", "siema", "się")));

    /**
     * Function words and special letters of one language.
     *
     * @param language the language
     * @param letters  letters typical for it, empty for none
     * @param words    common function and chat words, lower case
     */
    private record Profile(@NotNull LanguageCode language, @NotNull String letters, @NotNull Set<String> words) {
    }

    private LocalLanguageGuess() {
    }

    /**
     * Returns whether a word is one of the common function words this guess knows in any language.
     *
     * @param word the word, any case
     * @return whether it is a common word
     */
    public static boolean isCommonWord(@NotNull String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        return PROFILES.stream().anyMatch(profile -> profile.words().contains(lower));
    }

    /**
     * Guesses the language of a line.
     *
     * @param text      the chat line, codes allowed
     * @param languages enabled languages
     * @return the language with clearly the most hits, or empty when unsure
     */
    public static @NotNull Optional<LanguageCode> guess(@NotNull String text, @NotNull LanguageSettings languages) {
        String lower = FormatCodes.strip(text).toLowerCase(Locale.ROOT);
        List<Profile> enabled = PROFILES.stream().filter(profile -> languages.isEnabled(profile.language())).toList();
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        Map<LanguageCode, Integer> scores = new HashMap<>();
        for (String word : WORD.split(lower)) {
            soleOwner(enabled, profile -> profile.words().contains(word))
                    .ifPresent(language -> scores.merge(language, HIT, Integer::sum));
        }
        letterBonus(lower, enabled).forEach(language -> scores.merge(language, HIT, Integer::sum));
        return winner(scores);
    }

    /** The language that alone claims something among the enabled ones. */
    private static @NotNull Optional<LanguageCode> soleOwner(@NotNull List<Profile> enabled,
                                                             @NotNull Predicate<Profile> claims) {
        LanguageCode owner = null;
        for (Profile profile : enabled) {
            if (claims.test(profile)) {
                if (owner != null) {
                    return Optional.empty();
                }
                owner = profile.language();
            }
        }
        return Optional.ofNullable(owner);
    }

    /** Each language gets one bonus when the line has a letter that only it claims among the enabled ones. */
    private static @NotNull Set<LanguageCode> letterBonus(@NotNull String lower, @NotNull List<Profile> enabled) {
        Set<LanguageCode> bonus = new HashSet<>();
        lower.codePoints().distinct().forEach(letter -> soleOwner(enabled,
                profile -> profile.letters().indexOf(letter) >= 0).ifPresent(bonus::add));
        return bonus;
    }

    private static @NotNull Optional<LanguageCode> winner(@NotNull Map<LanguageCode, Integer> scores) {
        LanguageCode best = null;
        int bestScore = 0;
        int secondScore = 0;
        for (Map.Entry<LanguageCode, Integer> entry : scores.entrySet()) {
            int score = entry.getValue();
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = entry.getKey();
            } else {
                secondScore = Math.max(secondScore, score);
            }
        }
        return bestScore > 0 && bestScore >= secondScore * 2 ? Optional.ofNullable(best) : Optional.empty();
    }
}

package de.jexcellence.lingo.glossary;

import de.jexcellence.lingo.LingoEdition;
import de.jexcellence.lingo.database.entity.GlossaryTermEntity;
import de.jexcellence.lingo.database.repository.GlossaryTermRepository;
import de.jexcellence.lingo.pipeline.LanguagePair;
import de.jexcellence.lingo.pipeline.MaskRules;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The server's own vocabulary. Keep terms ({@code OneBlock}, rank names, {@code gg}) are never translated; force
 * terms always get the same translation. The glossary is small, so it lives in memory and every change is written
 * through to the database. The prepared rules per language pair are cached until the next change.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class GlossaryService {

    /** Outcome of adding a term. */
    public enum AddResult {
        /** The term was stored. */
        ADDED,
        /** A term with the same text and languages exists. */
        DUPLICATE,
        /** The edition's glossary limit is reached. */
        LIMIT_REACHED,
        /** A force term needs a replacement. */
        MISSING_REPLACEMENT
    }

    /**
     * Outcome of an import from {@code glossary.yml}.
     *
     * @param toAdd      terms that are new and fit the limit
     * @param duplicates terms already in the glossary
     * @param overLimit  new terms left out because of the edition's limit
     */
    public record ImportResult(@NotNull List<GlossaryTerm> toAdd, int duplicates, int overLimit) {

        /**
         * Copies the list.
         *
         * @param toAdd      terms that are new and fit the limit
         * @param duplicates terms already in the glossary
         * @param overLimit  new terms left out because of the limit
         */
        public ImportResult {
            toAdd = List.copyOf(toAdd);
        }
    }

    /**
     * Decides which candidates an import adds: new terms in file order until the limit, skipping terms whose text
     * and languages are already in the glossary or earlier in the file.
     *
     * @param existing   the current glossary
     * @param candidates terms from the file
     * @param maxTerms   the edition's limit, 0 for none
     * @return the plan
     */
    public static @NotNull ImportResult plan(@NotNull List<GlossaryTerm> existing,
                                             @NotNull List<GlossaryTerm> candidates, int maxTerms) {
        Set<String> known = new HashSet<>();
        existing.forEach(term -> known.add(identity(term)));
        List<GlossaryTerm> toAdd = new ArrayList<>();
        int duplicates = 0;
        int overLimit = 0;
        for (GlossaryTerm candidate : candidates) {
            if (!known.add(identity(candidate))) {
                duplicates++;
            } else if (maxTerms > 0 && existing.size() + toAdd.size() >= maxTerms) {
                overLimit++;
            } else {
                toAdd.add(candidate);
            }
        }
        return new ImportResult(toAdd, duplicates, overLimit);
    }

    private static @NotNull String identity(@NotNull GlossaryTerm term) {
        return term.term().toLowerCase(Locale.ROOT) + '\u0000' + term.source() + '\u0000' + term.target();
    }

    private final GlossaryTermRepository repository;
    private final LingoEdition edition;
    private final Executor worker;
    private final Logger logger;
    private final AtomicReference<List<GlossaryTerm>> terms = new AtomicReference<>(List.of());
    private final Map<LanguagePair, MaskRules> rules = new ConcurrentHashMap<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    /**
     * Creates the service.
     *
     * @param repository the glossary repository
     * @param edition    the edition, for the term limit
     * @param worker     executor for database work
     * @param logger     the plugin logger
     */
    public GlossaryService(@NotNull GlossaryTermRepository repository, @NotNull LingoEdition edition,
                           @NotNull Executor worker, @NotNull Logger logger) {
        this.repository = repository;
        this.edition = edition;
        this.worker = worker;
        this.logger = logger;
    }

    /**
     * Registers a callback for glossary edits (add and remove), for example to clear the translation cache. Loading
     * the glossary at start does not call it.
     *
     * @param listener the callback
     */
    public void onChange(@NotNull Runnable listener) {
        changeListeners.add(listener);
    }

    /**
     * Loads every term; seeds the defaults on the very first start.
     *
     * @param seed the default terms from {@code glossary.yml}
     * @return completes when the glossary is in memory
     */
    public @NotNull CompletableFuture<Void> load(@NotNull List<GlossaryTerm> seed) {
        return CompletableFuture.runAsync(() -> {
            if (repository.count() == 0L && !seed.isEmpty()) {
                List<GlossaryTerm> allowed = limit(seed);
                repository.createAll(allowed.stream().map(GlossaryTermEntity::new).toList());
                final int count = allowed.size();
                logger.log(Level.INFO, () -> "Seeded the glossary with " + count + " default terms.");
            }
            install(repository.findAll().stream().map(GlossaryTermEntity::toTerm).toList());
        }, worker).exceptionally(error -> {
            logger.log(Level.WARNING, error, () -> "Could not load the glossary");
            return null;
        });
    }

    /**
     * The prepared rules for a language pair.
     *
     * @param pair the language pair
     * @return keep and force terms that apply to it
     */
    public @NotNull MaskRules rulesFor(@NotNull LanguagePair pair) {
        return rules.computeIfAbsent(pair, this::buildRules);
    }

    /**
     * Returns every term, sorted by text.
     *
     * @return every term, sorted by text
     */
    public @NotNull List<GlossaryTerm> terms() {
        return terms.get();
    }

    /**
     * Adds a term.
     *
     * @param term the term (id ignored)
     * @return the outcome
     */
    public @NotNull CompletableFuture<AddResult> add(@NotNull GlossaryTerm term) {
        if (term.mode() == GlossaryMode.FORCE && !term.forces()) {
            return CompletableFuture.completedFuture(AddResult.MISSING_REPLACEMENT);
        }
        if (find(term).isPresent()) {
            return CompletableFuture.completedFuture(AddResult.DUPLICATE);
        }
        if (!edition.acceptsGlossaryTerm(terms.get().size())) {
            return CompletableFuture.completedFuture(AddResult.LIMIT_REACHED);
        }
        return CompletableFuture.supplyAsync(() -> {
            GlossaryTerm stored = repository.create(new GlossaryTermEntity(term)).toTerm();
            List<GlossaryTerm> next = new ArrayList<>(terms.get());
            next.add(stored);
            replace(next);
            return AddResult.ADDED;
        }, worker);
    }

    /**
     * Adds the terms of {@code glossary.yml} that are not in the glossary yet. Existing terms stay as they are, and
     * the edition's limit still applies.
     *
     * @param candidates the terms read from the file
     * @return what was added, skipped as duplicates and left out because of the limit
     */
    public @NotNull CompletableFuture<ImportResult> importTerms(@NotNull List<GlossaryTerm> candidates) {
        return CompletableFuture.supplyAsync(() -> {
            ImportResult result = plan(terms.get(), candidates, edition.maxGlossaryTerms());
            if (!result.toAdd().isEmpty()) {
                repository.createAll(result.toAdd().stream().map(GlossaryTermEntity::new).toList());
                replace(repository.findAll().stream().map(GlossaryTermEntity::toTerm).toList());
            }
            return result;
        }, worker);
    }

    /**
     * Removes every term with this text (any languages).
     *
     * @param text the term text in any case
     * @return the number of removed terms
     */
    public @NotNull CompletableFuture<Integer> remove(@NotNull String text) {
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        List<GlossaryTerm> matching = terms.get().stream()
                .filter(term -> term.term().toLowerCase(Locale.ROOT).equals(wanted))
                .toList();
        if (matching.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.supplyAsync(() -> {
            repository.deleteAll(matching.stream().map(GlossaryTerm::id).toList());
            List<GlossaryTerm> next = new ArrayList<>(terms.get());
            next.removeAll(matching);
            replace(next);
            return matching.size();
        }, worker);
    }

    /**
     * Removes one term by id.
     *
     * @param id the term id
     * @return whether a term was removed
     */
    public @NotNull CompletableFuture<Boolean> removeById(long id) {
        Optional<GlossaryTerm> match = terms.get().stream().filter(term -> term.id() == id).findFirst();
        if (match.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            repository.delete(id);
            List<GlossaryTerm> next = new ArrayList<>(terms.get());
            next.remove(match.get());
            replace(next);
            return true;
        }, worker);
    }

    private @NotNull Optional<GlossaryTerm> find(@NotNull GlossaryTerm candidate) {
        String wanted = candidate.term().toLowerCase(Locale.ROOT);
        return terms.get().stream()
                .filter(term -> term.term().toLowerCase(Locale.ROOT).equals(wanted))
                .filter(term -> Objects.equals(term.source(), candidate.source())
                        && Objects.equals(term.target(), candidate.target()))
                .findFirst();
    }

    private @NotNull List<GlossaryTerm> limit(@NotNull List<GlossaryTerm> seed) {
        int max = edition.maxGlossaryTerms();
        return max <= 0 || seed.size() <= max ? seed : seed.subList(0, max);
    }

    /** Applies an edit and tells the listeners; loading at start uses {@link #install(List)} and stays quiet. */
    private void replace(@NotNull List<GlossaryTerm> next) {
        install(next);
        changeListeners.forEach(Runnable::run);
    }

    private void install(@NotNull List<GlossaryTerm> next) {
        terms.set(next.stream().sorted(Comparator.comparing(term -> term.term().toLowerCase(Locale.ROOT))).toList());
        rules.clear();
    }

    private @NotNull MaskRules buildRules(@NotNull LanguagePair pair) {
        List<String> keep = new ArrayList<>();
        Map<String, String> force = new HashMap<>();
        for (GlossaryTerm term : terms.get()) {
            if (!term.appliesTo(pair)) {
                continue;
            }
            if (term.forces()) {
                force.putIfAbsent(term.term(), term.replacement());
            } else {
                keep.add(term.term());
            }
        }
        return new MaskRules(keep, force);
    }
}

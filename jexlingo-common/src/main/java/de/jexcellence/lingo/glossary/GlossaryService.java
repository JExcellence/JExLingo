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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    private final GlossaryTermRepository repository;
    private final LingoEdition edition;
    private final Executor worker;
    private final Logger logger;
    private final AtomicReference<List<GlossaryTerm>> terms = new AtomicReference<>(List.of());
    private final Map<LanguagePair, MaskRules> rules = new ConcurrentHashMap<>();
    private final List<Runnable> changeListeners = new ArrayList<>();

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
     * Registers a callback for glossary changes, for example to clear the translation cache.
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
            replace(repository.findAll().stream().map(GlossaryTermEntity::toTerm).toList());
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

    private void replace(@NotNull List<GlossaryTerm> next) {
        terms.set(next.stream().sorted(Comparator.comparing(term -> term.term().toLowerCase(Locale.ROOT))).toList());
        rules.clear();
        changeListeners.forEach(Runnable::run);
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

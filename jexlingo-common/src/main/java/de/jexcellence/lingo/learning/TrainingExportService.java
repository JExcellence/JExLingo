package de.jexcellence.lingo.learning;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Writes approved corrections as a tab-separated file (Premium). The format is fixed so the data can later train
 * an Argos model offline: {@code source_language, target_language, source, target}, one pair per line, tabs and
 * line breaks inside texts escaped.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class TrainingExportService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String HEADER = "source_language\ttarget_language\tsource\ttarget";

    private final TranslationMemoryService memory;
    private final Path directory;
    private final Executor worker;

    /**
     * The written file.
     *
     * @param file    the file
     * @param entries number of exported pairs
     */
    public record Export(@NotNull Path file, int entries) {
    }

    /**
     * Creates the service.
     *
     * @param memory    the translation memory
     * @param directory export directory, created on demand
     * @param worker    executor for file work
     */
    public TrainingExportService(@NotNull TranslationMemoryService memory, @NotNull Path directory,
                                 @NotNull Executor worker) {
        this.memory = memory;
        this.directory = directory;
        this.worker = worker;
    }

    /**
     * Returns the written file.
     *
     * @return the written file
     */
    public @NotNull CompletableFuture<Export> export() {
        return memory.approvedEntries().thenApplyAsync(this::write, worker);
    }

    /**
     * The TSV lines of entries, header first.
     *
     * @param entries the entries
     * @return the lines
     */
    public static @NotNull List<String> lines(@NotNull List<MemoryEntry> entries) {
        List<String> lines = new ArrayList<>(entries.size() + 1);
        lines.add(HEADER);
        for (MemoryEntry entry : entries) {
            lines.add(entry.pair().source().code() + '\t' + entry.pair().target().code() + '\t'
                    + escape(entry.sourceText()) + '\t' + escape(entry.targetText()));
        }
        return lines;
    }

    /**
     * Escapes a text for one TSV cell.
     *
     * @param text the text
     * @return the text with backslash, tab and line breaks escaped
     */
    public static @NotNull String escape(@NotNull String text) {
        return text.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    private @NotNull Export write(@NotNull List<MemoryEntry> entries) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("jexlingo-memory-" + LocalDateTime.now().format(FILE_TIME) + ".tsv");
            Files.write(file, lines(entries), StandardCharsets.UTF_8);
            return new Export(file, entries.size());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}

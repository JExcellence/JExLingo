package de.jexcellence.lingo.command;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command trees are copied into the data folder once so owners can rename or re-permission commands, which means an
 * update never reaches them. On every start this adds each top-level subcommand the bundled file has and the live
 * file lacks, and each missing subcommand of an existing one (for example {@code glossary import}), copied verbatim
 * with its settings. Existing entries, owner edits and comments stay untouched; a
 * timestamped backup is written before the file changes.
 */
public final class CommandTreeMerger {

    private static final Pattern TOP_LEVEL_ENTRY = Pattern.compile("^  - name:\\s*\"?([A-Za-z0-9_-]+)\"?\\s*$");
    private static final String SUBCOMMANDS = "subcommands:";
    private static final Pattern NESTED_ENTRY = Pattern.compile("^      - name:\\s*\"?([A-Za-z0-9_-]+)\"?\\s*$");
    private static final int NESTED_INDENT = 6;

    private CommandTreeMerger() {
    }

    /** Adds the bundled subcommands of {@code resourcePath} that the live copy is missing. */
    public static void addMissingSubcommands(@NotNull JavaPlugin plugin, @NotNull String resourcePath) {
        File live = new File(plugin.getDataFolder(), resourcePath.replace('/', File.separatorChar));
        if (!live.isFile()) {
            return;
        }
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return;
            }
            List<String> bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            List<String> current = new ArrayList<>(Files.readAllLines(live.toPath(), StandardCharsets.UTF_8));
            List<String> added = merge(bundled, current);
            if (added.isEmpty()) {
                return;
            }
            File backup = new File(live.getPath() + ".bak-" + System.currentTimeMillis());
            Files.copy(live.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.write(live.toPath(), current, StandardCharsets.UTF_8);
            plugin.getLogger().log(Level.INFO, () -> "Added new subcommands " + added + " to " + resourcePath
                    + " (backup: " + backup.getName() + ")");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, ex, () -> "Could not update " + resourcePath);
        }
    }

    /** Inserts the missing blocks into {@code current} and returns their names; package-visible for tests. */
    static @NotNull List<String> merge(@NotNull List<String> bundled, @NotNull List<String> current) {
        Set<String> present = entryNames(current);
        List<String> added = new ArrayList<>(mergeNested(bundled, current, present));
        List<String> blocks = new ArrayList<>();
        for (String name : entryNames(bundled)) {
            if (!present.contains(name)) {
                added.add(name);
                blocks.add("");
                blocks.addAll(block(bundled, name));
            }
        }
        if (!blocks.isEmpty()) {
            current.addAll(insertIndex(current), blocks);
        }
        return added;
    }

    /**
     * Adds subcommands that a top-level entry has in the bundled file but not in the live file, for example a new
     * {@code glossary import}, at the end of that entry. Entries without a {@code subcommands:} list in the live file
     * are left alone, because the owner may have removed it on purpose.
     */
    private static @NotNull List<String> mergeNested(@NotNull List<String> bundled, @NotNull List<String> current,
                                                     @NotNull Set<String> present) {
        List<String> added = new ArrayList<>();
        for (String name : entryNames(bundled)) {
            if (!present.contains(name)) {
                continue;
            }
            List<String> bundledBlock = block(bundled, name);
            int[] range = entryRange(current, name);
            List<String> liveBlock = current.subList(range[0], range[1]);
            if (liveBlock.stream().noneMatch(line -> line.trim().equals(SUBCOMMANDS))) {
                continue;
            }
            Set<String> liveNested = nestedNames(liveBlock);
            List<String> missing = new ArrayList<>();
            for (String nested : nestedNames(bundledBlock)) {
                if (!liveNested.contains(nested)) {
                    missing.addAll(nestedBlock(bundledBlock, nested));
                    added.add(name + "." + nested);
                }
            }
            if (!missing.isEmpty()) {
                int end = range[1];
                while (end > range[0] + 1 && current.get(end - 1).isBlank()) {
                    end--;
                }
                current.addAll(end, missing);
            }
        }
        return added;
    }

    /** Start (the entry line) and end (exclusive) of a top-level entry in {@code lines}. */
    private static int[] entryRange(@NotNull List<String> lines, @NotNull String name) {
        int start = -1;
        int end = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = TOP_LEVEL_ENTRY.matcher(lines.get(i));
            boolean entry = matcher.matches();
            if (start < 0 && entry && matcher.group(1).equals(name)) {
                start = i;
            } else if (start >= 0 && (entry || isTopLevelKey(lines.get(i)))) {
                end = i;
                break;
            }
        }
        return new int[]{Math.max(start, 0), start < 0 ? 0 : end};
    }

    private static @NotNull Set<String> nestedNames(@NotNull List<String> block) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : block) {
            Matcher matcher = NESTED_ENTRY.matcher(line);
            if (matcher.matches()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    /** The lines of one nested subcommand: its {@code - name:} line and everything indented below it. */
    private static @NotNull List<String> nestedBlock(@NotNull List<String> block, @NotNull String name) {
        List<String> out = new ArrayList<>();
        boolean inside = false;
        for (String line : block) {
            Matcher matcher = NESTED_ENTRY.matcher(line);
            if (matcher.matches()) {
                inside = matcher.group(1).equals(name);
            } else if (inside && !line.isBlank() && indent(line) <= NESTED_INDENT) {
                inside = false;
            }
            if (inside) {
                out.add(line);
            }
        }
        while (!out.isEmpty() && out.getLast().isBlank()) {
            out.removeLast();
        }
        return out;
    }

    private static int indent(@NotNull String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static @NotNull Set<String> entryNames(@NotNull List<String> lines) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : lines) {
            Matcher matcher = TOP_LEVEL_ENTRY.matcher(line);
            if (matcher.matches()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static @NotNull List<String> block(@NotNull List<String> lines, @NotNull String name) {
        List<String> out = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            Matcher matcher = TOP_LEVEL_ENTRY.matcher(line);
            if (matcher.matches()) {
                inside = matcher.group(1).equals(name);
            } else if (inside && isTopLevelKey(line)) {
                inside = false;
            }
            if (inside) {
                out.add(line);
            }
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** Right after the last line that belongs to the {@code subcommands:} list. */
    private static int insertIndex(@NotNull List<String> lines) {
        int start = lines.indexOf(SUBCOMMANDS);
        if (start < 0) {
            return lines.size();
        }
        int end = lines.size();
        for (int i = start + 1; i < lines.size(); i++) {
            if (isTopLevelKey(lines.get(i))) {
                end = i;
                break;
            }
        }
        while (end > start + 1 && lines.get(end - 1).isBlank()) {
            end--;
        }
        return end;
    }

    private static boolean isTopLevelKey(@NotNull String line) {
        return !line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.startsWith("#") && !line.startsWith("-");
    }
}

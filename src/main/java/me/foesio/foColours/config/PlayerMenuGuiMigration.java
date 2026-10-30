package me.foesio.foColours.config;

import me.foesio.core.gui.publicgui.GuiDefinitionException;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.gui.publicgui.StrictGuiYaml;
import me.foesio.foColours.FoColours;
import me.foesio.foColours.gui.PlayerColorMenu;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One-time bridge from released config.yml and the interim GUI v2 shape. */
final class PlayerMenuGuiMigration {

    static final int TARGET_VERSION = 3;
    static final String PENDING_PATH = "public-gui-migration-pending";
    private static final String RESOURCE = "guis/player-menu.yml";
    private static final Map<String, String> FIELDS = fields();

    private PlayerMenuGuiMigration() {
    }

    /** Returns true only when config.yml was atomically replaced and must be reloaded. */
    static boolean installOrUpgrade(FoColours plugin, boolean freshInstall) {
        Path root = plugin.getDataFolder().toPath();
        Path target = root.resolve(RESOURCE);
        Path configFile = root.resolve("config.yml");
        byte[] bundled;
        try (InputStream input = plugin.getResource(RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing packaged " + RESOURCE);
            }
            bundled = input.readAllBytes();
        } catch (IOException error) {
            plugin.logWarning("Cannot load player GUI defaults: " + error.getMessage());
            return false;
        }

        try {
            if (Files.isSymbolicLink(configFile) || Files.isSymbolicLink(target)
                    || Files.isSymbolicLink(target.getParent())) {
                throw new IOException("Player GUI migration will not replace a symbolic link");
            }
            if (Files.isRegularFile(target)) {
                YamlConfiguration active = StrictGuiYaml.load(target);
                if (active.contains("title", false) && !active.contains("gui", false)) {
                    YamlConfiguration installedConfig = StrictGuiYaml.load(configFile);
                    if (installedConfig.isConfigurationSection("gui.player-menu")
                            && installedConfig.getInt("public-gui-schema-version", 0) < TARGET_VERSION) {
                        finishInterruptedConfigUpgrade(configFile, installedConfig);
                        plugin.logInfo("Finished interrupted released player GUI config migration.");
                        return true;
                    }
                    return false; // Current owner file: never backfill or rewrite on reload.
                }
                if (!active.isConfigurationSection("gui.player-menu")) {
                    throw new IOException("Installed player GUI has an unknown schema; preserving its bytes");
                }
                int version = active.getInt("schema-version", 1);
                return upgrade(plugin, active, version, target, configFile, bundled, false);
            }
            if (Files.exists(target)) {
                throw new IOException("Player GUI path is not a regular file");
            }
            YamlConfiguration installedConfig = StrictGuiYaml.load(configFile);
            if (installedConfig.isConfigurationSection("gui.player-menu")) {
                return upgrade(plugin, installedConfig, 1, target, configFile, bundled, true);
            }
            if (!freshInstall) {
                throw new IOException("Player GUI is missing on an existing installation; owner deletion preserved");
            }
            // No predecessor owner source: install generated defaults, without migration or backup.
            atomicWrite(target, bundled);
            StrictGuiYaml.load(target);
            plugin.logInfo("Installed new player GUI from packaged defaults.");
            return false;
        } catch (IOException | InvalidConfigurationException error) {
            plugin.logWarning("Player GUI unavailable: " + error.getMessage());
            return false;
        }
    }

    private static boolean upgrade(FoColours plugin, YamlConfiguration old, int version,
                                   Path target, Path configFile, byte[] bundled, boolean fromConfig) throws IOException {
        if (version != 1 && version != 2) {
            throw new IOException("Unsupported player GUI schema v" + version + "; owner file retained");
        }
        YamlConfiguration oldDefaults = resource(plugin, "migrations/player-menu-v" + version + ".yml");
        YamlConfiguration newest = resource(plugin, RESOURCE);
        YamlConfiguration candidate = candidate(old, oldDefaults, newest, plugin::logWarning);

        YamlConfiguration nextConfig;
        try {
            nextConfig = StrictGuiYaml.load(configFile);
        } catch (InvalidConfigurationException error) {
            throw new IOException("Invalid config.yml; migration kept pending", error);
        }
        nextConfig.set("public-gui-schema-version", TARGET_VERSION);
        if (fromConfig) {
            nextConfig.set(PENDING_PATH, true);
            removeLegacyPublicGuiKeys(nextConfig);
        }
        byte[] nextGuiBytes = candidate.saveToString().getBytes(StandardCharsets.UTF_8);
        byte[] nextConfigBytes = nextConfig.saveToString().getBytes(StandardCharsets.UTF_8);
        stageAndCommit(target, configFile, nextGuiBytes, nextConfigBytes, version, fromConfig);
        plugin.logInfo("Migrated player GUI v" + version + " to Core schema v" + TARGET_VERSION + ".");
        return true;
    }

    static void removeLegacyPublicGuiKeys(YamlConfiguration config) {
        for (String oldPublicPath : List.of(
                "gui.shared.colour-slots", "gui.shared.empty-colour-material",
                "gui.shared.filler", "gui.shared.empty-colour", "gui.shared.close", "gui.shared.back",
                "gui.states.unlocked", "gui.states.locked", "gui.player-menu")) {
            config.set(oldPublicPath, null);
        }
    }

    static void finishInterruptedConfigUpgrade(Path configFile, YamlConfiguration oldConfig) throws IOException {
        YamlConfiguration next = new YamlConfiguration();
        try {
            next.loadFromString(oldConfig.saveToString());
        } catch (InvalidConfigurationException error) {
            throw new IOException("Interrupted config migration source is invalid", error);
        }
        next.set("public-gui-schema-version", TARGET_VERSION);
        next.set(PENDING_PATH, true);
        removeLegacyPublicGuiKeys(next);
        backupOnce(configFile, 1);
        atomicWrite(configFile, next.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    /** Stages the full transaction even when an older installation has no guis/ folder yet. */
    static void stageAndCommit(Path target, Path configFile, byte[] nextGuiBytes, byte[] nextConfigBytes,
                               int version, boolean fromConfig) throws IOException {
        Files.createDirectories(target.getParent());
        Path stagedGui = Files.createTempFile(target.getParent(), ".player-menu-v3-", ".yml");
        Path stagedConfig = Files.createTempFile(configFile.getParent(), ".config-gui-v3-", ".yml");
        try {
            Files.write(stagedGui, nextGuiBytes);
            Files.write(stagedConfig, nextConfigBytes);
            try {
                validate(StrictGuiYaml.load(stagedGui), stagedGui.toString());
                StrictGuiYaml.load(stagedConfig);
            } catch (InvalidConfigurationException error) {
                throw new IOException("Staged migration failed strict YAML validation", error);
            }
            commitStaged(target, configFile, stagedGui, stagedConfig, version, fromConfig, () -> {});
        } finally {
            Files.deleteIfExists(stagedGui);
            Files.deleteIfExists(stagedConfig);
        }
    }

    /** Package-visible transaction seam for byte-for-byte backup and rollback tests. */
    static void commitStaged(Path target, Path configFile, Path stagedGui, Path stagedConfig,
                             int version, boolean fromConfig, Runnable afterGuiReplace) throws IOException {
        byte[] guiBefore = Files.isRegularFile(target) ? Files.readAllBytes(target) : null;
        byte[] configBefore = Files.readAllBytes(configFile);
        backupOnce(fromConfig ? configFile : target, version);
        if (!fromConfig) {
            backupOnce(configFile, version);
        }
        try {
            replace(stagedGui, target);
            afterGuiReplace.run();
            replace(stagedConfig, configFile);
        } catch (IOException | RuntimeException error) {
            try {
                if (guiBefore == null) {
                    Files.deleteIfExists(target);
                } else {
                    atomicWrite(target, guiBefore);
                }
                atomicWrite(configFile, configBefore);
            } catch (IOException rollbackError) {
                error.addSuppressed(rollbackError);
            }
            throw error;
        }
    }

    static YamlConfiguration candidate(YamlConfiguration old, YamlConfiguration oldDefaults,
                                       YamlConfiguration newest, java.util.function.Consumer<String> warning)
            throws IOException {
        YamlConfiguration candidate = YamlConfiguration.loadConfiguration(
                new java.io.StringReader(newest.saveToString()));
        overlay(old, oldDefaults, newest, candidate, warning);
        return candidate;
    }

    private static void overlay(YamlConfiguration old, YamlConfiguration oldDefaults,
                                YamlConfiguration newest, YamlConfiguration candidate,
                                java.util.function.Consumer<String> warning)
            throws IOException {
        for (Map.Entry<String, String> field : FIELDS.entrySet()) {
            String source = field.getKey();
            String target = field.getValue();
            boolean nullableV2Field = oldDefaults.getInt("schema-version", 1) == 2
                    && source.endsWith(".custom-model-data");
            if (!oldDefaults.contains(source, false) && !nullableV2Field) {
                continue; // Genuinely new field: use the newest packaged default.
            }
            if (!old.contains(source, false)) {
                if (optional(target)) {
                    candidate.set(target, null);
                } else {
                    warning.accept("GUI migration: missing required " + source + "; using " + newest.get(target));
                }
                continue;
            }
            Object value = old.get(source);
            if (!Objects.equals(value, oldDefaults.get(source))) {
                candidate.set(target, target.endsWith(".slots") && source.endsWith(".slot")
                        && value instanceof Integer slot ? List.of(slot) : value);
            }
        }
        // The old item template served selected and unselected entries. Each
        // new state has a complete Core-owned presentation.
        for (String field : List.of("name", "lore", "amount", "custom-model-data")) {
            String source = "gui.player-menu.items.colour-unlocked." + field;
            if (!oldDefaults.contains(source, false)
                    && !(oldDefaults.getInt("schema-version", 1) == 2
                    && source.endsWith(".custom-model-data"))) {
                continue;
            }
            if (!old.contains(source, false)) {
                if (optional("states.colour-selected." + field)) {
                    candidate.set("states.colour-selected." + field, null);
                }
            } else if (!Objects.equals(old.get(source), oldDefaults.get(source))) {
                candidate.set("states.colour-selected." + field, old.get(source));
            }
        }
        String selectedGlow = "gui.player-menu.items.colour-unlocked.selected-glow";
        if (oldDefaults.contains(selectedGlow, false) && old.contains(selectedGlow, false)
                && !Objects.equals(old.get(selectedGlow), oldDefaults.get(selectedGlow))) {
            candidate.set("states.colour-selected.glow", old.get(selectedGlow));
        }
        upgradeInfo(old, oldDefaults, candidate, warning);
        // Parse all mapped values together. If one old value or relation is
        // invalid, replace only the reported field with its newest default.
        for (int attempt = 0; attempt < 64; attempt++) {
            try {
                validate(candidate, RESOURCE);
                return;
            } catch (IOException failure) {
                Throwable cause = failure.getCause();
                if (!(cause instanceof GuiDefinitionException problem)) {
                    throw failure;
                }
                String path = problem.key().replaceAll("\\[\\d+].*$", "");
                if (path.endsWith(".slot") && path.startsWith("entries.")) {
                    path = path.substring(0, path.length() - 5) + ".slots";
                }
                Object defaultValue = newest.get(path);
                Object invalid = candidate.get(path);
                if (invalid == null && defaultValue == null || Objects.equals(invalid, defaultValue)) {
                    throw new IOException("Cannot isolate invalid migrated field " + problem.key() + ": "
                            + problem.getMessage(), problem);
                }
                candidate.set(path, defaultValue);
                warning.accept("GUI migration replaced invalid " + path + "=" + invalid + " ("
                        + problem.getMessage() + ") with newest default " + defaultValue);
            }
        }
        throw new IOException("Too many invalid migrated GUI fields; owner file retained");
    }

    private static void upgradeInfo(YamlConfiguration old, YamlConfiguration defaults, YamlConfiguration candidate,
                                    java.util.function.Consumer<String> warning) {
        String root = "gui.player-menu.items.info.";
        List<String> emptyLore = new ArrayList<>(candidate.getStringList("buttons.info.states.empty.lore"));
        String newestEmptyLine = emptyLore.isEmpty() ? null : emptyLore.getLast();
        if (!emptyLore.isEmpty()) emptyLore.removeLast();
        if (defaults.contains(root + "lore", false)) {
            if (!old.contains(root + "lore", false)) {
                candidate.set("buttons.info.lore", List.of("{options}"));
                emptyLore.clear();
            } else if (!Objects.equals(old.get(root + "lore"), defaults.get(root + "lore"))) {
                Object raw = old.get(root + "lore");
                if (raw instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
                    List<String> lines = new ArrayList<>(old.getStringList(root + "lore"));
                    emptyLore.clear();
                    emptyLore.addAll(lines);
                    lines.add("{options}");
                    candidate.set("buttons.info.lore", lines);
                } else {
                    candidate.set("buttons.info.lore", raw); // Core reports and repairs this field.
                }
            }
        }
        String header = root + "unlocked-header";
        if (old.contains(header, false) && !Objects.equals(old.get(header), defaults.get(header))) {
            if (old.get(header) instanceof String text) {
                List<String> lines = new ArrayList<>(candidate.getStringList("buttons.info.lore"));
                lines.add(Math.max(0, lines.size() - 1), text);
                candidate.set("buttons.info.lore", lines);
            } else {
                warning.accept("GUI migration: invalid " + header + "=" + old.get(header)
                        + " (expected text); using newest bundled buttons.info.lore="
                        + candidate.getStringList("buttons.info.lore"));
            }
        }
        String entry = root + "unlocked-entry";
        if (defaults.contains(entry, false) && !old.contains(entry, false)) {
            candidate.set("buttons.info.cycle.selected-format", "");
            candidate.set("buttons.info.cycle.unselected-format", "");
        } else if (old.contains(entry, false) && !Objects.equals(old.get(entry), defaults.get(entry))) {
            Object raw = old.get(entry);
            Object format = raw instanceof String text ? text.replace("{colour}", "{option}") : raw;
            candidate.set("buttons.info.cycle.selected-format", format);
            candidate.set("buttons.info.cycle.unselected-format", format);
        }
        String empty = root + "empty-unlocked";
        if (defaults.contains(empty, false) && !old.contains(empty, false)) {
            candidate.set("buttons.info.states.empty.lore", emptyLore);
        } else if (old.contains(empty, false) && !Objects.equals(old.get(empty), defaults.get(empty))) {
            if (old.get(empty) instanceof String text) {
                emptyLore.add(text);
                candidate.set("buttons.info.states.empty.lore", emptyLore);
            } else {
                warning.accept("GUI migration: invalid " + empty + "=" + old.get(empty)
                        + " (expected text); using newest bundled empty label=" + newestEmptyLine);
                if (newestEmptyLine != null) emptyLore.add(newestEmptyLine);
                candidate.set("buttons.info.states.empty.lore", emptyLore);
            }
        } else {
            if (newestEmptyLine != null) emptyLore.add(newestEmptyLine);
            candidate.set("buttons.info.states.empty.lore", emptyLore);
        }
    }

    private static boolean optional(String target) {
        return !(target.equals("title") || target.equals("rows") || target.equals("filler.enabled")
                || target.endsWith(".material") || target.endsWith(".slots") || target.endsWith(".slot"));
    }

    private static Map<String, String> fields() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("gui.player-menu.title", "title");
        map.put("gui.player-menu.rows", "rows");
        map.put("gui.shared.colour-slots", "entries.colours.slots");
        map.put("gui.shared.filler-material", "filler.material");
        map.put("gui.shared.empty-colour-material", "entries.colours.empty-item.material");
        map.put("gui.player-menu.locked-material", "states.colour-locked.material");
        map.put("gui.states.unlocked", "states.status-unlocked.name");
        map.put("gui.states.locked", "states.status-locked.name");
        for (String field : List.of("material", "name", "lore", "amount", "custom-model-data", "glow")) {
            map.put("gui.shared.filler." + field, "filler." + field);
            map.put("gui.shared.empty-colour." + field, "entries.colours.empty-item." + field);
        }
        for (String field : List.of("name", "lore", "amount", "custom-model-data", "glow")) {
            map.put("gui.player-menu.items.colour-unlocked." + field, "states.colour-unlocked." + field);
            map.put("gui.player-menu.items.colour-locked." + field, "states.colour-locked." + field);
        }
        for (String item : List.of("reset", "info", "previous-page", "next-page")) {
            String destination = switch (item) {
                case "previous-page" -> "entries.previous.item";
                case "next-page" -> "entries.next.item";
                default -> "buttons." + item;
            };
            for (String field : List.of("material", "name", "lore", "amount", "custom-model-data", "glow")) {
                if (item.equals("info") && field.equals("lore")) continue;
                map.put("gui.player-menu.items." + item + "." + field, destination + "." + field);
            }
            if (item.equals("reset") || item.equals("info")) {
                map.put("gui.player-menu.items." + item + ".slot", "buttons." + item + ".slot");
            } else {
                map.put("gui.player-menu.items." + item + ".slot", "entries."
                        + (item.equals("previous-page") ? "previous" : "next") + ".slots");
            }
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    private static YamlConfiguration resource(FoColours plugin, String path) throws IOException {
        try (InputStream stream = plugin.getResource(path)) {
            if (stream == null) throw new IOException("Missing bundled " + path);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static void validate(YamlConfiguration yaml, String file) throws IOException {
        try {
            PublicGuiSchema.parse(PlayerColorMenu.MENU_ID, file, yaml, PlayerColorMenu.contract());
        } catch (GuiDefinitionException error) {
            throw new IOException(file + ":" + error.key() + ": " + error.getMessage(), error);
        }
    }

    private static void backupOnce(Path source, int oldVersion) throws IOException {
        Path backup = source.resolveSibling(source.getFileName() + ".gui-v" + oldVersion + "-backup");
        if (!Files.exists(backup)) {
            Files.copy(source, backup);
        }
    }

    static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path stage = Files.createTempFile(target.getParent(), ".gui-write-", ".tmp");
        try {
            Files.write(stage, bytes);
            replace(stage, target);
        } finally {
            Files.deleteIfExists(stage);
        }
    }

    private static void replace(Path stage, Path target) throws IOException {
        try {
            Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(stage, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

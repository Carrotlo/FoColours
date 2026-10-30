package me.foesio.foColours.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Copies released config.yml text into a newly installed messages.yml exactly once. */
public final class PublicTextMigration {

    private PublicTextMigration() {
    }

    public static boolean fromReleasedConfig(FileConfiguration owner, FileConfiguration releasedDefaults,
                                             FileConfiguration target, FileConfiguration newestDefaults,
                                             Consumer<String> warning) {
        if (!owner.getBoolean(PlayerMenuGuiMigration.PENDING_PATH, false)) {
            return false;
        }
        boolean changed = overlay(owner, releasedDefaults, target, newestDefaults,
                "messages", "messages", warning);
        changed |= overlay(owner, releasedDefaults, target, newestDefaults,
                "messages", "tokens", "messages.prefix", "tokens.prefix", warning);
        changed |= overlay(owner, releasedDefaults, target, newestDefaults,
                "style.tokens", "tokens", warning);
        return changed;
    }

    /** A migration is complete only when the values Core serves were saved to disk. */
    public static boolean sameValues(FileConfiguration expected, FileConfiguration saved) {
        Set<String> expectedKeys = leafKeys(expected);
        if (!expectedKeys.equals(leafKeys(saved))) {
            return false;
        }
        return expectedKeys.stream().allMatch(key -> Objects.equals(expected.get(key), saved.get(key)));
    }

    private static Set<String> leafKeys(FileConfiguration config) {
        Set<String> keys = new java.util.HashSet<>(config.getKeys(true));
        keys.removeIf(config::isConfigurationSection);
        return keys;
    }

    private static boolean overlay(FileConfiguration owner, FileConfiguration releasedDefaults,
                                   FileConfiguration target, FileConfiguration newestDefaults,
                                   String sourceRoot, String targetRoot, Consumer<String> warning) {
        ConfigurationSection oldSection = releasedDefaults.getConfigurationSection(sourceRoot);
        if (oldSection == null) {
            return false;
        }
        boolean changed = false;
        for (String key : oldSection.getKeys(true)) {
            String source = sourceRoot + "." + key;
            if (releasedDefaults.isConfigurationSection(source) || source.equals("messages.prefix")) {
                continue;
            }
            changed |= overlay(owner, releasedDefaults, target, newestDefaults,
                    sourceRoot, targetRoot, source, targetRoot + "." + key, warning);
        }
        return changed;
    }

    private static boolean overlay(FileConfiguration owner, FileConfiguration releasedDefaults,
                                   FileConfiguration target, FileConfiguration newestDefaults,
                                   String sourceRoot, String targetRoot, String source, String destination,
                                   Consumer<String> warning) {
        if (!newestDefaults.contains(destination, false) || !releasedDefaults.contains(source, false)) {
            return false; // Removed legacy field, or genuinely new destination.
        }
        if (!owner.contains(source, false)) {
            if (!target.contains(destination, false)) {
                return false;
            }
            target.set(destination, null);
            return true;
        }
        Object value = owner.get(source);
        Object oldDefault = releasedDefaults.get(source);
        if (Objects.equals(value, oldDefault)) {
            return false; // Use the newest bundled style for an unchanged old default.
        }
        if (!sameTextShape(value, oldDefault)) {
            warning.accept("Message migration: invalid " + source + "=" + value
                    + "; using newest bundled " + destination + "=" + newestDefaults.get(destination));
            return false;
        }
        if (Objects.equals(target.get(destination), value)) {
            return false;
        }
        target.set(destination, value);
        return true;
    }

    private static boolean sameTextShape(Object value, Object oldDefault) {
        if (oldDefault instanceof String) {
            return value instanceof String;
        }
        if (oldDefault instanceof java.util.List<?>) {
            return value instanceof java.util.List<?> lines && lines.stream().allMatch(String.class::isInstance);
        }
        return false;
    }
}

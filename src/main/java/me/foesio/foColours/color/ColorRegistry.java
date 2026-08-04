package me.foesio.foColours.color;

import me.foesio.foColours.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ColorRegistry {

    private final ConfigManager configManager;
    private volatile Map<String, ChatColorDefinition> definitions = Map.of();
    private volatile Map<String, String> aliasLookup = Map.of();

    public ColorRegistry(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void reload() {
        LinkedHashMap<String, ChatColorDefinition> loadedDefinitions = new LinkedHashMap<>();
        LinkedHashMap<String, String> loadedAliases = new LinkedHashMap<>();

        loadColours("colours.built-in", false, loadedDefinitions, loadedAliases);
        loadColours("colours.custom", true, loadedDefinitions, loadedAliases);
        ensureEnabledFallback(loadedDefinitions, loadedAliases);

        this.definitions = Collections.unmodifiableMap(loadedDefinitions);
        this.aliasLookup = Collections.unmodifiableMap(loadedAliases);
    }

    public Collection<ChatColorDefinition> getAllColours() {
        return definitions.values();
    }

    public List<ChatColorDefinition> getEnabledColours() {
        return definitions.values().stream()
                .filter(ChatColorDefinition::isEnabled)
                .toList();
    }

    public Optional<ChatColorDefinition> getColour(String key) {
        return Optional.ofNullable(definitions.get(normalize(key)));
    }

    public Optional<ChatColorDefinition> getEnabledColour(String key) {
        return getColour(key).filter(ChatColorDefinition::isEnabled);
    }

    public Optional<ChatColorDefinition> findEnabledColour(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(input);
        String key = aliasLookup.getOrDefault(normalized, normalized);
        return Optional.ofNullable(definitions.get(key)).filter(ChatColorDefinition::isEnabled);
    }

    public String normalize(String input) {
        if (input == null) {
            return "";
        }

        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_')
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private void loadColours(String path, boolean custom, Map<String, ChatColorDefinition> loadedDefinitions, Map<String, String> loadedAliases) {
        ConfigurationSection section = configManager.getConfig().getConfigurationSection(path);
        if (section == null) {
            return;
        }

        for (String rawKey : section.getKeys(false)) {
            ConfigurationSection colourSection = section.getConfigurationSection(rawKey);
            if (colourSection == null) {
                continue;
            }

            String key = normalize(rawKey);
            if (key.isBlank()) {
                continue;
            }

            ColorType type = parseType(colourSection.getString("type", custom ? "HEX" : "LEGACY"));
            boolean enabled = colourSection.getBoolean("enabled", true);

            String displayName = colourSection.getString("display-name", humanize(key));
            String value = switch (type) {
                case LEGACY -> colourSection.getString("legacy-code", "&f");
                case HEX -> colourSection.getString("hex", "#ffffff");
                case RAINBOW -> "";
            };

            Material material = configManager.readMaterial(colourSection.getString("material"), custom ? Material.NAME_TAG : Material.WHITE_DYE);
            List<String> aliases = new ArrayList<>(colourSection.getStringList("aliases"));
            aliases.add(key);
            aliases.add(displayName);

            ChatColorDefinition definition = new ChatColorDefinition(key, displayName, aliases, type, value, material, enabled, custom);
            loadedDefinitions.put(key, definition);

            if (enabled) {
                for (String alias : aliases) {
                    loadedAliases.put(normalize(alias), key);
                }
            }
        }
    }

    private void ensureEnabledFallback(Map<String, ChatColorDefinition> loadedDefinitions, Map<String, String> loadedAliases) {
        boolean hasEnabledColour = loadedDefinitions.values().stream().anyMatch(ChatColorDefinition::isEnabled);
        if (hasEnabledColour) {
            return;
        }

        ChatColorDefinition fallback = new ChatColorDefinition(
                "light_gray",
                "Light Gray",
                List.of("light_gray", "lightgray", "gray", "grey", "default"),
                ColorType.LEGACY,
                "&7",
                Material.LIGHT_GRAY_DYE,
                true,
                false
        );
        loadedDefinitions.put(fallback.getKey(), fallback);
        for (String alias : fallback.getAliases()) {
            loadedAliases.put(normalize(alias), fallback.getKey());
        }
    }

    private ColorType parseType(String rawType) {
        try {
            return ColorType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ColorType.LEGACY;
        }
    }

    private String humanize(String key) {
        String[] parts = key.split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return String.join(" ", words);
    }
}

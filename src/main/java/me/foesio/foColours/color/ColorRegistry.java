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

            String colourPath = path + "." + rawKey;
            Optional<ColorType> parsedType = readType(colourSection, custom);
            if (parsedType.isEmpty()) {
                configManager.warnInvalidColour(colourPath + ".type", "expected LEGACY, HEX, or RAINBOW");
                continue;
            }
            ColorType type = parsedType.get();
            boolean enabled = colourSection.getBoolean("enabled", true);

            String displayName = colourSection.getString("display-name", humanize(key));
            String value = readColourValue(colourSection, type);
            if (type == ColorType.HEX && (value == null || !value.matches("#[0-9A-Fa-f]{6}"))
                    || type == ColorType.LEGACY && (value == null || !value.matches("&[0-9A-Fa-fK-ORk-or]"))) {
                configManager.warnInvalidColour(colourPath + (type == ColorType.HEX ? ".hex" : ".legacy-code"),
                        "invalid or missing colour value");
                continue;
            }

            Optional<Material> configuredMaterial = configManager.readOptionalItemMaterial(
                    colourSection.getString("material"), colourPath + ".material");
            Material material = configuredMaterial.orElse(custom ? Material.NAME_TAG : Material.WHITE_DYE);
            List<String> aliases = new ArrayList<>(colourSection.getStringList("aliases"));
            aliases.add(key);
            aliases.add(displayName);

            ChatColorDefinition definition = new ChatColorDefinition(key, displayName, aliases, type, value,
                    material, configuredMaterial.isPresent(), enabled, custom);
            loadedDefinitions.put(key, definition);

            if (enabled) {
                for (String alias : aliases) {
                    loadedAliases.put(normalize(alias), key);
                }
            }
        }
    }

    static Optional<ColorType> readType(ConfigurationSection section, boolean custom) {
        if (!section.contains("type", false)) {
            // Both were valid omissions in released FoColours owner data.
            return Optional.of(custom ? ColorType.HEX : ColorType.LEGACY);
        }
        String rawType = section.getString("type");
        if (rawType == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ColorType.valueOf(rawType.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static String readColourValue(ConfigurationSection section, ColorType type) {
        return switch (type) {
            // Omitted value fields were valid in the released loader. Explicit
            // malformed values remain invalid and are rejected by reload().
            case LEGACY -> section.contains("legacy-code", false)
                    ? section.getString("legacy-code") : "&f";
            case HEX -> section.contains("hex", false)
                    ? section.getString("hex") : "#ffffff";
            case RAINBOW -> "";
        };
    }

    static String humanize(String key) {
        String[] parts = key.split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
            }
        }
        return String.join(" ", words);
    }

}

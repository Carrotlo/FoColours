package me.foesio.foColours.color;

import me.foesio.foColours.config.ConfigManager;
import me.foesio.foColours.util.TextUtil;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class ChatColorDefinition {

    private final String key;
    private final String displayName;
    private final List<String> aliases;
    private final ColorType type;
    private final String value;
    private final Material material;
    private final boolean enabled;
    private final boolean custom;

    public ChatColorDefinition(String key, String displayName, List<String> aliases, ColorType type, String value, Material material, boolean enabled, boolean custom) {
        this.key = key;
        this.displayName = displayName;
        this.aliases = List.copyOf(new ArrayList<>(aliases));
        this.type = type;
        this.value = value;
        this.material = material;
        this.enabled = enabled;
        this.custom = custom;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public ColorType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public Material getMaterial() {
        return material;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCustom() {
        return custom;
    }

    public String applyToText(String input, ConfigManager configManager) {
        String cleanInput = input == null ? "" : input;
        return switch (type) {
            case LEGACY -> TextUtil.colorize(value + cleanInput + "&r");
            case HEX -> TextUtil.hexToLegacy(value) + cleanInput + org.bukkit.ChatColor.RESET;
            case RAINBOW -> TextUtil.applyRainbow(cleanInput, configManager.getRainbowPalette(), configManager.isRainbowAdvanceOnSpaces());
        };
    }

    public String getChatFormatCode(ConfigManager configManager) {
        return switch (type) {
            case LEGACY -> value;
            case HEX -> "&" + value;
            case RAINBOW -> "";
        };
    }

    public String getPreviewLabel(ConfigManager configManager) {
        return applyToText(displayName, configManager);
    }
}

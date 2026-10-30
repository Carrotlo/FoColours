package me.foesio.foColours.service;

import me.foesio.foColours.compat.EzColorsCompatService;
import me.foesio.foColours.api.ColourChangeCause;
import me.foesio.foColours.color.ChatColorDefinition;
import me.foesio.foColours.color.ColorRegistry;
import me.foesio.foColours.color.ColorType;
import me.foesio.foColours.config.ConfigManager;
import me.foesio.foColours.data.KnownPlayer;
import me.foesio.foColours.data.PlayerColorData;
import me.foesio.foColours.data.PlayerDataManager;
import me.foesio.foColours.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ChatColorService {

    private final ConfigManager configManager;
    private final ColorRegistry colorRegistry;
    private final PlayerDataManager playerDataManager;
    private final EzColorsCompatService ezColorsCompatService;

    public ChatColorService(ConfigManager configManager, ColorRegistry colorRegistry, PlayerDataManager playerDataManager, EzColorsCompatService ezColorsCompatService) {
        this.configManager = configManager;
        this.colorRegistry = colorRegistry;
        this.playerDataManager = playerDataManager;
        this.ezColorsCompatService = ezColorsCompatService;
    }

    public List<ChatColorDefinition> getUnlockedColours(Player player) {
        List<ChatColorDefinition> unlocked = new ArrayList<>();
        for (ChatColorDefinition definition : colorRegistry.getEnabledColours()) {
            if (hasUnlocked(player, definition.getKey())) {
                unlocked.add(definition);
            }
        }
        return unlocked;
    }

    public boolean hasUnlocked(Player player, String key) {
        PlayerColorData data = playerDataManager.loadPlayer(player);
        if (data.isRevoked(key)) {
            return false;
        }
        return data.hasUnlocked(key) || ezColorsCompatService.hasPermissionAccess(player, key);
    }

    public boolean isSelected(Player player, String key) {
        return hasEnabledColours() && resolveSelectedKey(player).equals(key);
    }

    public boolean isDefaultSelected(Player player) {
        return hasEnabledColours() && resolveSelectedKey(player).equals(getDefaultColour().getKey());
    }

    public void selectColour(Player player, String key) {
        selectColour(player, key, ColourChangeCause.API);
    }

    public boolean selectColour(Player player, String key, ColourChangeCause cause) {
        playerDataManager.loadPlayer(player);
        if (ezColorsCompatService.isEnabled()) {
            ezColorsCompatService.syncPlayer(player, false);
        }
        PlayerColorData data = playerDataManager.loadPlayer(player);
        if (!data.isRevoked(key) && ezColorsCompatService.hasPermissionAccess(player, key)) {
            playerDataManager.grantColour(player, key);
        }
        if (colorRegistry.getEnabledColour(key).isEmpty() || !hasUnlocked(player, key)) {
            return false;
        }
        return playerDataManager.setSelectedColour(player, key, cause);
    }

    public void resetColour(Player player) {
        resetColour(player, ColourChangeCause.API);
    }

    public boolean resetColour(Player player, ColourChangeCause cause) {
        playerDataManager.loadPlayer(player);
        if (ezColorsCompatService.isEnabled()) {
            ezColorsCompatService.syncPlayer(player, false);
        }
        return playerDataManager.setSelectedColour(player, getDefaultColour().getKey(), cause);
    }

    public boolean grantColour(KnownPlayer target, ChatColorDefinition colour) {
        return grantColour(target, colour, ColourChangeCause.ADMIN_COMMAND);
    }

    public boolean grantColour(KnownPlayer target, ChatColorDefinition colour, ColourChangeCause cause) {
        return playerDataManager.grantColour(target, colour.getKey(), cause);
    }

    public boolean takeColour(KnownPlayer target, ChatColorDefinition colour) {
        return takeColour(target, colour, ColourChangeCause.ADMIN_COMMAND);
    }

    public boolean takeColour(KnownPlayer target, ChatColorDefinition colour, ColourChangeCause cause) {
        Player onlineTarget = Bukkit.getPlayer(target.uuid());
        boolean hasLivePermissionAccess = onlineTarget != null && ezColorsCompatService.hasPermissionAccess(onlineTarget, colour.getKey());
        return playerDataManager.takeColour(target, colour.getKey(), getDefaultColour().getKey(), hasLivePermissionAccess, cause);
    }

    public boolean isRainbowSelected(Player player) {
        return hasEnabledColours() && getActiveColour(player).getType() == ColorType.RAINBOW;
    }

    public boolean isCachedRainbowSelected(Player player) {
        return hasEnabledColours() && getCachedActiveColour(player).getType() == ColorType.RAINBOW;
    }

    public String colorizeRainbowChatMessage(String rawMessage) {
        return TextUtil.applyRainbow(TextUtil.stripColour(rawMessage), configManager.getRainbowPalette(), configManager.isRainbowAdvanceOnSpaces());
    }

    public String colorizeSelectedChatMessage(Player player, String rawMessage) {
        return hasEnabledColours()
                ? getCachedActiveColour(player).applyToText(TextUtil.stripColour(rawMessage), configManager)
                : rawMessage;
    }

    public String getPlaceholderValue(Player player) {
        return hasEnabledColours() ? getCachedActiveColour(player).getChatFormatCode(configManager) : "";
    }

    public String getPlaceholderValue(OfflinePlayer player) {
        if (!hasEnabledColours()) {
            return "";
        }
        if (player instanceof Player onlinePlayer) {
            return getPlaceholderValue(onlinePlayer);
        }
        if (player == null || player.getName() == null) {
            return getDefaultPlaceholderValue();
        }

        PlayerColorData data = playerDataManager.getOrLoad(new KnownPlayer(player.getUniqueId(), player.getName()));
        String selected = data.getSelectedColour();
        if (data.isRevoked(selected) || !data.hasUnlocked(selected)) {
            selected = configManager.getDefaultColourKey();
        }

        return colorRegistry.getEnabledColour(selected)
                .orElseGet(this::getDefaultColour)
                .getChatFormatCode(configManager);
    }

    public String getDefaultPlaceholderValue() {
        return hasEnabledColours() ? getDefaultColour().getChatFormatCode(configManager) : "";
    }

    public String getPlaceholderPreview(Player player) {
        return hasEnabledColours() ? getCachedActiveColour(player).getPreviewLabel(configManager)
                : configManager.messageSetting("formats.unknown-colour");
    }

    public String getDefaultPlaceholderPreview() {
        return hasEnabledColours() ? getDefaultColour().getPreviewLabel(configManager)
                : configManager.messageSetting("formats.unknown-colour");
    }

    public String getPlaceholderDisplayName(Player player) {
        return hasEnabledColours() ? getCachedActiveColour(player).getDisplayName()
                : configManager.messageSetting("formats.unknown-colour");
    }

    public String getDefaultPlaceholderDisplayName() {
        return hasEnabledColours() ? getDefaultColour().getDisplayName()
                : configManager.messageSetting("formats.unknown-colour");
    }

    public Map<String, String> commandContext(Player player, String... extraPairs) {
        Map<String, String> placeholders = basePlaceholders(extraPairs);
        placeholders.put("current_colour", hasEnabledColours() ? getActiveColour(player).getDisplayName()
                : configManager.messageSetting("formats.unknown-colour"));
        placeholders.put("default_colour", hasEnabledColours() ? getDefaultColour().getDisplayName()
                : configManager.messageSetting("formats.unknown-colour"));

        List<String> available = getUnlockedColours(player).stream().map(ChatColorDefinition::getDisplayName).toList();
        placeholders.put("available", available.isEmpty()
                ? configManager.messageSetting("formats.available-empty")
                : String.join(configManager.messageSetting("formats.available-separator"), available));
        return placeholders;
    }

    public String getResetColourKey() {
        return getDefaultColour().getKey();
    }

    public Map<String, String> basePlaceholders(String... extraPairs) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (int index = 0; index + 1 < extraPairs.length; index += 2) {
            placeholders.put(extraPairs[index], extraPairs[index + 1]);
        }
        return placeholders;
    }

    public ChatColorDefinition getActiveColour(Player player) {
        PlayerColorData data = playerDataManager.loadPlayer(player);
        String selected = data.getSelectedColour();
        if (data.isRevoked(selected) || !(data.hasUnlocked(selected) || ezColorsCompatService.hasPermissionAccess(player, selected))) {
            selected = configManager.getDefaultColourKey();
        }

        return colorRegistry.getEnabledColour(selected)
                .or(() -> colorRegistry.getEnabledColour(configManager.getDefaultColourKey()))
                .or(() -> colorRegistry.getEnabledColours().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No enabled colours are configured"));
    }

    private ChatColorDefinition getCachedActiveColour(Player player) {
        Optional<PlayerColorData> loadedData = playerDataManager.getLoaded(player.getUniqueId());
        String selected = loadedData
                .map(PlayerColorData::getSelectedColour)
                .orElse(configManager.getDefaultColourKey());
        if (loadedData.isPresent() && (loadedData.get().isRevoked(selected) || !loadedData.get().hasUnlocked(selected))) {
            selected = configManager.getDefaultColourKey();
        }

        return colorRegistry.getEnabledColour(selected)
                .or(() -> colorRegistry.getEnabledColour(configManager.getDefaultColourKey()))
                .or(() -> colorRegistry.getEnabledColours().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No enabled colours are configured"));
    }

    private ChatColorDefinition getDefaultColour() {
        return colorRegistry.getEnabledColour(configManager.getDefaultColourKey())
                .or(() -> colorRegistry.getEnabledColours().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No enabled colours are configured"));
    }

    private String resolveSelectedKey(Player player) {
        return getActiveColour(player).getKey();
    }

    public boolean hasEnabledColours() {
        return !colorRegistry.getEnabledColours().isEmpty();
    }
}

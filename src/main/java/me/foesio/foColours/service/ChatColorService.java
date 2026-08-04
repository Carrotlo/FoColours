package me.foesio.foColours.service;

import me.foesio.foColours.compat.EzColorsCompatService;
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
        return resolveSelectedKey(player).equals(key);
    }

    public boolean isDefaultSelected(Player player) {
        return resolveSelectedKey(player).equals(configManager.getDefaultColourKey());
    }

    public void selectColour(Player player, String key) {
        PlayerColorData data = playerDataManager.loadPlayer(player);
        data.ensureUnlocked(List.of(configManager.getDefaultColourKey()));
        if (ezColorsCompatService.isEnabled()) {
            ezColorsCompatService.syncPlayer(player, false);
        }
        if (!data.isRevoked(key) && ezColorsCompatService.hasPermissionAccess(player, key)) {
            playerDataManager.grantColour(player, key);
        }
        playerDataManager.setSelectedColour(player, key);
    }

    public void resetColour(Player player) {
        playerDataManager.loadPlayer(player).ensureUnlocked(configManager.getDefaultUnlockedColours());
        if (ezColorsCompatService.isEnabled()) {
            ezColorsCompatService.syncPlayer(player, false);
        }
        playerDataManager.setSelectedColour(player, configManager.getDefaultColourKey());
    }

    public boolean grantColour(KnownPlayer target, ChatColorDefinition colour) {
        return playerDataManager.grantColour(target, colour.getKey());
    }

    public boolean takeColour(KnownPlayer target, ChatColorDefinition colour) {
        Player onlineTarget = Bukkit.getPlayer(target.uuid());
        boolean hasLivePermissionAccess = onlineTarget != null && ezColorsCompatService.hasPermissionAccess(onlineTarget, colour.getKey());
        return playerDataManager.takeColour(target, colour.getKey(), configManager.getDefaultColourKey(), hasLivePermissionAccess);
    }

    public boolean isRainbowSelected(Player player) {
        return getActiveColour(player).getType() == ColorType.RAINBOW;
    }

    public boolean isCachedRainbowSelected(Player player) {
        return getCachedActiveColour(player).getType() == ColorType.RAINBOW;
    }

    public String colorizeRainbowChatMessage(String rawMessage) {
        return TextUtil.applyRainbow(TextUtil.stripColour(rawMessage), configManager.getRainbowPalette(), configManager.isRainbowAdvanceOnSpaces());
    }

    public String colorizeSelectedChatMessage(Player player, String rawMessage) {
        return getCachedActiveColour(player).applyToText(TextUtil.stripColour(rawMessage), configManager);
    }

    public String getPlaceholderValue(Player player) {
        return getCachedActiveColour(player).getChatFormatCode(configManager);
    }

    public String getPlaceholderValue(OfflinePlayer player) {
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
        return getDefaultColour().getChatFormatCode(configManager);
    }

    public String getPlaceholderPreview(Player player) {
        return getCachedActiveColour(player).getPreviewLabel(configManager);
    }

    public String getDefaultPlaceholderPreview() {
        return getDefaultColour().getPreviewLabel(configManager);
    }

    public String getPlaceholderDisplayName(Player player) {
        return getCachedActiveColour(player).getDisplayName();
    }

    public String getDefaultPlaceholderDisplayName() {
        return getDefaultColour().getDisplayName();
    }

    public Map<String, String> commandContext(Player player, String... extraPairs) {
        Map<String, String> placeholders = basePlaceholders(extraPairs);
        ChatColorDefinition active = getActiveColour(player);
        placeholders.put("current_colour", active.getDisplayName());
        placeholders.put("default_colour", colorRegistry.getEnabledColour(configManager.getDefaultColourKey())
                .map(ChatColorDefinition::getDisplayName)
                .orElse("Light Gray"));
        placeholders.put("available", String.join(", ", getUnlockedColours(player).stream().map(ChatColorDefinition::getKey).toList()));
        return placeholders;
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
}

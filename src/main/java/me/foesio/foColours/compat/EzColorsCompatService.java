package me.foesio.foColours.compat;

import me.foesio.foColours.color.ChatColorDefinition;
import me.foesio.foColours.color.ColorRegistry;
import me.foesio.foColours.color.ColorType;
import me.foesio.foColours.config.ConfigManager;
import me.foesio.foColours.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class EzColorsCompatService {

    private final ConfigManager configManager;
    private final ColorRegistry colorRegistry;
    private final PlayerDataManager playerDataManager;
    private final Map<String, String> legacyPermissionMap;
    private final Map<String, String> legacyNameMap;

    public EzColorsCompatService(ConfigManager configManager, ColorRegistry colorRegistry, PlayerDataManager playerDataManager) {
        this.configManager = configManager;
        this.colorRegistry = colorRegistry;
        this.playerDataManager = playerDataManager;
        this.legacyPermissionMap = buildLegacyPermissionMap();
        this.legacyNameMap = buildLegacyNameMap();
    }

    public boolean isEnabled() {
        return configManager.isEzColorsCompatEnabled();
    }

    public boolean shouldSyncOnJoin() {
        return configManager.isEzColorsSyncOnJoin();
    }

    public boolean hasPermissionAccess(Player player, String colourKey) {
        return resolvePermissionGrantedColours(player).contains(colourKey);
    }

    public Optional<ChatColorDefinition> findCompatibleColour(String input) {
        Optional<ChatColorDefinition> direct = colorRegistry.findEnabledColour(input);
        if (direct.isPresent()) {
            return direct;
        }

        String mappedKey = legacyNameMap.get(configManager.normalize(input));
        if (mappedKey == null) {
            return Optional.empty();
        }

        return colorRegistry.getEnabledColour(mappedKey);
    }

    public Set<String> resolvePermissionGrantedColours(Player player) {
        LinkedHashSet<String> granted = new LinkedHashSet<>();
        if (!isEnabled()) {
            return granted;
        }

        if (player.hasPermission("ezcolors.*")
                || player.hasPermission("ezcolors.color.*")) {
            for (ChatColorDefinition definition : colorRegistry.getEnabledColours()) {
                granted.add(definition.getKey());
            }
            return granted;
        }

        for (Map.Entry<String, String> entry : legacyPermissionMap.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                colorRegistry.getEnabledColour(entry.getValue()).ifPresent(definition -> granted.add(definition.getKey()));
            }
        }

        boolean allowCustomHexColours = player.hasPermission("ezcolors.color.custom")
                || player.hasPermission("ezcolors.color.hex");
        boolean allowRainbowFromShowPermission = configManager.isEzColorsShowRainbowUnlocksRainbow()
                && player.hasPermission("ezcolors.showrainbow");

        for (ChatColorDefinition definition : colorRegistry.getEnabledColours()) {
            if (allowCustomHexColours && definition.isCustom() && definition.getType() == ColorType.HEX) {
                granted.add(definition.getKey());
            }
            if (allowRainbowFromShowPermission && definition.getType() == ColorType.RAINBOW) {
                granted.add(definition.getKey());
            }
        }

        return granted;
    }

    public SyncResult syncPlayer(Player player) {
        return syncPlayer(player, true);
    }

    public SyncResult syncPlayer(Player player, boolean markConvertedWhenNoGrants) {
        if (!isEnabled()) {
            return new SyncResult(0, 0, 0, false);
        }

        Set<String> grants = resolvePermissionGrantedColours(player);
        return playerDataManager.syncLegacyPermissionGrants(
                player,
                grants,
                configManager.isEzColorsRevokeMissingPermissionGrants(),
                configManager.getDefaultColourKey(),
                markConvertedWhenNoGrants || !grants.isEmpty()
        );
    }

    public int syncOnlinePlayersIfConfigured() {
        if (!isEnabled() || !configManager.isEzColorsSyncOnReload()) {
            return 0;
        }

        return syncAllOnlinePlayers();
    }

    public int syncAllOnlinePlayers() {
        if (!isEnabled()) {
            return 0;
        }

        int synced = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayer(player);
            synced++;
        }
        return synced;
    }

    public record SyncResult(int added, int removed, int permissionGranted, boolean alreadyConverted) {
    }

    private Map<String, String> buildLegacyPermissionMap() {
        LinkedHashMap<String, String> permissions = new LinkedHashMap<>();
        permissions.put("ezcolors.color.darkred", "dark_red");
        permissions.put("ezcolors.color.green", "dark_green");
        permissions.put("ezcolors.color.blue", "blue");
        permissions.put("ezcolors.color.cyan", "dark_aqua");
        permissions.put("ezcolors.color.purple", "dark_purple");
        permissions.put("ezcolors.color.gold", "gold");
        permissions.put("ezcolors.color.darkgray", "dark_gray");
        permissions.put("ezcolors.color.red", "red");
        permissions.put("ezcolors.color.lime", "green");
        permissions.put("ezcolors.color.lightblue", "aqua");
        permissions.put("ezcolors.color.black", "black");
        permissions.put("ezcolors.color.pink", "light_purple");
        permissions.put("ezcolors.color.yellow", "yellow");
        permissions.put("ezcolors.color.gray", "light_gray");
        permissions.put("ezcolors.color.aqua", "aqua");
        permissions.put("ezcolors.color.white", "white");
        permissions.put("ezcolors.color.rainbow", "rainbow");
        return permissions;
    }

    private Map<String, String> buildLegacyNameMap() {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : legacyPermissionMap.entrySet()) {
            names.put(entry.getKey().replace("ezcolors.color.", ""), entry.getValue());
        }
        return names;
    }
}

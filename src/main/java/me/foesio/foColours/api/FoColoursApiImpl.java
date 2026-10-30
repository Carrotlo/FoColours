package me.foesio.foColours.api;

import me.foesio.foColours.FoColours;
import me.foesio.foColours.color.ChatColorDefinition;
import me.foesio.foColours.data.KnownPlayer;
import me.foesio.foColours.data.PlayerColorData;
import me.foesio.foColours.service.ChatColorService;
import me.foesio.foColours.util.TextUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class FoColoursApiImpl implements FoColoursApi {
    private final FoColours plugin;

    public FoColoursApiImpl(FoColours plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<ColourSnapshot> enabledColours() {
        requireEnabled();
        return plugin.getColorRegistry().getEnabledColours().stream().map(this::snapshot).toList();
    }

    @Override
    public Optional<ColourSnapshot> findEnabledColour(String keyOrAlias) {
        requireEnabled();
        return plugin.getEzColorsCompatService().findCompatibleColour(keyOrAlias).map(this::snapshot);
    }

    @Override
    public PlayerColourSnapshot playerColours(Player player) {
        requireEnabled();
        Objects.requireNonNull(player, "player");
        PlayerColorData data = plugin.getPlayerDataManager().loadPlayer(player);
        ChatColorService service = plugin.getChatColorService();
        String effective = service.hasEnabledColours() ? service.getActiveColour(player).getKey() : "";
        return new PlayerColourSnapshot(data.getSelectedColour(), effective,
                service.getUnlockedColours(player).stream().map(ChatColorDefinition::getKey).collect(java.util.stream.Collectors.toSet()));
    }

    @Override
    public ColourChangeResult grantColour(OfflinePlayer player, String keyOrAlias) {
        requireEnabled();
        Objects.requireNonNull(player, "player");
        if (!plugin.getChatColorService().hasEnabledColours()) return ColourChangeResult.NO_ENABLED_COLOURS;
        Optional<ChatColorDefinition> colour = plugin.getEzColorsCompatService().findCompatibleColour(keyOrAlias);
        if (colour.isEmpty()) return ColourChangeResult.UNKNOWN_OR_DISABLED_COLOUR;
        return plugin.getChatColorService().grantColour(known(player), colour.get(), ColourChangeCause.API)
                ? ColourChangeResult.CHANGED : ColourChangeResult.NO_CHANGE;
    }

    @Override
    public ColourChangeResult takeColour(OfflinePlayer player, String keyOrAlias) {
        requireEnabled();
        Objects.requireNonNull(player, "player");
        if (!plugin.getChatColorService().hasEnabledColours()) return ColourChangeResult.NO_ENABLED_COLOURS;
        Optional<ChatColorDefinition> colour = plugin.getEzColorsCompatService().findCompatibleColour(keyOrAlias);
        if (colour.isEmpty()) return ColourChangeResult.UNKNOWN_OR_DISABLED_COLOUR;
        if (colour.get().getKey().equals(plugin.getConfigManager().getDefaultColourKey())) {
            return ColourChangeResult.DEFAULT_PROTECTED;
        }
        return plugin.getChatColorService().takeColour(known(player), colour.get(), ColourChangeCause.API)
                ? ColourChangeResult.CHANGED : ColourChangeResult.NO_CHANGE;
    }

    @Override
    public ColourChangeResult selectColour(Player player, String keyOrAlias) {
        requireEnabled();
        Objects.requireNonNull(player, "player");
        ChatColorService service = plugin.getChatColorService();
        if (!service.hasEnabledColours()) return ColourChangeResult.NO_ENABLED_COLOURS;
        Optional<ChatColorDefinition> colour = plugin.getEzColorsCompatService().findCompatibleColour(keyOrAlias);
        if (colour.isEmpty()) return ColourChangeResult.UNKNOWN_OR_DISABLED_COLOUR;
        if (!service.hasUnlocked(player, colour.get().getKey())) return ColourChangeResult.NOT_UNLOCKED;
        if (plugin.getPlayerDataManager().loadPlayer(player).getSelectedColour().equals(colour.get().getKey())) {
            return ColourChangeResult.NO_CHANGE;
        }
        return service.selectColour(player, colour.get().getKey(), ColourChangeCause.API)
                ? ColourChangeResult.CHANGED : ColourChangeResult.NOT_UNLOCKED;
    }

    @Override
    public ColourChangeResult resetColour(Player player) {
        requireEnabled();
        Objects.requireNonNull(player, "player");
        if (!plugin.getConfigManager().isResetEnabled()) return ColourChangeResult.RESET_DISABLED;
        ChatColorService service = plugin.getChatColorService();
        if (!service.hasEnabledColours()) return ColourChangeResult.NO_ENABLED_COLOURS;
        String oldKey = plugin.getPlayerDataManager().loadPlayer(player).getSelectedColour();
        if (oldKey.equals(service.getResetColourKey())) {
            return ColourChangeResult.NO_CHANGE;
        }
        service.resetColour(player, ColourChangeCause.API);
        return oldKey.equals(plugin.getPlayerDataManager().loadPlayer(player).getSelectedColour())
                ? ColourChangeResult.NO_CHANGE : ColourChangeResult.CHANGED;
    }

    @Override
    public String renderText(Player player, String text) {
        requireEnabled();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(text, "text");
        ChatColorService service = plugin.getChatColorService();
        return service.hasEnabledColours()
                ? service.getActiveColour(player).applyToText(TextUtil.stripColour(text), plugin.getConfigManager())
                : text;
    }

    private ColourSnapshot snapshot(ChatColorDefinition colour) {
        return new ColourSnapshot(colour.getKey(), colour.getDisplayName(), ColourType.valueOf(colour.getType().name()),
                colour.getValue(), colour.getAliases());
    }

    private KnownPlayer known(OfflinePlayer player) {
        return new KnownPlayer(player.getUniqueId(), player.getName() == null ? player.getUniqueId().toString() : player.getName());
    }

    private void requireEnabled() {
        if (!plugin.isEnabled()) throw new IllegalStateException("FoColours is disabled");
    }
}

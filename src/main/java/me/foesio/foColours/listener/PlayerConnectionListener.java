package me.foesio.foColours.listener;

import me.foesio.foColours.FoColours;
import me.foesio.foColours.compat.EzColorsCompatService;
import me.foesio.foColours.data.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {

    private final FoColours plugin;
    private final PlayerDataManager playerDataManager;
    private final EzColorsCompatService ezColorsCompatService;

    public PlayerConnectionListener(FoColours plugin, PlayerDataManager playerDataManager, EzColorsCompatService ezColorsCompatService) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.ezColorsCompatService = ezColorsCompatService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        playerDataManager.loadPlayer(event.getPlayer());
        if (ezColorsCompatService.isEnabled() && ezColorsCompatService.shouldSyncOnJoin()) {
            scheduleSyncRetry(event.getPlayer(), 1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerDataManager.saveAndUnload(event.getPlayer());
    }

    private void scheduleSyncRetry(Player player, int attempt) {
        long delay = plugin.getConfigManager().getEzColorsJoinSyncDelayTicks() * attempt;
        plugin.coreScheduler().runLaterForPlayer(player, () -> {
            if (player == null || !player.isOnline()) {
                return;
            }

            EzColorsCompatService.SyncResult result = ezColorsCompatService.syncPlayer(player, attempt >= 3);
            if (!result.alreadyConverted() && result.permissionGranted() == 0 && attempt < 3) {
                scheduleSyncRetry(player, attempt + 1);
            }
        }, delay);
    }
}

package me.foesio.foColours.listener;

import me.foesio.foColours.config.ConfigManager;
import me.foesio.foColours.service.ChatColorService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ChatListener implements Listener {

    private final ChatColorService service;
    private final ConfigManager configManager;

    public ChatListener(ChatColorService service, ConfigManager configManager) {
        this.service = service;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (configManager.isAutoChatMode()) {
            event.setMessage(service.colorizeSelectedChatMessage(event.getPlayer(), event.getMessage()));
            return;
        }

        if (service.isCachedRainbowSelected(event.getPlayer())) {
            event.setMessage(service.colorizeRainbowChatMessage(event.getMessage()));
        }
    }
}

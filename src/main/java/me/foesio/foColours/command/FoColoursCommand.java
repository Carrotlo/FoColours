package me.foesio.foColours.command;

import me.foesio.core.command.FoAdminArguments;
import me.foesio.foColours.FoColours;
import me.foesio.foColours.compat.EzColorsCompatService;
import me.foesio.foColours.color.ChatColorDefinition;
import me.foesio.foColours.gui.PlayerColorMenu;
import me.foesio.foColours.service.ChatColorService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FoColoursCommand implements CommandExecutor, TabCompleter {

    private final FoColours plugin;
    private final ChatColorService service;
    private final EzColorsCompatService compatService;

    public FoColoursCommand(FoColours plugin) {
        this.plugin = plugin;
        this.service = plugin.getChatColorService();
        this.compatService = plugin.getEzColorsCompatService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("focolours.use")) {
            sender.sendMessage(plugin.getConfigManager().message("messages.no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().message("messages.player-only"));
            return true;
        }

        if (args.length == 0) {
            if (plugin.getConfigManager().isPlayerGuiEnabled()) {
                new PlayerColorMenu(plugin).open(player);
            } else {
                player.sendMessage(String.join("\n", plugin.getConfigManager().messageList("messages.player-help", service.commandContext(player))));
            }
            return true;
        }

        String input = args[0];
        if (input.equalsIgnoreCase("reset")) {
            if (service.isDefaultSelected(player)) {
                player.sendMessage(plugin.getConfigManager().message("messages.already-default", service.commandContext(player)));
                return true;
            }

            service.resetColour(player);
            player.sendMessage(plugin.getConfigManager().message("messages.reset-success", service.commandContext(player)));
            return true;
        }

        Optional<ChatColorDefinition> definition = compatService.findCompatibleColour(input);
        if (definition.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().message("messages.unknown-colour", service.commandContext(player, "colour", input)));
            return true;
        }

        if (!service.hasUnlocked(player, definition.get().getKey())) {
            player.sendMessage(plugin.getConfigManager().message("messages.not-unlocked", service.commandContext(player, "colour", definition.get().getDisplayName())));
            return true;
        }

        if (service.isSelected(player, definition.get().getKey())) {
            player.sendMessage(plugin.getConfigManager().message("messages.already-selected", service.commandContext(player, "colour", definition.get().getDisplayName())));
            return true;
        }

        service.selectColour(player, definition.get().getKey());
        player.sendMessage(plugin.getConfigManager().message("messages.select-success", service.commandContext(player, "colour", definition.get().getDisplayName())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("focolours.use")) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            values.add("reset");
            values.addAll(service.getUnlockedColours(player).stream().map(ChatColorDefinition::getKey).toList());
            return FoAdminArguments.completeOptions(values, args[0]);
        }

        return List.of();
    }
}

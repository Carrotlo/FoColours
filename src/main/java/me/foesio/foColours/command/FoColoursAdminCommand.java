package me.foesio.foColours.command;

import me.foesio.core.command.FoAdminArguments;
import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminCommandContext;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.foColours.FoColours;
import me.foesio.foColours.color.ChatColorDefinition;
import me.foesio.foColours.compat.EzColorsCompatService;
import me.foesio.foColours.data.KnownPlayer;
import me.foesio.foColours.gui.AdminEditorMenu;
import me.foesio.foColours.service.ChatColorService;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

public final class FoColoursAdminCommand {

    private static final String PERMISSION = "focolours.admin";

    private final FoColours plugin;
    private final ChatColorService service;
    private final EzColorsCompatService compatService;

    public FoColoursAdminCommand(FoColours plugin) {
        this.plugin = plugin;
        this.service = plugin.getChatColorService();
        this.compatService = plugin.getEzColorsCompatService();
    }

    public void register() {
        FoAdminCommand.builder(plugin, plugin.getMessages())
                .commandName("focoloursadmin")
                .permission(PERMISSION)
                .versionCommand(false)
                .adminSounds(plugin.getAdminSounds())
                .adminMessages(adminMessages())
                .defaultExecutor(this::sendHelp)
                .addSubcommand(FoAdminSubcommand.builder("version", this::version)
                        .usage("version")
                        .build())
                .addSubcommand(FoAdminSubcommand.builder("reload", this::reload)
                        .usage("reload")
                        .build())
                .addSubcommand(FoAdminSubcommand.builder("editor", this::editor)
                        .usage("editor")
                        .playerOnly()
                        .build())
                .addSubcommand(FoAdminSubcommand.builder("give", this::give)
                        .usage("give <player> <colour>")
                        .tabCompleter(this::completeGiveTake)
                        .build())
                .addSubcommand(FoAdminSubcommand.builder("take", this::take)
                        .usage("take <player> <colour>")
                        .tabCompleter(this::completeGiveTake)
                        .build())
                .addSubcommand(FoAdminSubcommand.builder("convertezcolors", this::convertEzColors)
                        .aliases("importezcolors", "migrateezcolors")
                        .usage("convertezcolors <enable|disable|status>")
                        .tabCompleter(context -> List.of("enable", "disable", "status"))
                        .build())
                .register();
    }

    private FoAdminMessages adminMessages() {
        return FoAdminMessages.builder()
                .generalNoPermission("messages.no-permission", "{prefix} {bad}You do not have permission {muted}to do that.")
                .generalPlayerOnly("messages.player-only", "{prefix} {bad}Only players {muted}can use this.")
                .usage("messages.admin-help", "{prefix} {muted}Use {theme}/focoloursadmin <version|reload|editor|give|take|convertezcolors>{muted}.")
                .commandMissing("messages.admin-command-missing", "{prefix} {bad}Admin command is missing from plugin.yml: {theme}{command}")
                .commandFailed("messages.admin-command-failed", "{prefix} {bad}Command failed. {muted}{error}")
                .build();
    }

    private boolean sendHelp(FoAdminCommandContext context) {
        context.sender().sendMessage(String.join("\n", plugin.getConfigManager().messageList("messages.admin-help", service.basePlaceholders())));
        return true;
    }

    private boolean version(FoAdminCommandContext context) {
        plugin.getUpdateNotices().checkAndSendVersion(context.sender());
        return true;
    }

    private boolean reload(FoAdminCommandContext context) {
        plugin.sendReloadResult(context.sender(), plugin.reloadPluginState());
        return true;
    }

    private boolean editor(FoAdminCommandContext context) {
        new AdminEditorMenu(plugin).open(context.playerOrNull());
        return true;
    }

    private boolean convertEzColors(FoAdminCommandContext context) {
        CommandSender sender = context.sender();
        String mode = context.subArg(0);
        if (mode.isBlank()) {
            sender.sendMessage(plugin.getConfigManager().message("messages.ezcolors-toggle-usage"));
            plugin.getAdminSounds().updateError(sender);
            return true;
        }

        return switch (FoAdminArguments.normalize(mode)) {
            case "enable", "enabled", "on", "true" -> enableEzColors(sender);
            case "disable", "disabled", "off", "false" -> disableEzColors(sender);
            case "status" -> {
                sender.sendMessage(plugin.getConfigManager().message("messages.ezcolors-toggle-status", service.basePlaceholders(
                        "state", compatService.isEnabled()
                                ? plugin.getConfigManager().message("gui.states.enabled")
                                : plugin.getConfigManager().message("gui.states.disabled")
                )));
                yield true;
            }
            default -> {
                sender.sendMessage(plugin.getConfigManager().message("messages.ezcolors-toggle-usage"));
                plugin.getAdminSounds().updateError(sender);
                yield true;
            }
        };
    }

    private boolean enableEzColors(CommandSender sender) {
        plugin.getConfigManager().set("compatibility.ezcolors.enabled", true);
        FoReloadResult result = plugin.reloadPluginState();
        if (!result.successful()) {
            plugin.sendReloadResult(sender, result);
            return true;
        }

        int converted = compatService.syncAllOnlinePlayers();
        sender.sendMessage(plugin.getConfigManager().message("messages.ezcolors-toggle-enabled", service.basePlaceholders(
                "count", Integer.toString(converted)
        )));
        return true;
    }

    private boolean disableEzColors(CommandSender sender) {
        plugin.getConfigManager().set("compatibility.ezcolors.enabled", false);
        FoReloadResult result = plugin.reloadPluginState();
        if (!result.successful()) {
            plugin.sendReloadResult(sender, result);
            return true;
        }

        sender.sendMessage(plugin.getConfigManager().message("messages.ezcolors-toggle-disabled"));
        return true;
    }

    private boolean give(FoAdminCommandContext context) {
        if (context.subArgs().length < 2) {
            context.sender().sendMessage(plugin.getConfigManager().message("messages.admin-usage-give"));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        Optional<KnownPlayer> target = plugin.getPlayerDataManager().resolveKnownPlayer(context.subArg(0));
        if (target.isEmpty()) {
            context.sender().sendMessage(plugin.getConfigManager().message("messages.unknown-player", service.basePlaceholders("player", context.subArg(0))));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        Optional<ChatColorDefinition> colour = compatService.findCompatibleColour(context.subArg(1));
        if (colour.isEmpty()) {
            context.sender().sendMessage(plugin.getConfigManager().message("messages.unknown-colour", service.basePlaceholders("colour", context.subArg(1))));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        if (!service.grantColour(target.get(), colour.get())) {
            context.sender().sendMessage(plugin.getConfigManager().message("messages.already-unlocked", service.basePlaceholders(
                    "player", target.get().name(),
                    "colour", colour.get().getDisplayName()
            )));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        context.sender().sendMessage(plugin.getConfigManager().message("messages.admin-give-success", service.basePlaceholders(
                "player", target.get().name(),
                "colour", colour.get().getDisplayName()
        )));
        return true;
    }

    private boolean take(FoAdminCommandContext context) {
        if (context.subArgs().length < 2) {
            context.sender().sendMessage(plugin.getConfigManager().message("messages.admin-usage-take"));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        Optional<KnownPlayer> target = plugin.getPlayerDataManager().resolveKnownPlayer(context.subArg(0));
        if (target.isEmpty()) {
            context.sender().sendMessage(plugin.getConfigManager().message("messages.unknown-player", service.basePlaceholders("player", context.subArg(0))));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        Optional<ChatColorDefinition> colour = compatService.findCompatibleColour(context.subArg(1));
        if (colour.isEmpty()) {
            context.sender().sendMessage(plugin.getConfigManager().message("messages.unknown-colour", service.basePlaceholders("colour", context.subArg(1))));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        if (colour.get().getKey().equals(plugin.getConfigManager().getDefaultColourKey())) {
            context.sender().sendMessage(plugin.getConfigManager().message("messages.cannot-take-default"));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        if (!service.takeColour(target.get(), colour.get())) {
            context.sender().sendMessage(plugin.getConfigManager().message("messages.not-unlocked", service.basePlaceholders(
                    "player", target.get().name(),
                    "colour", colour.get().getDisplayName()
            )));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        context.sender().sendMessage(plugin.getConfigManager().message("messages.admin-take-success", service.basePlaceholders(
                "player", target.get().name(),
                "colour", colour.get().getDisplayName()
        )));
        return true;
    }

    private List<String> completeGiveTake(FoAdminCommandContext context) {
        if (context.args().length == 2) {
            return plugin.getPlayerDataManager().getKnownPlayerNames();
        }
        if (context.args().length == 3) {
            return plugin.getColorRegistry().getEnabledColours().stream()
                    .map(ChatColorDefinition::getKey)
                    .toList();
        }
        return List.of();
    }
}

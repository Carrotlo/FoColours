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
                .reloadSuccess("messages.reload-complete", "{prefix} :emerald: {good}Reloaded {theme}FoColours {muted}from config.yml, messages.yml, and guis/.")
                .reloadFailed("messages.reload-failed", "{prefix} :redstone: {bad}Reload failed at {theme}{step}{bad}. {muted}{error}")
                .editorOpened("messages.editor-opened", "{prefix} :book: {theme}Editor opened.")
                .build();
    }

    private boolean sendHelp(FoAdminCommandContext context) {
        plugin.getConfigManager().sendMessageList(context.sender(), "messages.admin-help", service.basePlaceholders());
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
            plugin.getConfigManager().sendMessage(sender, "messages.ezcolors-toggle-usage");
            plugin.getAdminSounds().updateError(sender);
            return true;
        }

        return switch (FoAdminArguments.normalize(mode)) {
            case "enable", "enabled", "on", "true" -> enableEzColors(sender);
            case "disable", "disabled", "off", "false" -> disableEzColors(sender);
            case "status" -> {
                plugin.getConfigManager().sendMessage(sender, "messages.ezcolors-toggle-status", service.basePlaceholders(
                        "state", compatService.isEnabled()
                                ? plugin.getConfigManager().message("gui.states.enabled")
                                : plugin.getConfigManager().message("gui.states.disabled")
                ));
                yield true;
            }
            default -> {
                plugin.getConfigManager().sendMessage(sender, "messages.ezcolors-toggle-usage");
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
        plugin.getConfigManager().sendMessage(sender, "messages.ezcolors-toggle-enabled", service.basePlaceholders(
                "count", Integer.toString(converted)
        ));
        return true;
    }

    private boolean disableEzColors(CommandSender sender) {
        plugin.getConfigManager().set("compatibility.ezcolors.enabled", false);
        FoReloadResult result = plugin.reloadPluginState();
        if (!result.successful()) {
            plugin.sendReloadResult(sender, result);
            return true;
        }

        plugin.getConfigManager().sendMessage(sender, "messages.ezcolors-toggle-disabled");
        return true;
    }

    private boolean give(FoAdminCommandContext context) {
        if (context.subArgs().length < 2) {
            plugin.getConfigManager().sendMessage(context.sender(), "messages.admin-usage-give");
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        Optional<KnownPlayer> target = plugin.getPlayerDataManager().resolveKnownPlayer(context.subArg(0));
        if (target.isEmpty()) {
            plugin.getConfigManager().sendMessage(context.sender(), "messages.unknown-player", service.basePlaceholders("player", context.subArg(0)));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        Optional<ChatColorDefinition> colour = compatService.findCompatibleColour(context.subArg(1));
        if (colour.isEmpty()) {
            plugin.getConfigManager().sendMessage(context.sender(), "messages.unknown-colour", service.basePlaceholders("colour", context.subArg(1)));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        if (!service.grantColour(target.get(), colour.get())) {
            plugin.getConfigManager().sendMessage(context.sender(), "messages.already-unlocked", service.basePlaceholders(
                    "player", target.get().name(),
                    "colour", colour.get().getDisplayName()
            ));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        plugin.getConfigManager().sendMessage(context.sender(), "messages.admin-give-success", service.basePlaceholders(
                "player", target.get().name(),
                "colour", colour.get().getDisplayName()
        ));
        return true;
    }

    private boolean take(FoAdminCommandContext context) {
        if (context.subArgs().length < 2) {
            plugin.getConfigManager().sendMessage(context.sender(), "messages.admin-usage-take");
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        Optional<KnownPlayer> target = plugin.getPlayerDataManager().resolveKnownPlayer(context.subArg(0));
        if (target.isEmpty()) {
            plugin.getConfigManager().sendMessage(context.sender(), "messages.unknown-player", service.basePlaceholders("player", context.subArg(0)));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        Optional<ChatColorDefinition> colour = compatService.findCompatibleColour(context.subArg(1));
        if (colour.isEmpty()) {
            plugin.getConfigManager().sendMessage(context.sender(), "messages.unknown-colour", service.basePlaceholders("colour", context.subArg(1)));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        if (colour.get().getKey().equals(plugin.getConfigManager().getDefaultColourKey())) {
            plugin.getConfigManager().sendMessage(context.sender(), "messages.cannot-take-default");
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        if (!service.takeColour(target.get(), colour.get())) {
            plugin.getConfigManager().sendMessage(context.sender(), "messages.not-unlocked", service.basePlaceholders(
                    "player", target.get().name(),
                    "colour", colour.get().getDisplayName()
            ));
            plugin.getAdminSounds().updateError(context.sender());
            return true;
        }

        plugin.getConfigManager().sendMessage(context.sender(), "messages.admin-take-success", service.basePlaceholders(
                "player", target.get().name(),
                "colour", colour.get().getDisplayName()
        ));
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

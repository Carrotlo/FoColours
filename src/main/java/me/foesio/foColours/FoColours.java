package me.foesio.foColours;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.dialog.FallbackDialogService;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.ChatPromptManager;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foColours.color.ColorRegistry;
import me.foesio.foColours.compat.EzColorsCompatService;
import me.foesio.foColours.command.FoColoursAdminCommand;
import me.foesio.foColours.command.FoColoursCommand;
import me.foesio.foColours.config.ConfigManager;
import me.foesio.foColours.data.PlayerDataManager;
import me.foesio.foColours.gui.MenuListener;
import me.foesio.foColours.listener.ChatListener;
import me.foesio.foColours.listener.PlayerConnectionListener;
import me.foesio.foColours.service.ChatColorService;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class FoColours extends JavaPlugin {

    private static final int BSTATS_ID = 33114;
    private static final String MODRINTH_PROJECT_ID = "pTijkOe1";
    private static final String INVISIBLE_COLOUR_CODE_BREAK = "\u200B";
    private static final String VARIATION_SELECTOR_TEXT = "\uFE0E";
    private static final String VISIBLE_COLOUR_RESTORE_TOKEN = "{white}";

    private FoCoreContext core;
    private FoMessageService messages;
    private ConfigManager configManager;
    private ColorRegistry colorRegistry;
    private PlayerDataManager playerDataManager;
    private ChatColorService chatColorService;
    private ChatPromptManager chatPromptManager;
    private EzColorsCompatService ezColorsCompatService;
    private DialogService dialogService;
    private UpdateNoticeService updateNotices;
    private FoSoundService sounds;
    private FoEditorSounds editorSounds;
    private FoGuiSounds guiSounds;
    private FoAdminSounds adminSounds;
    private boolean placeholderHookRegistered;
    private final Set<UUID> nativeDialogFallbackWarnedPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.messages = FoMessageService.load(this, messageMigrations());
        this.configManager.setMessages(messages);
        refreshCoreContext();
        this.colorRegistry = new ColorRegistry(configManager);
        this.playerDataManager = new PlayerDataManager(this, configManager);
        this.ezColorsCompatService = new EzColorsCompatService(configManager, colorRegistry, playerDataManager);
        this.chatColorService = new ChatColorService(configManager, colorRegistry, playerDataManager, ezColorsCompatService);
        this.chatPromptManager = new ChatPromptManager(this, core.scheduler());
        refreshDialogService();

        colorRegistry.reload();
        playerDataManager.initialize();
        ezColorsCompatService.syncOnlinePlayersIfConfigured();

        registerCommands();
        registerListeners();
        refreshPlaceholderHook();
    }

    @Override
    public void onDisable() {
        placeholderHookRegistered = false;

        if (playerDataManager != null) {
            playerDataManager.flushAll();
        }
        if (chatPromptManager != null) {
            chatPromptManager.close();
            chatPromptManager = null;
        }

        messages = null;
        updateNotices = null;
        if (core != null) {
            core.close();
            core = null;
        }
    }

    public FoReloadResult reloadPluginState() {
        FoReloadResult result = FoReloadRegistry.create()
                .add("config", configManager::reload)
                .addMessages(messages)
                .add("core", this::refreshCoreContext)
                .add("sounds", () -> sounds.reload())
                .add("colours", colorRegistry::reload)
                .add("player-data", () -> playerDataManager.normalizeOnlineSelections(colorRegistry, configManager.getDefaultColourKey()))
                .add("ezcolors", ezColorsCompatService::syncOnlinePlayersIfConfigured)
                .add("placeholders", this::refreshPlaceholderHook)
                .add("dialogs", this::refreshDialogService)
                .add("admin commands", this::registerCommands)
                .reload();
        if (!result.successful()) {
            logWarning("Reload failed at " + result.failedStep() + ": " + result.errorMessage());
        }
        return result;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public FoMessageService getMessages() {
        return messages;
    }

    public ColorRegistry getColorRegistry() {
        return colorRegistry;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public ChatColorService getChatColorService() {
        return chatColorService;
    }

    public boolean openEditorTextInput(
            Player player,
            TextDialogRequest request,
            Predicate<String> validator,
            Consumer<String> onAccept,
            Runnable reopenAction
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(onAccept, "onAccept");
        Objects.requireNonNull(reopenAction, "reopenAction");

        boolean openedNative = EditorDialogInputs.openTextFromInventory(
                this,
                core.inventoryCloseSuppressor(),
                dialogService,
                player,
                request,
                input -> {
                    if (input.length() > request.maxLength() || !validator.test(input)) {
                        editorSounds.error(player);
                        player.sendMessage(configManager.message("messages.prompt-invalid"));
                        openEditorTextInput(player, request, validator, onAccept, reopenAction);
                        return;
                    }

                    onAccept.accept(input);
                    reopenAction.run();
                },
                () -> {
                    player.sendMessage(configManager.message("messages.prompt-cancelled"));
                    editorSounds.back(player);
                    reopenAction.run();
                }
        );
        if (openedNative) {
            editorSounds.open(player);
        }
        return openedNative;
    }

    public boolean consumeSuppressedInventoryClose(Player player) {
        return core != null && core.inventoryCloseSuppressor().consumeSuppressedClose(player);
    }

    public EzColorsCompatService getEzColorsCompatService() {
        return ezColorsCompatService;
    }

    public UpdateNoticeService getUpdateNotices() {
        return updateNotices;
    }

    public FoSoundService getSounds() {
        return sounds;
    }

    public FoEditorSounds getEditorSounds() {
        return editorSounds;
    }

    public FoGuiSounds getGuiSounds() {
        return guiSounds;
    }

    public FoAdminSounds getAdminSounds() {
        return adminSounds;
    }

    public FoScheduler coreScheduler() {
        return core.scheduler();
    }

    public void sendReloadResult(CommandSender sender, FoReloadResult result) {
        if (result.successful()) {
            sender.sendMessage(configManager.message("messages.reload-complete"));
            if (sender instanceof Player player) {
                adminSounds.reload(player);
            }
            return;
        }

        sender.sendMessage(configManager.message("messages.reload-failed", Map.of(
                "step", result.failedStep(),
                "error", result.errorMessage()
        )));
        if (sender instanceof Player player) {
            adminSounds.reloadError(player);
        }
    }

    public void logInfo(String message) {
        getLogger().info("[" + getName() + "] " + message);
    }

    public void logWarning(String message) {
        getLogger().warning("[" + getName() + "] " + message);
    }

    private void registerCommands() {
        FoColoursCommand coloursCommand = new FoColoursCommand(this);

        PluginCommand mainCommand = Objects.requireNonNull(getCommand("focolours"), "Missing focolours command");
        mainCommand.setExecutor(coloursCommand);
        mainCommand.setTabCompleter(coloursCommand);

        new FoColoursAdminCommand(this).register();
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new MenuListener(this), this);
        pluginManager.registerEvents(new PlayerConnectionListener(this, playerDataManager, ezColorsCompatService), this);
        pluginManager.registerEvents(new ChatListener(chatColorService, configManager), this);
    }

    private void refreshCoreContext() {
        if (core != null) {
            core.close();
        }

        this.core = FoPluginCore.create(this);
        this.sounds = core.createSounds();
        this.editorSounds = FoEditorSounds.create(sounds);
        this.guiSounds = FoGuiSounds.create(sounds);
        this.adminSounds = FoAdminSounds.create(sounds);
        this.core.metrics(BSTATS_ID);
        if (updateNotices != null) {
            HandlerList.unregisterAll(updateNotices);
        }
        this.updateNotices = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID, adminSounds).start();
    }

    private void refreshDialogService() {
        NativeDialogSupport nativeDialogs = core.nativeDialogs();
        DialogService fallback = new FallbackDialogService(
                nativeDialogs,
                (player, request, onClose) -> runIfPresent(onClose),
                (player, request, onConfirm, onCancel) -> runIfPresent(onCancel),
                this::openChatTextFallback
        );
        nativeDialogFallbackWarnedPlayers.clear();
        core.warnIfNativeDialogsUnavailable();
        this.dialogService = core.createDialogService(fallback);
    }

    private void openChatTextFallback(Player player, TextDialogRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        warnNativeDialogFallback(player);
        chatPromptManager.openRaw(
                player,
                request.body(),
                input -> {
                    if (onSubmit != null) {
                        onSubmit.accept(input);
                    }
                },
                () -> runIfPresent(onCancel)
        );
    }

    private void warnNativeDialogFallback(Player player) {
        NativeDialogSupport nativeDialogs = core.nativeDialogs();
        if (!nativeDialogs.configEnabled()
                || nativeDialogs.canUseNativeDialogs()
                || !nativeDialogs.warnOnFallback()
                || !nativeDialogFallbackWarnedPlayers.add(player.getUniqueId())) {
            return;
        }

        player.sendMessage(configManager.message("messages.native-dialog-fallback"));
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .add(this::migrateLegacyTextConfigToMessages)
                .add(this::migrateStyleTokensToCoreTokens)
                .add(this::prefixLegacyMessageTemplates)
                .add(this::cleanupAddCustomColourPromptText)
                .remove("messages.prefix")
                .remove("messages.version")
                .remove("messages.update-available")
                .remove("messages.reset-disabled")
                .remove("messages.prompts.edit-prefix")
                .remove("messages.prompts.edit-player-title")
                .remove("messages.prompts.edit-admin-title")
                .build();
    }

    private boolean migrateLegacyTextConfigToMessages(FileConfiguration messageConfig) {
        FileConfiguration mainConfig = configManager.getConfig();
        boolean messagesChanged = copySectionValues(mainConfig, messageConfig, "messages", "messages", true);
        messagesChanged |= copySectionValues(mainConfig, messageConfig, "style.tokens", "tokens", true);

        boolean configChanged = removeSection(mainConfig, "messages");
        configChanged |= removeSection(mainConfig, "style");
        if (configChanged) {
            saveConfig();
        }

        return messagesChanged;
    }

    private boolean migrateStyleTokensToCoreTokens(FileConfiguration messageConfig) {
        ConfigurationSection legacyTokens = messageConfig.getConfigurationSection("style.tokens");
        if (legacyTokens == null) {
            return false;
        }

        boolean changed = false;
        for (String key : legacyTokens.getKeys(false)) {
            if (legacyTokens.isConfigurationSection(key)) {
                continue;
            }
            String targetPath = "tokens." + key;
            Object value = legacyTokens.get(key);
            if (!Objects.equals(messageConfig.get(targetPath), value)) {
                messageConfig.set(targetPath, value);
                changed = true;
            }
        }

        messageConfig.set("style.tokens", null);
        ConfigurationSection style = messageConfig.getConfigurationSection("style");
        if (style == null || style.getKeys(false).isEmpty()) {
            messageConfig.set("style", null);
        }
        return true;
    }

    private boolean prefixLegacyMessageTemplates(FileConfiguration messageConfig) {
        ConfigurationSection section = messageConfig.getConfigurationSection("messages");
        if (section == null) {
            return false;
        }

        boolean changed = false;
        for (String key : section.getKeys(true)) {
            String path = "messages." + key;
            if (messageConfig.isConfigurationSection(path)) {
                continue;
            }

            if (messageConfig.isList(path)) {
                if (path.equals("messages.prompts.add-custom-colour")) {
                    continue;
                }
                java.util.List<String> current = messageConfig.getStringList(path);
                java.util.List<String> prefixed = current.stream()
                        .map(this::prefixLegacyMessageTemplate)
                        .toList();
                if (!current.equals(prefixed)) {
                    messageConfig.set(path, prefixed);
                    changed = true;
                }
                continue;
            }

            if (!messageConfig.isString(path)) {
                continue;
            }

            String current = messageConfig.getString(path, "");
            String prefixed = prefixLegacyMessageTemplate(current);
            if (!Objects.equals(current, prefixed)) {
                messageConfig.set(path, prefixed);
                changed = true;
            }
        }
        return changed;
    }

    private String prefixLegacyMessageTemplate(String template) {
        if (template == null || template.isBlank() || template.contains("{prefix}")) {
            return template;
        }
        return "{prefix} " + template;
    }

    private boolean cleanupAddCustomColourPromptText(FileConfiguration messageConfig) {
        String path = "messages.prompts.add-custom-colour";
        if (!messageConfig.isList(path)) {
            return false;
        }

        java.util.List<String> current = messageConfig.getStringList(path);
        java.util.List<String> cleaned = current.stream()
                .map(this::cleanupAddCustomColourPromptLine)
                .toList();
        if (current.equals(cleaned)) {
            return false;
        }

        messageConfig.set(path, cleaned);
        return true;
    }

    private String cleanupAddCustomColourPromptLine(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }

        String cleaned = line;
        if (cleaned.startsWith("{prefix}")) {
            cleaned = cleaned.substring("{prefix}".length()).stripLeading();
        }
        String visibleLine = normalizeLiteralColourExamples(cleaned)
                .replace("{muted}", "")
                .replace("{theme}", "")
                .replace("{white}", "")
                .replace("{good}", "")
                .replace("{bad}", "")
                .replaceAll("\\s+", " ")
                .trim();
        return escapeLiteralColourExamples(visibleLine);
    }

    private String escapeLiteralColourExamples(String line) {
        String plainLine = normalizeLiteralColourExamples(line);
        return plainLine
                .replaceAll("#([0-9A-Fa-f]{6})", "##$1" + VISIBLE_COLOUR_RESTORE_TOKEN + "$1")
                .replaceAll("&([0-9A-Fa-f])", "&&$1" + VISIBLE_COLOUR_RESTORE_TOKEN + "$1");
    }

    private String normalizeLiteralColourExamples(String line) {
        return line
                .replace(INVISIBLE_COLOUR_CODE_BREAK, "")
                .replace(VARIATION_SELECTOR_TEXT, "")
                .replaceAll("##([0-9A-Fa-f]{6})\\{(?:theme|muted|white|good|bad)}\\1", "#$1")
                .replaceAll("&&([0-9A-Fa-f])\\{(?:theme|muted|white|good|bad)}\\1", "&$1");
    }

    private boolean copySectionValues(FileConfiguration source, FileConfiguration target, String sourceRoot, String targetRoot, boolean overwrite) {
        ConfigurationSection section = source.getConfigurationSection(sourceRoot);
        if (section == null) {
            return false;
        }

        boolean changed = false;
        for (String key : section.getKeys(true)) {
            String sourcePath = sourceRoot + "." + key;
            if (source.isConfigurationSection(sourcePath)) {
                continue;
            }

            String targetPath = targetRoot + "." + key;
            if (!overwrite && target.isSet(targetPath)) {
                continue;
            }

            Object value = source.get(sourcePath);
            if (Objects.equals(target.get(targetPath), value)) {
                continue;
            }

            target.set(targetPath, value);
            changed = true;
        }
        return changed;
    }

    private boolean removeSection(FileConfiguration source, String path) {
        if (!source.isSet(path)) {
            return false;
        }

        source.set(path, null);
        return true;
    }

    private void runIfPresent(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    private void refreshPlaceholderHook() {
        if (placeholderHookRegistered) {
            return;
        }

        placeholderHookRegistered = core.placeholders("focolours")
                .author("Carrotio")
                .offline("colour", chatColorService::getPlaceholderValue)
                .registerIfAvailable();
    }
}

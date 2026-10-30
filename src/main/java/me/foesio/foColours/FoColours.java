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
import me.foesio.foColours.api.FoColoursApi;
import me.foesio.foColours.api.FoColoursApiImpl;
import me.foesio.foColours.compat.EzColorsCompatService;
import me.foesio.foColours.command.FoColoursAdminCommand;
import me.foesio.foColours.command.FoColoursCommand;
import me.foesio.foColours.config.ConfigManager;
import me.foesio.foColours.config.PublicTextMigration;
import me.foesio.foColours.config.PublicGuiUpgradeSnapshot;
import me.foesio.foColours.data.PlayerDataManager;
import me.foesio.foColours.gui.MenuListener;
import me.foesio.foColours.gui.PlayerColorMenu;
import me.foesio.foColours.listener.ChatListener;
import me.foesio.foColours.listener.PlayerConnectionListener;
import me.foesio.foColours.service.ChatColorService;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private FoColoursApi api;
    private ChatPromptManager chatPromptManager;
    private EzColorsCompatService ezColorsCompatService;
    private DialogService dialogService;
    private UpdateNoticeService updateNotices;
    private FoSoundService sounds;
    private PlayerColorMenu playerColorMenu;
    private FoEditorSounds editorSounds;
    private FoGuiSounds guiSounds;
    private FoAdminSounds adminSounds;
    private boolean placeholderHookRegistered;
    private boolean messagesInstalledThisStart;
    private final Set<UUID> nativeDialogFallbackWarnedPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        boolean freshInstall = !getDataFolder().exists()
                || getDataFolder().listFiles() == null || getDataFolder().listFiles().length == 0;
        java.nio.file.Path installMarker = getDataFolder().toPath().resolve(
                ConfigManager.PUBLIC_GUI_INSTALL_MARKER);
        PublicGuiUpgradeSnapshot ownerFiles;
        try {
            if (freshInstall) {
                Files.createDirectories(getDataFolder().toPath());
                Files.writeString(installMarker, "FoColours public GUI first install\n",
                        java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            ownerFiles = PublicGuiUpgradeSnapshot.capture(getDataFolder().toPath());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not capture public GUI upgrade files", error);
        }
        try {
            saveDefaultConfig();

            this.configManager = new ConfigManager(this, freshInstall || Files.isRegularFile(installMarker,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS));
            if (configManager.shouldInstallNewResources()) {
                messagesInstalledThisStart = installNewResource("messages.yml")
                        || configManager.isPublicGuiMigrationPending() && matchesBundledResource("messages.yml");
                installNewResource("sounds.yml");
            }
            this.messages = FoMessageService.loadPreservingRemovals(this, messageMigrations());
            messagesInstalledThisStart = false;
            this.configManager.setMessages(messages);
            refreshCoreContext();
            migrateSprites();
            this.colorRegistry = new ColorRegistry(configManager);
            this.playerDataManager = new PlayerDataManager(this, configManager);
            this.ezColorsCompatService = new EzColorsCompatService(configManager, colorRegistry, playerDataManager);
            this.chatColorService = new ChatColorService(configManager, colorRegistry, playerDataManager, ezColorsCompatService);
            this.chatPromptManager = new ChatPromptManager(this, core.scheduler());
            refreshDialogService();

            colorRegistry.reload();
            playerDataManager.initialize();
            ezColorsCompatService.syncOnlinePlayersIfConfigured();
            this.playerColorMenu = new PlayerColorMenu(this);
            try {
                finishPendingPublicGuiMigration();
            } catch (RuntimeException migrationError) {
                logWarning("Public GUI upgrade could not finish; restoring owner files and leaving the menu unavailable: "
                        + migrationError.getMessage());
                try {
                    ownerFiles.rollback();
                    configManager.refreshAfterFailedMigration();
                    // Released installs stored their text in config.yml and had no
                    // messages.yml to restore. Keep the already migrated in-memory
                    // text in that case so direct colour commands retain feedback.
                    if (Files.isRegularFile(getDataFolder().toPath().resolve("messages.yml"))) {
                        messages = FoMessageService.loadPreservingRemovals(this, FoMessageMigrations.none());
                        configManager.setMessages(messages);
                    }
                    sounds = FoSoundService.loadPreservingRemovals(this);
                    refreshCoreContext();
                    colorRegistry.reload();
                    playerColorMenu.blockForFailedMigration();
                } catch (java.io.IOException | RuntimeException restoreError) {
                    migrationError.addSuppressed(restoreError);
                    throw migrationError;
                }
            }

            registerCommands();
            registerListeners();
            refreshPlaceholderHook();
            this.api = new FoColoursApiImpl(this);
            getServer().getServicesManager().register(FoColoursApi.class, api, this, ServicePriority.Normal);
        } catch (RuntimeException | Error failure) {
            try {
                ownerFiles.rollback();
            } catch (java.io.IOException rollbackError) {
                failure.addSuppressed(rollbackError);
            }
            throw failure;
        }
    }

    @Override
    public void onDisable() {
        if (api != null) {
            getServer().getServicesManager().unregister(FoColoursApi.class, api);
            api = null;
        }
        placeholderHookRegistered = false;
        if (playerColorMenu != null) {
            playerColorMenu.close();
            playerColorMenu = null;
        }

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
        boolean menuWasAvailable = playerColorMenu != null && playerColorMenu.isAvailable();
        PublicGuiUpgradeSnapshot ownerFiles;
        try {
            ownerFiles = PublicGuiUpgradeSnapshot.capture(getDataFolder().toPath());
        } catch (java.io.IOException error) {
            return FoReloadResult.failed(java.util.List.of(), "public GUI upgrade snapshot", error);
        }
        // Ordinary reloads must reject invalid owner files before changing any
        // messages, sounds, colours, or the GUI snapshot they are rendered with.
        // A pending upgrade first needs its versioned file migration.
        if (!configManager.isPublicGuiMigrationPending()
                && !configManager.isFreshPublicGuiInstallPending()) {
            for (String filename : new String[] {"config.yml", "messages.yml"}) {
                try {
                    validateOwnerYaml(filename);
                } catch (RuntimeException error) {
                    return FoReloadResult.failed(java.util.List.of(), filename, error);
                }
            }
            if (!sounds.validateInstalled()) {
                return FoReloadResult.failed(java.util.List.of(), "sounds.yml",
                        new IllegalStateException("Sound validation failed; previous working version remains active"));
            }
            if (playerColorMenu != null && !playerColorMenu.validateInstalled().successful()) {
                return FoReloadResult.failed(java.util.List.of(), "player GUI",
                        new IllegalStateException("Player GUI validation failed; previous working version remains active"));
            }
        }
        FoReloadResult result = FoReloadRegistry.create()
                .add("config", () -> {
                    configManager.reload();
                    if (configManager.shouldInstallNewResources()) {
                        messagesInstalledThisStart = installNewResource("messages.yml")
                                || configManager.isPublicGuiMigrationPending() && matchesBundledResource("messages.yml");
                        installNewResource("sounds.yml");
                    }
                })
                .add("messages", () -> {
                    validateOwnerYaml("messages.yml");
                    messages.reload();
                    messagesInstalledThisStart = false;
                    migrateSprites();
                })
                .add("core", this::refreshCoreContext)
                .add("sounds", () -> {
                    validateOwnerYaml("sounds.yml");
                    if (!sounds.reloadChecked()) {
                        throw new IllegalStateException("sounds.yml validation failed; previous sounds remain active");
                    }
                })
                .add("colours", colorRegistry::reload)
                .add("player-data", () -> playerDataManager.normalizeOnlineSelections(colorRegistry, configManager.getDefaultColourKey()))
                .add("ezcolors", ezColorsCompatService::syncOnlinePlayersIfConfigured)
                .add("placeholders", this::refreshPlaceholderHook)
                .add("dialogs", this::refreshDialogService)
                .add("admin commands", this::registerCommands)
                .add("player GUI", () -> {
                    if (!playerColorMenu.reload().successful()) {
                        throw new IllegalStateException("Player GUI validation failed; previous GUI remains active");
                    }
                })
                .add("public GUI migration", this::finishPendingPublicGuiMigration)
                .reload();
        if (!result.successful()) {
            try {
                if (ownerFiles.rollback()) {
                    configManager.refreshAfterFailedMigration();
                    colorRegistry.reload();
                    if (Files.isRegularFile(getDataFolder().toPath().resolve("messages.yml"))) {
                        messages = FoMessageService.loadPreservingRemovals(this, FoMessageMigrations.none());
                        configManager.setMessages(messages);
                    }
                    sounds = FoSoundService.loadPreservingRemovals(this);
                    refreshCoreContext();
                    if (result.completedSteps().contains("player GUI")) {
                        playerColorMenu.close();
                        playerColorMenu = new PlayerColorMenu(this);
                    }
                }
            } catch (java.io.IOException | RuntimeException rollbackError) {
                logWarning("Could not fully restore public GUI owner files after failed reload: "
                        + rollbackError.getMessage());
                return FoReloadResult.failed(result.completedSteps(), "public GUI upgrade rollback", rollbackError);
            }
            if ("public GUI migration".equals(result.failedStep()) && !menuWasAvailable) {
                playerColorMenu.blockForFailedMigration();
            }
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
                        configManager.sendMessage(player, "messages.prompt-invalid");
                        openEditorTextInput(player, request, validator, onAccept, reopenAction);
                        return;
                    }

                    onAccept.accept(input);
                    reopenAction.run();
                },
                () -> {
                    configManager.sendMessage(player, "messages.prompt-cancelled");
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

    public PlayerColorMenu getPlayerColorMenu() {
        return playerColorMenu;
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
            configManager.sendMessage(sender, "messages.reload-complete");
            if (sender instanceof Player player) {
                adminSounds.reload(player);
            }
            return;
        }

        configManager.sendMessage(sender, "messages.reload-failed", Map.of(
                "step", result.failedStep(),
                "error", result.errorMessage()
        ));
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

    private boolean installNewResource(String path) {
        Path target = getDataFolder().toPath().resolve(path);
        if (Files.exists(target)) {
            return false;
        }
        try (InputStream bundled = getResource(path)) {
            if (bundled == null) {
                throw new IllegalStateException("Missing packaged resource " + path);
            }
            Files.createDirectories(target.getParent());
            Path staged = Files.createTempFile(target.getParent(), ".resource-", ".tmp");
            try {
                Files.copy(bundled, staged, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException unavailable) {
                    Files.move(staged, target);
                }
            } finally {
                Files.deleteIfExists(staged);
            }
            return true;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not install " + path, error);
        }
    }

    private boolean matchesBundledResource(String path) {
        File installed = new File(getDataFolder(), path);
        try (InputStream bundled = getResource(path)) {
            return installed.isFile() && bundled != null
                    && java.util.Arrays.equals(Files.readAllBytes(installed.toPath()), bundled.readAllBytes());
        } catch (java.io.IOException error) {
            logWarning("Could not compare " + path + " during upgrade recovery: " + error.getMessage());
            return false;
        }
    }

    private void finishPendingPublicGuiMigration() {
        if (!configManager.isPublicGuiMigrationPending()
                && !configManager.isFreshPublicGuiInstallPending()) {
            if (playerColorMenu != null) {
                playerColorMenu.completeMigration();
            }
            return;
        }
        validateOwnerYaml("messages.yml");
        validateOwnerYaml("sounds.yml");
        try {
            YamlConfiguration savedMessages = new YamlConfiguration();
            savedMessages.load(new File(getDataFolder(), "messages.yml"));
            if (!PublicTextMigration.sameValues(messages.config(), savedMessages)) {
                throw new IllegalStateException("messages.yml was not saved with the migrated public text");
            }
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IllegalStateException("Cannot confirm migrated messages.yml on disk", error);
        }
        // The first load may have used Core's field-level cold-start fallback.
        // A pending upgrade may only finish when the installed file itself
        // passes a strict reload, without in-memory substitutions.
        if (playerColorMenu == null || !playerColorMenu.reload().successful() || !sounds.reloadChecked()) {
            throw new IllegalStateException("Public GUI migration remains pending until the installed GUI and sounds load");
        }
        configManager.completePublicGuiMigration();
        try {
            if (configManager.isFreshPublicGuiInstallPending()) {
                Files.deleteIfExists(getDataFolder().toPath().resolve(ConfigManager.PUBLIC_GUI_INSTALL_MARKER));
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not finish fresh public GUI installation", error);
        }
        playerColorMenu.completeMigration();
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
        if (this.sounds == null) {
            this.sounds = FoSoundService.loadPreservingRemovals(this);
        }
        this.editorSounds = FoEditorSounds.create(sounds);
        this.guiSounds = FoGuiSounds.create(sounds);
        this.adminSounds = FoAdminSounds.create(sounds);
        this.core.metrics(BSTATS_ID);
        if (updateNotices != null) {
            HandlerList.unregisterAll(updateNotices);
        }
        this.updateNotices = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID, adminSounds).start();
    }

    private void validateOwnerYaml(String filename) {
        File file = new File(getDataFolder(), filename);
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.load(file);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load " + filename + ": " + error.getMessage(), error);
        }
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
                || nativeDialogs.canUseNativeDialogs(player)
                || !nativeDialogs.warnOnFallback()
                || !nativeDialogFallbackWarnedPlayers.add(player.getUniqueId())) {
            return;
        }

        configManager.sendMessage(player, "messages.native-dialog-fallback");
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .add(this::migrateLegacyTextConfigToMessages)
                .add(this::migrateStyleTokensToCoreTokens)
                .add(this::cleanupAddCustomColourPromptText)
                .remove("messages.prefix")
                .remove("messages.version")
                .remove("messages.update-available")
                .remove("messages.prompts.edit-prefix")
                .remove("messages.prompts.edit-player-title")
                .remove("messages.prompts.edit-admin-title")
                .build();
    }

    private void migrateSprites() {
        // Old sprite migrations are retained as version checkpoints. The newest
        // bundled defaults already contain sprites; custom owner text stays exact.
        messages.migrateToVersion(core.migrations(), 1, config -> false);
        core.migrations().runToVersion(2, configManager::migrateGuiSprites);
        messages.migrateToVersion(core.migrations(), 3, config -> false);
        // An existing messages.yml is authoritative. Its absent keys may have
        // been deliberately removed, so version checkpoints cannot add them.
        messages.migrateToVersion(core.migrations(), 4, config -> false);
        messages.migrateToVersion(core.migrations(), 5, config -> false);
    }

    private boolean migrateLegacyTextConfigToMessages(FileConfiguration messageConfig) {
        if (!messagesInstalledThisStart || !configManager.isPublicGuiMigrationPending()) {
            return false; // Existing messages.yml is authoritative, including owner-removed keys.
        }
        try (InputStream old = getResource("migrations/player-menu-v1.yml");
             InputStream newest = getResource("messages.yml")) {
            if (old == null || newest == null) {
                throw new IllegalStateException("Missing bundled public text migration defaults");
            }
            YamlConfiguration released = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(old, StandardCharsets.UTF_8));
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(newest, StandardCharsets.UTF_8));
            return PublicTextMigration.fromReleasedConfig(configManager.getConfig(), released,
                    messageConfig, bundled, this::logWarning);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not read public text migration defaults", error);
        }
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
            if (!messageConfig.contains(targetPath, false)) {
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

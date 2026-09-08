package me.foesio.foColours.config;

import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.config.ResourceFiles;
import me.foesio.core.message.FoMessageService;
import me.foesio.foColours.FoColours;
import me.foesio.foColours.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.foesio.core.gui.GuiTitles;
import java.util.Objects;
import java.util.Set;

public final class ConfigManager {

    private static final List<String> DEFAULT_RAINBOW_PALETTE = List.of(
            "#ff5d73",
            "#ffbd2e",
            "#f7f779",
            "#3ecf8e",
            "#4dbdff",
            "#d37cff"
    );
    private static final List<Integer> CURRENT_DEFAULT_COLOUR_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );
    private static final List<Integer> OLD_DEFAULT_COLOUR_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );

    private static final int YAML_LINE_WIDTH = 4096;

    private final FoColours plugin;
    private final File guisFolder;
    private volatile FoMessageService messages;
    private volatile FileConfiguration config;
    private volatile Map<String, FileConfiguration> guis = Map.of();
    private volatile boolean playerGuiEnabled;
    private volatile boolean showLockedColours;
    private volatile boolean rainbowAdvanceOnSpaces;
    private volatile ChatMode chatMode;
    private volatile boolean nativeDialogsEnabled;
    private volatile boolean ezColorsCompatEnabled;
    private volatile boolean ezColorsSyncOnJoin;
    private volatile boolean ezColorsSyncOnReload;
    private volatile boolean ezColorsRevokeMissingPermissionGrants;
    private volatile boolean ezColorsShowRainbowUnlocksRainbow;
    private volatile long ezColorsJoinSyncDelayTicks;
    private volatile String defaultColourKey;
    private volatile Set<String> defaultUnlockedColours;
    private volatile List<String> rainbowPalette;

    public ConfigManager(FoColours plugin) {
        this.plugin = plugin;
        this.guisFolder = ResourceFiles.dataFile(plugin, "guis");
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        preventLineWrapping(config);
        loadGuiFiles();
        ensureConfigDefaults();
        this.playerGuiEnabled = config.getBoolean("features.player-gui-enabled", true);
        this.showLockedColours = config.getBoolean("features.show-locked-colours", true);
        this.rainbowAdvanceOnSpaces = config.getBoolean("rainbow.advance-on-spaces", false);
        this.chatMode = parseChatMode(config.getString("chat.mode", "placeholder"));
        this.nativeDialogsEnabled = config.getBoolean("native-dialogs.enabled", true);
        this.ezColorsCompatEnabled = config.getBoolean("compatibility.ezcolors.enabled", true);
        this.ezColorsSyncOnJoin = config.getBoolean("compatibility.ezcolors.sync-on-join", true);
        this.ezColorsSyncOnReload = config.getBoolean("compatibility.ezcolors.sync-on-reload", true);
        this.ezColorsRevokeMissingPermissionGrants = config.getBoolean("compatibility.ezcolors.revoke-missing-permission-grants", false);
        this.ezColorsShowRainbowUnlocksRainbow = config.getBoolean("compatibility.ezcolors.showrainbow-unlocks-rainbow", true);
        this.ezColorsJoinSyncDelayTicks = Math.max(1L, config.getLong("compatibility.ezcolors.join-sync-delay-ticks", 20L));
        this.defaultColourKey = normalize(config.getString("player-data.default-colour", "light_gray"));
        this.defaultUnlockedColours = new LinkedHashSet<>(config.getStringList("player-data.default-unlocked-colours").stream().map(this::normalize).toList());
        if (defaultUnlockedColours.isEmpty()) {
            this.defaultUnlockedColours = Set.of(defaultColourKey);
        }
        this.rainbowPalette = List.copyOf(config.getStringList("rainbow.palette"));
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void setMessages(FoMessageService messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public FileConfiguration getMessagesConfig() {
        return messages().config();
    }

    public boolean isPlayerGuiEnabled() {
        return playerGuiEnabled;
    }

    public boolean isShowLockedColours() {
        return showLockedColours;
    }

    public boolean isRainbowAdvanceOnSpaces() {
        return rainbowAdvanceOnSpaces;
    }

    public boolean isAutoChatMode() {
        return chatMode == ChatMode.AUTO;
    }

    public ChatMode getChatMode() {
        return chatMode;
    }

    public boolean isNativeDialogsEnabled() {
        return nativeDialogsEnabled;
    }

    public boolean isEzColorsCompatEnabled() {
        return ezColorsCompatEnabled;
    }

    public boolean isEzColorsSyncOnJoin() {
        return ezColorsSyncOnJoin;
    }

    public boolean isEzColorsSyncOnReload() {
        return ezColorsSyncOnReload;
    }

    public boolean isEzColorsRevokeMissingPermissionGrants() {
        return ezColorsRevokeMissingPermissionGrants;
    }

    public boolean isEzColorsShowRainbowUnlocksRainbow() {
        return ezColorsShowRainbowUnlocksRainbow;
    }

    public long getEzColorsJoinSyncDelayTicks() {
        return ezColorsJoinSyncDelayTicks;
    }

    public String getDefaultColourKey() {
        return defaultColourKey;
    }

    public Set<String> getDefaultUnlockedColours() {
        return defaultUnlockedColours;
    }

    public List<String> getRainbowPalette() {
        return rainbowPalette;
    }

    public String message(String path) {
        return message(path, Map.of());
    }

    public String message(String path, Map<String, String> placeholders) {
        if (isMessagesPath(path)) {
            return messages().render(toMessagePath(path), "", placeholders);
        }

        String raw = textString(path, "");
        return formatText(raw, placeholders);
    }

    /**
     * Renders configurable GUI text for a specific viewer while preserving
     * sprite tokens until FoPluginCore can resolve them for that player.
     */
    public String messageForViewer(Player viewer, String path, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        String raw = isMessagesPath(path)
                ? messages().config().getString(toMessagePath(path), "")
                : textString(path, "");
        return messages().renderTemplateForViewer(viewer, raw, placeholders);
    }

    public List<String> messageList(String path, Map<String, String> placeholders) {
        if (isMessagesPath(path)) {
            return messages().renderList(toMessagePath(path), List.of(""), placeholders);
        }

        List<String> formatted = new ArrayList<>();
        for (String line : textList(path)) {
            formatted.add(formatText(line, placeholders));
        }
        return formatted;
    }

    public List<String> messageListForViewer(Player viewer, String path, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        List<String> raw = isMessagesPath(path)
                ? messages().config().getStringList(toMessagePath(path))
                : textList(path);
        return raw.stream()
                .map(line -> messages().renderTemplateForViewer(viewer, line, placeholders))
                .toList();
    }

    public String title(String path) {
        return GuiTitles.format(formatText(textString(path, "&8ꜰᴏᴄᴏʟᴏᴜʀꜱ"), Map.of()));
    }

    public void set(String path, Object value) {
        if (isMessagesPath(path)) {
            messages().config().set(toMessagePath(path), value);
            messages().save();
            return;
        }

        config.set(path, value);
        saveMainConfig();
    }

    public Material readMaterial(String rawMaterial, Material fallback) {
        if (rawMaterial == null || rawMaterial.isBlank()) {
            return fallback;
        }

        try {
            return Material.valueOf(rawMaterial.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public Material getMaterial(String path, Material fallback) {
        String rawMaterial = valueString(path, null);
        Material material = readMaterial(rawMaterial, fallback);
        if (rawMaterial != null && material == fallback && !rawMaterial.equalsIgnoreCase(fallback.name())) {
            plugin.logWarning("Invalid material at " + path + ": " + rawMaterial + ". Using " + fallback.name() + ".");
        }
        return material;
    }

    public List<Integer> getIntegerList(String path) {
        FileConfiguration source = configSource(path);
        return source.getIntegerList(path);
    }

    public int getBoundedInt(String path, int fallback, int min, int max) {
        FileConfiguration source = configSource(path);
        if (!source.isSet(path)) {
            return fallback;
        }
        if (!source.isInt(path)) {
            plugin.logWarning("Invalid number at " + path + ". Using " + fallback + ".");
            return fallback;
        }

        int value = source.getInt(path);
        if (value < min || value > max) {
            plugin.logWarning("Invalid number at " + path + ": " + value + ". Expected " + min + "-" + max + ". Using " + fallback + ".");
            return fallback;
        }
        return value;
    }

    public int getGuiSize(String rowsPath, int fallbackRows) {
        return getBoundedInt(rowsPath, fallbackRows, 1, 6) * 9;
    }

    public int getGuiSlot(String path, int fallback, int inventorySize) {
        int slot = getBoundedInt(path, fallback, 0, inventorySize - 1);
        if (slot < 0 || slot >= inventorySize) {
            plugin.logWarning("Invalid fallback slot for " + path + ": " + slot + ". Using 0.");
            return 0;
        }
        return slot;
    }

    public List<Integer> getGuiSlots(String path, int inventorySize) {
        FileConfiguration source = configSource(path);
        List<Integer> slots = new ArrayList<>();
        for (int slot : source.getIntegerList(path)) {
            if (slot < 0 || slot >= inventorySize) {
                plugin.logWarning("Invalid GUI slot at " + path + ": " + slot + ". Expected 0-" + (inventorySize - 1) + ".");
                continue;
            }
            slots.add(slot);
        }
        return slots;
    }

    public boolean getBoolean(String path, boolean fallback) {
        FileConfiguration source = configSource(path);
        return source.isSet(path) ? source.getBoolean(path, fallback) : fallback;
    }

    public Integer getOptionalInt(String path) {
        FileConfiguration source = configSource(path);
        if (!source.isSet(path)) {
            return null;
        }
        if (!source.isInt(path)) {
            plugin.logWarning("Invalid number at " + path + ". Ignoring value.");
            return null;
        }
        return source.getInt(path);
    }

    public String formatText(String raw, Map<String, String> placeholders) {
        return messages().renderTemplate(raw, placeholders);
    }

    public String plainPrefix() {
        return "[" + plugin.getName() + "]";
    }

    public String normalize(String input) {
        if (input == null) {
            return "";
        }

        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_')
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private void ensureConfigDefaults() {
        boolean changed = false;
        String defaultChatMode = config.getBoolean("features.chat-hook-enabled", false) ? "auto" : "placeholder";
        changed |= setDefault("chat.mode", defaultChatMode);
        changed |= ensureNativeDialogDefaults();
        changed |= migrateLegacyRainbowFeatureFlag();
        changed |= setDefault("rainbow.palette", DEFAULT_RAINBOW_PALETTE);
        changed |= setDefault("rainbow.advance-on-spaces", false);
        changed |= setDefault("compatibility.ezcolors.showrainbow-unlocks-rainbow", true);
        changed |= setDefault("colours.built-in.rainbow.enabled", true);
        changed |= setDefault("colours.built-in.rainbow.type", "RAINBOW");
        changed |= setDefault("colours.built-in.rainbow.display-name", "Rainbow");
        changed |= setDefault("colours.built-in.rainbow.material", "FIREWORK_STAR");
        changed |= setDefault("colours.built-in.rainbow.aliases", List.of());
        changed |= removeSection(config, "features.reset-enabled");
        changed |= removeSection(config, "features.chat-hook-enabled");
        changed |= removeSection(config, "features.placeholderapi-enabled");
        changed |= removeSection(config, "gui.admin-menu.items.edit-prefix");
        changed |= removeSection(config, "gui.admin-menu.items.edit-player-title");
        changed |= removeSection(config, "gui.admin-menu.items.edit-admin-title");
        changed |= removeSection(config, "gui.admin-menu.items.reload");
        changed |= removeSection(config, "gui.admin-menu.items.native-dialogs");
        changed |= removeSection(config, "gui.admin-menu.items.rainbow");
        changed |= removeSection(config, "gui.admin-menu.items.reset");
        changed |= removeSection(config, "gui.colour-config-menu.items.info");
        changed |= migrateAddCustomColourButtonConfig();
        changed |= removeSection(config, "updates");
        changed |= copyMissingResourceValues(config, "config.yml");
        if (changed) {
            saveMainConfig();
        }
    }

    private ChatMode parseChatMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return ChatMode.PLACEHOLDER;
        }

        try {
            return ChatMode.valueOf(rawMode.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            plugin.logWarning("Invalid chat.mode '" + rawMode + "'. Falling back to placeholder mode.");
            return ChatMode.PLACEHOLDER;
        }
    }

    private boolean setDefault(String path, Object value) {
        if (config.isSet(path)) {
            return false;
        }

        config.set(path, value);
        return true;
    }

    private boolean ensureNativeDialogDefaults() {
        boolean changed = !config.isSet(NativeDialogConfigDefaults.ENABLED_PATH)
                || !config.isSet(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH)
                || config.getComments("native-dialogs").isEmpty()
                || config.getComments(NativeDialogConfigDefaults.ENABLED_PATH).isEmpty()
                || config.getComments(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH).isEmpty();
        NativeDialogConfigDefaults.addDefaults(config);
        return changed;
    }

    private boolean migrateLegacyRainbowFeatureFlag() {
        String legacyPath = "features.rainbow-enabled";
        if (!config.isSet(legacyPath)) {
            return false;
        }

        boolean legacyEnabled = config.getBoolean(legacyPath, true);
        boolean rainbowColourEnabled = !config.isSet("colours.built-in.rainbow.enabled")
                || config.getBoolean("colours.built-in.rainbow.enabled", true);
        if (!legacyEnabled && rainbowColourEnabled) {
            config.set("colours.built-in.rainbow.enabled", false);
        }

        config.set(legacyPath, null);
        return true;
    }

    private boolean migrateAddCustomColourButtonConfig() {
        String oldRoot = "gui.admin-menu.items.add-custom";
        if (!config.isSet(oldRoot)) {
            return false;
        }

        if (config.isConfigurationSection(oldRoot)) {
            copySelectedButtonTextValues(oldRoot, "gui.colour-config-menu.items.add-custom");
        }
        config.set(oldRoot, null);
        return true;
    }

    private void copySelectedButtonTextValues(String sourceRoot, String targetRoot) {
        for (String key : List.of("name", "lore")) {
            String sourcePath = sourceRoot + "." + key;
            String targetPath = targetRoot + "." + key;
            if (!config.isSet(sourcePath) || config.isSet(targetPath)) {
                continue;
            }

            config.set(targetPath, config.get(sourcePath));
        }
    }

    private void loadGuiFiles() {
        boolean created = saveDefaultNestedResource("guis/player-menu.yml");
        File guiFile = ResourceFiles.dataFile(plugin, "guis/player-menu.yml");
        FileConfiguration playerMenu = loadNestedYaml(guisFolder, "player-menu.yml");
        boolean changed = copyMissingResourceValues(playerMenu, "guis/player-menu.yml");
        changed |= migrateOldDefaultColourSlots(playerMenu);
        if (created) {
            changed |= migratePublicGuiConfig(playerMenu);
        }
        changed |= removeDeprecatedSharedButtonSections(playerMenu);
        if (changed) {
            saveYaml(playerMenu, guiFile, "guis/player-menu.yml");
        }
        this.guis = Map.of("player-menu", playerMenu);
    }

    private boolean migrateOldDefaultColourSlots(FileConfiguration playerMenu) {
        List<Integer> currentSlots = playerMenu.getIntegerList("gui.shared.colour-slots");
        if (!currentSlots.equals(OLD_DEFAULT_COLOUR_SLOTS)) {
            return false;
        }

        playerMenu.set("gui.shared.colour-slots", CURRENT_DEFAULT_COLOUR_SLOTS);
        return true;
    }

    private boolean removeDeprecatedSharedButtonSections(FileConfiguration playerMenu) {
        boolean changed = false;
        changed |= removeSection(playerMenu, "gui.shared.page-previous");
        changed |= removeSection(playerMenu, "gui.shared.page-next");
        changed |= removeSection(playerMenu, "gui.shared.close");
        changed |= removeSection(playerMenu, "gui.shared.back");
        return changed;
    }

    private boolean saveDefaultNestedResource(String resourcePath) {
        File target = ResourceFiles.dataFile(plugin, resourcePath);
        boolean created = !target.exists();
        ResourceFiles.saveDefault(plugin, resourcePath);
        return created;
    }

    private FileConfiguration loadNestedYaml(File folder, String fileName) {
        File file = new File(folder, fileName);
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        loaded.options().width(YAML_LINE_WIDTH);
        return loaded;
    }

    private boolean migratePublicGuiConfig(FileConfiguration playerMenu) {
        boolean guiChanged = false;
        guiChanged |= copySectionValues(config, playerMenu, "gui.shared", true);
        guiChanged |= copySectionValues(config, playerMenu, "gui.states", true);
        guiChanged |= copySectionValues(config, playerMenu, "gui.player-menu", true);

        boolean configChanged = false;
        configChanged |= removeSection(config, "gui.shared");
        configChanged |= removeSection(config, "gui.states");
        configChanged |= removeSection(config, "gui.player-menu");
        if (configChanged) {
            saveMainConfig();
        }
        return guiChanged;
    }

    private boolean copyMissingResourceValues(FileConfiguration target, String resourcePath) {
        InputStream resource = plugin.getResource(resourcePath);
        if (resource == null) {
            plugin.logWarning("Could not find bundled " + resourcePath + " defaults.");
            return false;
        }

        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key) || target.isSet(key)) {
                    continue;
                }

                target.set(key, defaults.get(key));
                changed = true;
            }
            return changed;
        } catch (IOException exception) {
            plugin.logWarning("Could not read bundled " + resourcePath + " defaults: " + exception.getMessage());
            return false;
        }
    }

    private boolean copySectionValues(FileConfiguration source, FileConfiguration target, String rootPath, boolean overwrite) {
        ConfigurationSection section = source.getConfigurationSection(rootPath);
        if (section == null) {
            return false;
        }

        boolean changed = false;
        for (String key : section.getKeys(true)) {
            String path = rootPath + "." + key;
            if (source.isConfigurationSection(path)) {
                continue;
            }
            if (!overwrite && target.isSet(path)) {
                continue;
            }

            Object value = source.get(path);
            if (Objects.equals(target.get(path), value)) {
                continue;
            }

            target.set(path, value);
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

    private String textString(String path, String fallback) {
        String value = valueString(path, null);
        if (value != null) {
            return value;
        }
        return fallback;
    }

    private String valueString(String path, String fallback) {
        if (isGuiPath(path)) {
            FileConfiguration gui = guis.get("player-menu");
            if (gui != null && gui.isSet(path)) {
                return gui.getString(path, fallback);
            }
        }
        return config.getString(path, fallback);
    }

    private List<String> textList(String path) {
        if (isGuiPath(path)) {
            FileConfiguration gui = guis.get("player-menu");
            if (gui != null && gui.isSet(path)) {
                return gui.getStringList(path);
            }
        }
        return config.getStringList(path);
    }

    private FileConfiguration configSource(String path) {
        if (isGuiPath(path)) {
            FileConfiguration gui = guis.get("player-menu");
            if (gui != null && gui.isSet(path)) {
                return gui;
            }
        }
        return config;
    }

    private boolean isGuiPath(String path) {
        return path.equals("gui")
                || path.startsWith("gui.");
    }

    private boolean isMessagesPath(String path) {
        return path.equals("messages")
                || path.startsWith("messages.")
                || path.equals("tokens")
                || path.startsWith("tokens.")
                || path.equals("style.tokens")
                || path.startsWith("style.tokens.");
    }

    private String toMessagePath(String path) {
        if (path.equals("style.tokens")) {
            return "tokens";
        }
        if (path.startsWith("style.tokens.")) {
            return "tokens." + path.substring("style.tokens.".length());
        }
        return path;
    }

    private FoMessageService messages() {
        FoMessageService current = messages;
        if (current == null) {
            current = plugin.getMessages();
        }
        if (current == null) {
            throw new IllegalStateException("FoMessageService is not loaded");
        }
        return current;
    }

    private void saveMainConfig() {
        preventLineWrapping(config);
        plugin.saveConfig();
    }

    private void saveYaml(FileConfiguration fileConfiguration, File file, String label) {
        preventLineWrapping(fileConfiguration);
        try {
            fileConfiguration.save(file);
        } catch (IOException exception) {
            plugin.logWarning("Could not save " + label + ": " + exception.getMessage());
        }
    }

    private void preventLineWrapping(FileConfiguration configuration) {
        if (configuration instanceof YamlConfiguration yamlConfiguration) {
            yamlConfiguration.options().width(YAML_LINE_WIDTH);
        }
    }

    public enum ChatMode {
        PLACEHOLDER,
        AUTO
    }

}

package me.foesio.foColours.gui;

import me.foesio.core.editor.ConfigEditorButton;
import me.foesio.core.editor.ConfigEditorMenu;
import me.foesio.core.editor.ConfigEditorValueType;
import me.foesio.core.editor.EditorMenuHolder;
import me.foesio.core.editor.EditorSaveResult;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.foColours.FoColours;
import me.foesio.foColours.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;

public final class AdminEditorMenu {

    private static final String MENU_ID = "focolours-admin-editor";
    private static final int MANAGE_COLOURS_SLOT = 10;
    private static final int CHAT_MODE_SLOT = 11;
    private static final int LOCKED_SLOT = 12;

    private final FoColours plugin;

    public AdminEditorMenu(FoColours plugin) {
        this.plugin = plugin;
    }

    static boolean owns(EditorMenuHolder holder) {
        return holder != null && MENU_ID.equals(holder.id());
    }

    public void open(Player player) {
        open(player, true);
    }

    void openSilently(Player player) {
        open(player, false);
    }

    private void open(Player player, boolean playOpenSound) {
        if (playOpenSound) {
            plugin.getEditorSounds().open(player);
        }
        editor().open(player);
    }

    public void handleClick(Player player, EditorMenuHolder holder, int slot) {
        Optional<ConfigEditorButton> clicked = editor().buttonAt(holder, slot);
        if (clicked.isEmpty()) {
            return;
        }

        ConfigEditorButton button = clicked.get();
        if (button.type() == ConfigEditorValueType.BOOLEAN) {
            saveBoolean(player, button);
            return;
        }

        switch (button.id()) {
            case "chat-mode" -> cycleChatMode(player);
            case "manage-colours" -> new ColourConfigMenu(plugin).open(player);
            default -> {
            }
        }
    }

    private ConfigEditorMenu editor() {
        return ConfigEditorMenu.builder(plugin, plugin.getMessages(), adminEditorDefaults())
                .id(MENU_ID)
                .title("title", "FoColours Editor")
                .size("size", 27)
                .filler("filler", true, plugin.getConfigManager().getMaterial("gui.shared.filler-material", Material.GRAY_STAINED_GLASS_PANE))
                .booleanLabels(
                        plugin.getConfigManager().message("gui.states.enabled"),
                        plugin.getConfigManager().message("gui.states.disabled")
                )
                .reloadSettings(this::reloadPluginSettings)
                .button(booleanSetting("show-locked", "features.show-locked-colours", "Show Locked Colours",
                        "buttons.show-locked", LOCKED_SLOT, () -> plugin.getConfigManager().getConfig().getBoolean("features.show-locked-colours")))
                .button(ConfigEditorButton.action("chat-mode", "buttons.chat-mode", CHAT_MODE_SLOT)
                        .fallbackMaterial(Material.REPEATER)
                        .build())
                .button(ConfigEditorButton.action("manage-colours", "buttons.manage-colours", MANAGE_COLOURS_SLOT)
                        .fallbackMaterial(Material.SHULKER_BOX)
                        .build())
                .build();
    }

    private ConfigEditorButton booleanSetting(String id, String configPath, String label, String guiPath, int slot, java.util.function.BooleanSupplier currentValue) {
        return ConfigEditorButton.booleanSetting(id, configPath, label, guiPath, slot, currentValue)
                .toggleMaterials(Material.LIME_DYE, Material.RED_DYE)
                .build();
    }

    private YamlConfiguration adminEditorDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("title", plugin.getConfigManager().getConfig().getString("gui.admin-menu.title", "FoColours Editor"));
        defaults.set("size", 27);
        defaults.set("filler.enabled", true);
        defaults.set("filler.material", plugin.getConfigManager().getMaterial("gui.shared.filler-material", Material.GRAY_STAINED_GLASS_PANE).name());

        actionButton(defaults, "buttons.manage-colours", "gui.admin-menu.items.manage-colours", MANAGE_COLOURS_SLOT, Material.SHULKER_BOX, Map.of());
        actionButton(defaults, "buttons.chat-mode", "gui.admin-menu.items.chat-mode", CHAT_MODE_SLOT, Material.REPEATER, chatModePlaceholders());
        toggleButton(defaults, "buttons.show-locked", "gui.admin-menu.items.show-locked", LOCKED_SLOT,
                plugin.getConfigManager().getConfig().getBoolean("features.show-locked-colours"));
        return defaults;
    }

    private void toggleButton(YamlConfiguration defaults, String targetPath, String sourcePath, int slot, boolean enabled) {
        defaults.set(targetPath + ".slot", slot);
        defaults.set(targetPath + ".enabled-material", Material.LIME_DYE.name());
        defaults.set(targetPath + ".disabled-material", Material.RED_DYE.name());
        defaults.set(targetPath + ".name", plugin.getConfigManager().message(sourcePath + ".name"));
        defaults.set(targetPath + ".lore", plugin.getConfigManager().messageList(sourcePath + ".lore", Map.of(
                "state", enabled
                        ? plugin.getConfigManager().message("gui.states.enabled")
                        : plugin.getConfigManager().message("gui.states.disabled")
        )));
    }

    private void actionButton(YamlConfiguration defaults, String targetPath, String sourcePath, int slot, Material material, Map<String, String> placeholders) {
        defaults.set(targetPath + ".slot", slot);
        defaults.set(targetPath + ".material", material.name());
        defaults.set(targetPath + ".name", plugin.getConfigManager().message(sourcePath + ".name", placeholders));
        defaults.set(targetPath + ".lore", plugin.getConfigManager().messageList(sourcePath + ".lore", placeholders));
    }

    private Map<String, String> chatModePlaceholders() {
        ConfigManager.ChatMode current = plugin.getConfigManager().getChatMode();
        return Map.of(
                "mode", current.name().toLowerCase(),
                "placeholder_state", current == ConfigManager.ChatMode.PLACEHOLDER
                        ? plugin.getConfigManager().message("gui.states.selected")
                        : plugin.getConfigManager().message("gui.states.unselected"),
                "auto_state", current == ConfigManager.ChatMode.AUTO
                        ? plugin.getConfigManager().message("gui.states.selected")
                        : plugin.getConfigManager().message("gui.states.unselected")
        );
    }

    private void saveBoolean(Player player, ConfigEditorButton button) {
        boolean nextValue = !plugin.getConfigManager().getConfig().getBoolean(button.configPath());
        EditorSaveResult result = editor().toggle(button);
        if (!result.successful()) {
            plugin.getEditorSounds().error(player);
            player.sendMessage(plugin.getConfigManager().message("messages.reload-failed", Map.of(
                    "step", button.configPath(),
                    "error", result.errorMessage()
            )));
            return;
        }

        player.sendMessage(plugin.getConfigManager().message("messages.editor-saved"));
        plugin.getEditorSounds().toggle(player, nextValue);
        openSilently(player);
    }

    private void cycleChatMode(Player player) {
        ConfigManager.ChatMode next = plugin.getConfigManager().getChatMode() == ConfigManager.ChatMode.AUTO
                ? ConfigManager.ChatMode.PLACEHOLDER
                : ConfigManager.ChatMode.AUTO;
        plugin.getConfigManager().set("chat.mode", next.name().toLowerCase());
        FoReloadResult result = plugin.reloadPluginState();
        if (!result.successful()) {
            plugin.sendReloadResult(player, result);
            plugin.getEditorSounds().error(player);
            return;
        }
        plugin.getEditorSounds().cycle(player);
        player.sendMessage(plugin.getConfigManager().message("messages.editor-saved"));
        openSilently(player);
    }

    private void reloadPluginSettings() {
        FoReloadResult result = plugin.reloadPluginState();
        if (!result.successful()) {
            throw new IllegalStateException(result.failedStep() + ": " + result.errorMessage());
        }
    }
}

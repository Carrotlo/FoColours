package me.foesio.foColours.gui;

import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiItems;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.foColours.FoColours;
import me.foesio.foColours.color.ChatColorDefinition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ColourConfigMenu extends BaseMenu {

    private static final GuiButtonConfig BUTTONS = GuiButtonConfig.defaults();
    private static final int ROWS = 6;
    private static final int BACK_SLOT = GuiSlots.bottomMiddleSlot(ROWS);
    private static final int ADD_CUSTOM_SLOT = 47;
    private static final int ADD_CUSTOM_INPUT_MAX_LENGTH = 43;

    private final int page;

    public ColourConfigMenu(FoColours plugin) {
        this(plugin, 0);
    }

    ColourConfigMenu(FoColours plugin, int page) {
        super(plugin, GuiSlots.sizeForRows(ROWS), plugin.getConfigManager().title("gui.colour-config-menu.title"));
        this.page = Math.max(0, page);
    }

    @Override
    protected void draw(Player viewer) {
        fill(plugin.getConfigManager().getMaterial("gui.shared.filler-material", Material.GRAY_STAINED_GLASS_PANE));

        List<Integer> colourSlots = plugin.getConfigManager().getGuiSlots("gui.shared.colour-slots", inventory.getSize());
        List<ChatColorDefinition> definitions = plugin.getColorRegistry().getAllColours().stream().toList();
        int pageSize = colourSlots.size();
        int maxPage = maxPage(definitions.size(), pageSize);
        int activePage = Math.min(page, maxPage);
        int startIndex = activePage * pageSize;

        for (int index = 0; index < colourSlots.size(); index++) {
            int definitionIndex = startIndex + index;
            if (definitionIndex >= definitions.size()) {
                setEmptyColourSlot(colourSlots.get(index));
                continue;
            }

            ChatColorDefinition definition = definitions.get(definitionIndex);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("colour", definition.getDisplayName());
            placeholders.put("preview", definition.getPreviewLabel(plugin.getConfigManager()));
            placeholders.put("status", definition.isEnabled()
                    ? plugin.getConfigManager().message("gui.states.enabled")
                    : plugin.getConfigManager().message("gui.states.disabled"));
            placeholders.put("default_state", definition.getKey().equals(plugin.getConfigManager().getDefaultColourKey())
                    ? plugin.getConfigManager().message("gui.states.yes")
                    : plugin.getConfigManager().message("gui.states.no"));
            placeholders.put("custom_state", definition.isCustom()
                    ? plugin.getConfigManager().message("gui.states.custom")
                    : plugin.getConfigManager().message("gui.states.built-in"));

            boolean glowing = definition.getKey().equals(plugin.getConfigManager().getDefaultColourKey());
            setItem(colourSlots.get(index), GuiItems.create(
                    definition.getMaterial(),
                    plugin.getConfigManager().message("gui.colour-config-menu.items.colour.name", placeholders),
                    plugin.getConfigManager().messageList("gui.colour-config-menu.items.colour.lore", placeholders),
                    glowing
            ));
        }

        drawPageButtons(activePage, maxPage);
        setItem(BACK_SLOT, BUTTONS.back());
        setItem(ADD_CUSTOM_SLOT, GuiItems.create(
                plugin.getConfigManager().getMaterial("gui.colour-config-menu.items.add-custom.material", Material.ANVIL),
                plugin.getConfigManager().message("gui.colour-config-menu.items.add-custom.name"),
                plugin.getConfigManager().messageList("gui.colour-config-menu.items.add-custom.lore", Map.of())
        ));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        List<Integer> colourSlots = plugin.getConfigManager().getGuiSlots("gui.shared.colour-slots", event.getView().getTopInventory().getSize());
        List<ChatColorDefinition> definitions = plugin.getColorRegistry().getAllColours().stream().toList();
        int pageSize = colourSlots.size();
        int maxPage = maxPage(definitions.size(), pageSize);
        int activePage = Math.min(page, maxPage);

        if (slot == previousPageSlot(event.getView().getTopInventory().getSize()) && activePage > 0) {
            new ColourConfigMenu(plugin, activePage - 1).open(player);
            return;
        }
        if (slot == nextPageSlot(event.getView().getTopInventory().getSize()) && activePage < maxPage) {
            new ColourConfigMenu(plugin, activePage + 1).open(player);
            return;
        }
        if (slot == BACK_SLOT) {
            new AdminEditorMenu(plugin).open(player);
            return;
        }
        if (slot == ADD_CUSTOM_SLOT) {
            openAddCustomColourInput(player, activePage);
            return;
        }

        int index = colourSlots.indexOf(slot);
        int definitionIndex = activePage * pageSize + index;
        if (index < 0 || definitionIndex >= definitions.size()) {
            return;
        }

        ChatColorDefinition definition = definitions.get(definitionIndex);
        ItemStack cursor = event.getCursor();
        if (definition.isCustom() && cursor != null && cursor.getType() != Material.AIR) {
            plugin.getConfigManager().set("colours.custom." + definition.getKey() + ".material", cursor.getType().name());
            if (!reloadOrNotify(player)) {
                return;
            }
            player.sendMessage(plugin.getConfigManager().message("messages.custom-colour-item-updated", Map.of(
                    "colour", definition.getDisplayName(),
                    "item", cursor.getType().name()
            )));
            new ColourConfigMenu(plugin, activePage).open(player);
            return;
        }

        if (event.getClick() == ClickType.SHIFT_RIGHT && definition.isCustom()) {
            openRenameInput(player, definition, activePage);
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT) {
            if (!definition.isEnabled()) {
                player.sendMessage(plugin.getConfigManager().message("messages.colour-must-be-enabled-first"));
                return;
            }

            plugin.getConfigManager().set("player-data.default-colour", definition.getKey());
            if (!reloadOrNotify(player)) {
                return;
            }
            player.sendMessage(plugin.getConfigManager().message("messages.default-colour-set", Map.of("colour", definition.getDisplayName())));
            new ColourConfigMenu(plugin, activePage).open(player);
            return;
        }

        if (event.getClick() == ClickType.RIGHT && definition.isCustom()) {
            if (definition.getKey().equals(plugin.getConfigManager().getDefaultColourKey())) {
                player.sendMessage(plugin.getConfigManager().message("messages.cannot-remove-default-custom"));
                return;
            }

            new ConfirmDeleteCustomColourMenu(plugin, definition.getKey(), definition.getDisplayName(), activePage).open(player);
            return;
        }

        if (definition.getKey().equals(plugin.getConfigManager().getDefaultColourKey())) {
            player.sendMessage(plugin.getConfigManager().message("messages.cannot-disable-default"));
            return;
        }

        String path = (definition.isCustom() ? "colours.custom." : "colours.built-in.") + definition.getKey() + ".enabled";
        plugin.getConfigManager().set(path, !definition.isEnabled());
        if (!reloadOrNotify(player)) {
            return;
        }
        player.sendMessage(plugin.getConfigManager().message("messages.editor-saved"));
        new ColourConfigMenu(plugin, activePage).open(player);
    }

    private int previousPageSlot(int inventorySize) {
        return Math.max(0, inventorySize - GuiSlots.ROW_SIZE);
    }

    private void openRenameInput(Player player, ChatColorDefinition definition, int activePage) {
        TextDialogRequest request = new TextDialogRequest(
                "Rename Colour",
                plugin.getConfigManager().messageList("messages.prompts.rename-custom-colour", Map.of(
                        "colour", definition.getDisplayName()
                )),
                "Display Name",
                definition.getDisplayName(),
                definition.getDisplayName(),
                DialogButton.save(),
                DialogButton.cancel(),
                320,
                300,
                32,
                true,
                true,
                false
        );
        plugin.openEditorTextInput(
                player,
                request,
                input -> !input.isBlank(),
                input -> {
                    plugin.getConfigManager().set("colours.custom." + definition.getKey() + ".display-name", input);
                    if (!reloadOrNotify(player)) {
                        return;
                    }
                    player.sendMessage(plugin.getConfigManager().message("messages.custom-colour-renamed", Map.of(
                            "colour", input
                    )));
                },
                () -> new ColourConfigMenu(plugin, activePage).open(player)
        );
    }

    private void openAddCustomColourInput(Player player, int activePage) {
        TextDialogRequest request = new TextDialogRequest(
                "Add Custom Colour",
                plugin.getConfigManager().messageList("messages.prompts.add-custom-colour", Map.of()),
                "Colour and Name",
                "",
                "#ff8844 Sunset",
                DialogButton.save(),
                DialogButton.cancel(),
                340,
                320,
                ADD_CUSTOM_INPUT_MAX_LENGTH,
                true,
                true,
                false
        );
        plugin.openEditorTextInput(
                player,
                request,
                this::isValidCustomColourInput,
                input -> {
                    CustomColourInput customInput = parseCustomColourInput(input);
                    CustomColourValue colourValue = customInput.colourValue();
                    String displayName = customInput.displayName();
                    String key = nextCustomColourKey(displayName);
                    plugin.getConfigManager().set("colours.custom." + key + ".enabled", true);
                    plugin.getConfigManager().set("colours.custom." + key + ".type", colourValue.type());
                    plugin.getConfigManager().set("colours.custom." + key + ".hex", colourValue.hex());
                    plugin.getConfigManager().set("colours.custom." + key + ".legacy-code", colourValue.legacyCode());
                    plugin.getConfigManager().set("colours.custom." + key + ".display-name", displayName);
                    plugin.getConfigManager().set("colours.custom." + key + ".material", plugin.getConfigManager().getConfig().getString("editor.default-custom-material", "NAME_TAG"));
                    plugin.getConfigManager().set("colours.custom." + key + ".aliases", List.of(key));
                    if (!reloadOrNotify(player)) {
                        return;
                    }
                    player.sendMessage(plugin.getConfigManager().message("messages.custom-colour-added", plugin.getChatColorService().basePlaceholders(
                            "colour", displayName,
                            "value", colourValue.visibleValue()
                    )));
                },
                () -> new ColourConfigMenu(plugin, activePage).open(player)
        );
    }

    private boolean isValidCustomColourInput(String input) {
        CustomColourInput customInput = parseCustomColourInput(input);
        if (customInput == null) {
            return false;
        }
        String displayName = customInput.displayName();
        CustomColourValue colourValue = customInput.colourValue();
        if (colourValue == null) {
            return false;
        }
        return !displayName.isBlank() && !nextCustomColourKey(displayName).isBlank();
    }

    private CustomColourInput parseCustomColourInput(String input) {
        if (input == null) {
            return null;
        }

        String trimmed = input.trim();
        String[] parts = trimmed.split("\\s+", 2);
        if (parts.length < 2) {
            return null;
        }

        CustomColourValue colourValue = parseCustomColourValue(parts[0]);
        String displayName = parts[1].trim();
        if (colourValue == null || displayName.isBlank()) {
            return null;
        }
        return new CustomColourInput(colourValue, displayName);
    }

    private String nextCustomColourKey(String displayName) {
        String baseKey = plugin.getConfigManager().normalize(displayName);
        if (baseKey.isBlank()) {
            baseKey = "custom_colour";
        }
        if (plugin.getColorRegistry().getColour(baseKey).isEmpty()) {
            return baseKey;
        }

        int suffix = 2;
        while (plugin.getColorRegistry().getColour(baseKey + "_" + suffix).isPresent()) {
            suffix++;
        }
        return baseKey + "_" + suffix;
    }

    private CustomColourValue parseCustomColourValue(String rawInput) {
        if (rawInput == null) {
            return null;
        }

        String input = rawInput.trim();
        if (input.matches("(?i)&[0-9a-f]")) {
            String legacyCode = "&" + Character.toLowerCase(input.charAt(1));
            return new CustomColourValue("LEGACY", null, legacyCode, "\\" + legacyCode);
        }

        if (input.matches("(?i)&#[0-9a-f]{6}")) {
            input = input.substring(1);
        } else if (input.matches("(?i)<#[0-9a-f]{6}>")) {
            input = input.substring(1, input.length() - 1);
        } else if (input.matches("(?i)[0-9a-f]{6}")) {
            input = "#" + input;
        }

        if (!input.matches("(?i)#[0-9a-f]{6}")) {
            return null;
        }

        String normalizedHex = "#" + input.substring(1).toLowerCase(Locale.ROOT);
        return new CustomColourValue("HEX", normalizedHex, null, "\\#" + normalizedHex.substring(1));
    }

    private boolean reloadOrNotify(Player player) {
        FoReloadResult result = plugin.reloadPluginState();
        if (result.successful()) {
            return true;
        }

        plugin.sendReloadResult(player, result);
        return false;
    }

    private int nextPageSlot(int inventorySize) {
        return Math.max(0, inventorySize - 1);
    }

    private int maxPage(int totalEntries, int pageSize) {
        if (pageSize <= 0 || totalEntries <= 0) {
            return 0;
        }
        return (totalEntries - 1) / pageSize;
    }

    private void setEmptyColourSlot(int slot) {
        setItem(slot, GuiItems.create(
                plugin.getConfigManager().getMaterial("gui.shared.empty-colour-material", Material.LIGHT_GRAY_STAINED_GLASS_PANE),
                " ",
                List.of()
        ));
    }

    private void drawPageButtons(int activePage, int maxPage) {
        if (activePage > 0) {
            setItem(previousPageSlot(inventory.getSize()), BUTTONS.previousPage(activePage - 1, maxPage));
        }
        if (activePage < maxPage) {
            setItem(nextPageSlot(inventory.getSize()), BUTTONS.nextPage(activePage + 1, maxPage));
        }
    }

    private record CustomColourValue(String type, String hex, String legacyCode, String visibleValue) {
    }

    private record CustomColourInput(CustomColourValue colourValue, String displayName) {
    }
}

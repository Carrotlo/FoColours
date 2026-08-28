package me.foesio.foColours.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiItems;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.foColours.FoColours;
import me.foesio.foColours.color.ChatColorDefinition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class ColourConfigMenu {

    private static final GuiButtonConfig BUTTONS = GuiButtonConfig.defaults();
    private static final int ADD_CUSTOM_INPUT_MAX_LENGTH = 43;

    private final FoColours plugin;
    private final int page;
    private final String filter;

    public ColourConfigMenu(FoColours plugin) {
        this(plugin, 0, "");
    }

    ColourConfigMenu(FoColours plugin, int page) {
        this(plugin, page, "");
    }

    ColourConfigMenu(FoColours plugin, int page, String filter) {
        this.plugin = plugin;
        this.page = Math.max(0, page);
        this.filter = filter == null ? "" : filter.trim();
    }

    static boolean owns(EntryBrowserHolder holder) {
        return holder != null && holder.request().context() instanceof ColourBrowserContext;
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
        EntryBrowserMenus.open(player, request());
    }

    void handleEntryBrowserClick(InventoryClickEvent event, EntryBrowserHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        EntryBrowserClick click = EntryBrowserMenus.handleClick(event.getRawSlot(), holder, event.getClick());
        switch (click.action()) {
            case ENTRY -> handleColourClick(player, click.entryId(), click.clickType(), event.getCursor(), holder.request().page());
            case ADD -> openAddCustomColourInput(player, holder.request().page(), holder.request().filter());
            case BACK -> {
                plugin.getEditorSounds().back(player);
                new AdminEditorMenu(plugin).openSilently(player);
            }
            case SEARCH -> openSearchInput(player, holder.request().filter());
            case CLEAR_SEARCH -> {
                plugin.getEditorSounds().clearSearch(player);
                new ColourConfigMenu(plugin, 0, "").openSilently(player);
            }
            case PREVIOUS_PAGE -> {
                plugin.getEditorSounds().previousPage(player);
                new ColourConfigMenu(plugin, holder.request().page() - 1, holder.request().filter()).openSilently(player);
            }
            case NEXT_PAGE -> {
                plugin.getEditorSounds().nextPage(player);
                new ColourConfigMenu(plugin, holder.request().page() + 1, holder.request().filter()).openSilently(player);
            }
            case EXTRA, NONE -> {
            }
        }
    }

    private EntryBrowserRequest request() {
        List<EntryBrowserRequest.Entry> entries = filteredDefinitions().stream()
                .map(definition -> EntryBrowserRequest.Entry.of(definition.getKey(), colourItem(definition)))
                .toList();
        return EntryBrowserRequest.builder()
                .title(plugin.getConfigManager().title("gui.colour-config-menu.title"))
                .entries(entries)
                .page(page)
                .filter(filter)
                .buttons(BUTTONS)
                .showBack(true)
                .addButton(GuiItems.create(
                        plugin.getConfigManager().getMaterial("gui.colour-config-menu.items.add-custom.material", Material.ANVIL),
                        plugin.getConfigManager().message("gui.colour-config-menu.items.add-custom.name"),
                        plugin.getConfigManager().messageList("gui.colour-config-menu.items.add-custom.lore", Map.of())
                ))
                .context(new ColourBrowserContext())
                .build();
    }

    private List<ChatColorDefinition> filteredDefinitions() {
        String normalized = filter.toLowerCase(Locale.ROOT);
        return plugin.getColorRegistry().getAllColours().stream()
                .filter(definition -> normalized.isBlank()
                        || definition.getKey().toLowerCase(Locale.ROOT).contains(normalized)
                        || definition.getDisplayName().toLowerCase(Locale.ROOT).contains(normalized)
                        || definition.getPreviewLabel(plugin.getConfigManager()).toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    private ItemStack colourItem(ChatColorDefinition definition) {
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

        return GuiItems.create(
                definition.getMaterial(),
                plugin.getConfigManager().message("gui.colour-config-menu.items.colour.name", placeholders),
                plugin.getConfigManager().messageList("gui.colour-config-menu.items.colour.lore", placeholders),
                definition.getKey().equals(plugin.getConfigManager().getDefaultColourKey())
        );
    }

    private void handleColourClick(Player player, String key, ClickType clickType, ItemStack cursor, int returnPage) {
        ChatColorDefinition definition = plugin.getColorRegistry().getColour(key).orElse(null);
        if (definition == null) {
            plugin.getEditorSounds().error(player);
            new ColourConfigMenu(plugin, returnPage, filter).openSilently(player);
            return;
        }

        if (definition.isCustom() && cursor != null && cursor.getType() != Material.AIR) {
            plugin.getConfigManager().set("colours.custom." + definition.getKey() + ".material", cursor.getType().name());
            if (!reloadOrNotify(player)) {
                plugin.getEditorSounds().addItemError(player);
                return;
            }
            plugin.getEditorSounds().addItem(player);
            player.sendMessage(plugin.getConfigManager().message("messages.custom-colour-item-updated", Map.of(
                    "colour", definition.getDisplayName(),
                    "item", cursor.getType().name()
            )));
            new ColourConfigMenu(plugin, returnPage, filter).openSilently(player);
            return;
        }

        if (clickType == ClickType.SHIFT_RIGHT && definition.isCustom()) {
            openRenameInput(player, definition, returnPage);
            return;
        }

        if (clickType == ClickType.SHIFT_LEFT) {
            if (!definition.isEnabled()) {
                player.sendMessage(plugin.getConfigManager().message("messages.colour-must-be-enabled-first"));
                return;
            }

            plugin.getConfigManager().set("player-data.default-colour", definition.getKey());
            if (!reloadOrNotify(player)) {
                plugin.getEditorSounds().error(player);
                return;
            }
            plugin.getEditorSounds().cycle(player);
            player.sendMessage(plugin.getConfigManager().message("messages.default-colour-set", Map.of("colour", definition.getDisplayName())));
            new ColourConfigMenu(plugin, returnPage, filter).openSilently(player);
            return;
        }

        if (clickType == ClickType.RIGHT && definition.isCustom()) {
            if (definition.getKey().equals(plugin.getConfigManager().getDefaultColourKey())) {
                player.sendMessage(plugin.getConfigManager().message("messages.cannot-remove-default-custom"));
                return;
            }

            new ConfirmDeleteCustomColourMenu(plugin, definition.getKey(), definition.getDisplayName(), returnPage, filter).open(player);
            return;
        }

        if (definition.getKey().equals(plugin.getConfigManager().getDefaultColourKey())) {
            player.sendMessage(plugin.getConfigManager().message("messages.cannot-disable-default"));
            return;
        }

        String path = (definition.isCustom() ? "colours.custom." : "colours.built-in.") + definition.getKey() + ".enabled";
        plugin.getConfigManager().set(path, !definition.isEnabled());
        if (!reloadOrNotify(player)) {
            plugin.getEditorSounds().error(player);
            return;
        }
        plugin.getEditorSounds().toggle(player, !definition.isEnabled());
        player.sendMessage(plugin.getConfigManager().message("messages.editor-saved"));
        new ColourConfigMenu(plugin, returnPage, filter).openSilently(player);
    }

    private void openSearchInput(Player player, String currentFilter) {
        plugin.getEditorSounds().search(player);
        TextDialogRequest request = new TextDialogRequest(
                "Search",
                List.of("Search colour names, keys, or previews."),
                "Search",
                currentFilter,
                "Colour name or key",
                DialogButton.search(),
                DialogButton.cancel(),
                320,
                300,
                64,
                true,
                true,
                false
        );
        plugin.openEditorTextInput(
                player,
                request,
                input -> true,
                input -> new ColourConfigMenu(plugin, 0, input).openSilently(player),
                () -> new ColourConfigMenu(plugin, page, currentFilter).openSilently(player)
        );
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
                        plugin.getEditorSounds().error(player);
                        return;
                    }
                    plugin.getSounds().play(player, "colour.rename");
                    player.sendMessage(plugin.getConfigManager().message("messages.custom-colour-renamed", Map.of(
                            "colour", input
                    )));
                },
                () -> new ColourConfigMenu(plugin, activePage, filter).openSilently(player)
        );
    }

    private void openAddCustomColourInput(Player player, int activePage, String activeFilter) {
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
                        plugin.getEditorSounds().error(player);
                        return;
                    }
                    plugin.getEditorSounds().add(player);
                    player.sendMessage(plugin.getConfigManager().message("messages.custom-colour-added", plugin.getChatColorService().basePlaceholders(
                            "colour", displayName,
                            "value", colourValue.visibleValue()
                    )));
                },
                () -> new ColourConfigMenu(plugin, activePage, activeFilter).openSilently(player)
        );
    }

    private boolean isValidCustomColourInput(String input) {
        CustomColourInput customInput = parseCustomColourInput(input);
        if (customInput == null) {
            return false;
        }
        String displayName = customInput.displayName();
        CustomColourValue colourValue = customInput.colourValue();
        return colourValue != null && !displayName.isBlank() && !nextCustomColourKey(displayName).isBlank();
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

    private record ColourBrowserContext() {
    }

    private record CustomColourValue(String type, String hex, String legacyCode, String visibleValue) {
    }

    private record CustomColourInput(CustomColourValue colourValue, String displayName) {
    }
}

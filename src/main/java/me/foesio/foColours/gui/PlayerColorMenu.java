package me.foesio.foColours.gui;

import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiItems;
import me.foesio.core.gui.GuiSlots;
import me.foesio.foColours.FoColours;
import me.foesio.foColours.color.ChatColorDefinition;
import me.foesio.foColours.service.ChatColorService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlayerColorMenu extends BaseMenu {

    private static final GuiButtonConfig BUTTONS = GuiButtonConfig.defaults();
    private static final int RESET_SLOT = 48;
    private static final int INFO_SLOT = 50;

    private final ChatColorService service;
    private final int page;

    public PlayerColorMenu(FoColours plugin) {
        this(plugin, 0);
    }

    @Override
    protected void openSound(Player player) {
        plugin.getSounds().play(player, "player.open");
    }

    private PlayerColorMenu(FoColours plugin, int page) {
        super(plugin, plugin.getConfigManager().getGuiSize("gui.player-menu.rows", 6), plugin.getConfigManager().title("gui.player-menu.title"));
        this.service = plugin.getChatColorService();
        this.page = Math.max(0, page);
    }

    @Override
    protected void draw(Player viewer) {
        fill(plugin.getConfigManager().getMaterial("gui.shared.filler-material", Material.GRAY_STAINED_GLASS_PANE));

        List<Integer> colourSlots = plugin.getConfigManager().getGuiSlots("gui.shared.colour-slots", inventory.getSize());
        List<ChatColorDefinition> definitions = visibleColours(viewer);
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
            boolean unlocked = service.hasUnlocked(viewer, definition.getKey());

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("colour", definition.getDisplayName());
            placeholders.put("preview", definition.getPreviewLabel(plugin.getConfigManager()));
            placeholders.put("status", unlocked
                    ? plugin.getConfigManager().messageForViewer(viewer, "gui.states.unlocked", Map.of())
                    : plugin.getConfigManager().messageForViewer(viewer, "gui.states.locked", Map.of()));

            boolean selected = unlocked && service.isSelected(viewer, definition.getKey());
            String lorePath = unlocked ? "gui.player-menu.items.colour-unlocked.lore" : "gui.player-menu.items.colour-locked.lore";

            String itemPath = unlocked ? "gui.player-menu.items.colour-unlocked" : "gui.player-menu.items.colour-locked";
            Material material = unlocked
                    ? definition.getMaterial()
                    : plugin.getConfigManager().getMaterial("gui.player-menu.locked-material", Material.BARRIER);
            setItem(colourSlots.get(index), guiItem(
                    viewer,
                    itemPath,
                    material,
                    placeholders,
                    selected
            ));
        }

        drawPageButtons(viewer, activePage, maxPage);
        setItem(resetSlot(inventory.getSize()), guiItem(viewer, "gui.player-menu.items.reset", Material.FEATHER,
                service.commandContext(viewer), false));
        setItem(infoSlot(inventory.getSize()), guiItem(viewer, "gui.player-menu.items.info", Material.BOOK,
                service.commandContext(viewer), false, buildInfoLore(viewer)));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        int inventorySize = event.getView().getTopInventory().getSize();
        List<Integer> colourSlots = plugin.getConfigManager().getGuiSlots("gui.shared.colour-slots", inventorySize);
        List<ChatColorDefinition> definitions = visibleColours(player);
        int pageSize = colourSlots.size();
        int maxPage = maxPage(definitions.size(), pageSize);
        int activePage = Math.min(page, maxPage);

        if (slot == previousPageSlot(inventorySize) && activePage > 0) {
            plugin.getSounds().play(player, "player.page-previous");
            new PlayerColorMenu(plugin, activePage - 1).openSilently(player);
            return;
        }
        if (slot == nextPageSlot(inventorySize) && activePage < maxPage) {
            plugin.getSounds().play(player, "player.page-next");
            new PlayerColorMenu(plugin, activePage + 1).openSilently(player);
            return;
        }
        if (slot == resetSlot(inventorySize)) {
            service.resetColour(player);
            plugin.getSounds().play(player, "player.reset");
            player.sendMessage(plugin.getConfigManager().messageForViewer(player, "messages.reset-success", service.commandContext(player)));
            new PlayerColorMenu(plugin, activePage).openSilently(player);
            return;
        }

        int index = colourSlots.indexOf(slot);
        int definitionIndex = activePage * pageSize + index;
        if (index < 0 || definitionIndex >= definitions.size()) {
            return;
        }

        ChatColorDefinition definition = definitions.get(definitionIndex);
        if (!service.hasUnlocked(player, definition.getKey())) {
            plugin.getSounds().play(player, "player.error");
            player.sendMessage(plugin.getConfigManager().messageForViewer(player, "messages.not-unlocked",
                    service.commandContext(player, "colour", definition.getDisplayName())));
            return;
        }

        if (service.isSelected(player, definition.getKey())) {
            plugin.getSounds().play(player, "player.error");
            player.sendMessage(plugin.getConfigManager().messageForViewer(player, "messages.already-selected",
                    service.commandContext(player, "colour", definition.getDisplayName())));
            return;
        }

        service.selectColour(player, definition.getKey());
        plugin.getSounds().playWithPitchVariation(player, "player.select", 0.05f);
        player.sendMessage(plugin.getConfigManager().messageForViewer(player, "messages.select-success",
                service.commandContext(player, "colour", definition.getDisplayName())));
        new PlayerColorMenu(plugin, activePage).openSilently(player);
    }

    private List<String> buildInfoLore(Player viewer) {
        List<String> lore = new ArrayList<>(plugin.getConfigManager().messageListForViewer(
                viewer, "gui.player-menu.items.info.lore", service.commandContext(viewer)));
        List<ChatColorDefinition> unlocked = service.getUnlockedColours(viewer);
        if (unlocked.isEmpty()) {
            lore.add(plugin.getConfigManager().messageForViewer(viewer,
                    "gui.player-menu.items.info.empty-unlocked", Map.of()));
            return lore;
        }

        lore.add(plugin.getConfigManager().messageForViewer(viewer,
                "gui.player-menu.items.info.unlocked-header", Map.of()));
        for (ChatColorDefinition definition : unlocked) {
            lore.add(plugin.getConfigManager().messageForViewer(viewer,
                    "gui.player-menu.items.info.unlocked-entry", Map.of(
                    "colour", definition.getDisplayName()
            )));
        }
        return lore;
    }

    private int resetSlot(int inventorySize) {
        return plugin.getConfigManager().getGuiSlot("gui.player-menu.items.reset.slot", RESET_SLOT, inventorySize);
    }

    private int infoSlot(int inventorySize) {
        return plugin.getConfigManager().getGuiSlot("gui.player-menu.items.info.slot", INFO_SLOT, inventorySize);
    }

    private int previousPageSlot(int inventorySize) {
        return Math.max(0, inventorySize - GuiSlots.ROW_SIZE);
    }

    private int nextPageSlot(int inventorySize) {
        return Math.max(0, inventorySize - 1);
    }

    private List<ChatColorDefinition> visibleColours(Player player) {
        return plugin.getColorRegistry().getEnabledColours().stream()
                .filter(definition -> plugin.getConfigManager().isShowLockedColours() || service.hasUnlocked(player, definition.getKey()))
                .toList();
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

    private void drawPageButtons(Player viewer, int activePage, int maxPage) {
        if (activePage > 0) {
            setItem(previousPageSlot(inventory.getSize()), BUTTONS.previousPage(viewer, activePage - 1, maxPage));
        }
        if (activePage < maxPage) {
            setItem(nextPageSlot(inventory.getSize()), BUTTONS.nextPage(viewer, activePage + 1, maxPage));
        }
    }

    private org.bukkit.inventory.ItemStack guiItem(Player viewer, String path, Material fallbackMaterial,
            Map<String, String> placeholders, boolean forceGlow) {
        return guiItem(viewer, path, fallbackMaterial, placeholders, forceGlow,
                plugin.getConfigManager().messageListForViewer(viewer, path + ".lore", placeholders));
    }

    private org.bukkit.inventory.ItemStack guiItem(Player viewer, String path, Material fallbackMaterial,
            Map<String, String> placeholders, boolean forceGlow, List<String> lore) {
        int amount = plugin.getConfigManager().getBoundedInt(path + ".amount", 1, 1, 64);
        boolean glow = forceGlow || plugin.getConfigManager().getBoolean(path + ".glow", false);
        Material material = plugin.getConfigManager().getMaterial(path + ".material", fallbackMaterial);
        String name = plugin.getConfigManager().messageForViewer(viewer, path + ".name", placeholders);
        if ("gui.player-menu.items.colour-unlocked".equals(path)) {
            name = DialogIcons.withMaterialIcon(name, material);
        }
        return GuiItems.create(viewer,
                material,
                name,
                lore,
                glow,
                amount,
                plugin.getConfigManager().getOptionalInt(path + ".custom-model-data")
        );
    }
}

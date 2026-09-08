package me.foesio.foColours.gui;

import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.gui.GuiButtons;
import me.foesio.core.gui.GuiItems;
import me.foesio.foColours.FoColours;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;

public final class ConfirmDeleteCustomColourMenu extends BaseMenu {

    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 22;

    private final String colourKey;
    private final String displayName;
    private final int returnPage;
    private final String returnFilter;
    private boolean handled;

    public ConfirmDeleteCustomColourMenu(FoColours plugin, String colourKey, String displayName) {
        this(plugin, colourKey, displayName, 0);
    }

    public ConfirmDeleteCustomColourMenu(FoColours plugin, String colourKey, String displayName, int returnPage) {
        this(plugin, colourKey, displayName, returnPage, "");
    }

    public ConfirmDeleteCustomColourMenu(FoColours plugin, String colourKey, String displayName, int returnPage, String returnFilter) {
        super(plugin, 27, plugin.getConfigManager().title("gui.confirm-delete-colour.title"));
        this.colourKey = colourKey;
        this.displayName = displayName;
        this.returnPage = Math.max(0, returnPage);
        this.returnFilter = returnFilter == null ? "" : returnFilter;
    }

    @Override
    protected void draw(Player viewer) {
        fill(plugin.getConfigManager().getMaterial("gui.shared.filler-material", Material.GRAY_STAINED_GLASS_PANE));
        setItem(CONFIRM_SLOT, GuiItems.create(viewer,
                Material.LAVA_BUCKET,
                plugin.getConfigManager().messageForViewer(viewer,
                        "gui.confirm-delete-colour.items.confirm.name", placeholders()),
                plugin.getConfigManager().messageListForViewer(viewer,
                        "gui.confirm-delete-colour.items.confirm.lore", placeholders())
        ));
        setItem(CANCEL_SLOT, GuiButtons.back(viewer));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getRawSlot() == CONFIRM_SLOT) {
            handled = true;
            plugin.getConfigManager().set("colours.custom." + colourKey, null);
            FoReloadResult result = plugin.reloadPluginState();
            if (!result.successful()) {
                plugin.sendReloadResult(player, result);
                return;
            }
            plugin.getEditorSounds().delete(player);
            player.sendMessage(plugin.getConfigManager().messageForViewer(player, "messages.custom-colour-removed", placeholders()));
            new ColourConfigMenu(plugin, returnPage, returnFilter).openSilently(player);
            return;
        }

        if (event.getRawSlot() == CANCEL_SLOT) {
            handled = true;
            plugin.getEditorSounds().back(player);
            new ColourConfigMenu(plugin, returnPage, returnFilter).openSilently(player);
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (handled || !(event.getPlayer() instanceof Player player)) {
            return;
        }

        plugin.coreScheduler().runForPlayer(player, () -> new ColourConfigMenu(plugin, returnPage, returnFilter).openSilently(player));
    }

    private Map<String, String> placeholders() {
        return Map.of("colour", displayName);
    }
}

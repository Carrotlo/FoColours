package me.foesio.foColours.gui;

import me.foesio.core.editor.EditorMenuHolder;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.foColours.FoColours;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    private final FoColours plugin;

    public MenuListener(FoColours plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EntryBrowserHolder entryHolder
                && ColourConfigMenu.owns(entryHolder)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                new ColourConfigMenu(plugin, entryHolder.request().page(), entryHolder.request().filter())
                        .handleEntryBrowserClick(event, entryHolder);
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof EditorMenuHolder editorHolder
                && AdminEditorMenu.owns(editorHolder)) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()
                    || !(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            new AdminEditorMenu(plugin).handleClick(player, editorHolder, event.getRawSlot());
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof BaseMenu menu)) {
            return;
        }

        // Keep shift-clicks, hotbar swaps, and other bottom-inventory actions
        // from moving items into a public menu. Only top-inventory clicks are
        // dispatched to its control handler.
        event.setCancelled(true);
        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory())) {
            menu.handleClick(event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if ((event.getView().getTopInventory().getHolder() instanceof EntryBrowserHolder
                || event.getView().getTopInventory().getHolder() instanceof BaseMenu
                || event.getView().getTopInventory().getHolder() instanceof EditorMenuHolder editorHolder
                && AdminEditorMenu.owns(editorHolder))
                && event.getRawSlots().stream().anyMatch(rawSlot -> rawSlot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && plugin.consumeSuppressedInventoryClose(player)) {
            return;
        }

        if (event.getInventory().getHolder() instanceof BaseMenu menu) {
            menu.handleClose(event);
        }
    }
}

package me.foesio.foColours.gui;

import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.gui.GuiItems;
import me.foesio.foColours.FoColours;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;

public abstract class BaseMenu implements InventoryHolder {

    protected final FoColours plugin;
    private final int size;
    private final String title;
    protected Inventory inventory;

    protected BaseMenu(FoColours plugin, int size, String title) {
        this.plugin = plugin;
        this.size = size;
        this.title = title;
    }

    public void open(Player player) {
        open(player, true);
    }

    protected void openSilently(Player player) {
        open(player, false);
    }

    private void open(Player player, boolean playOpenSound) {
        inventory = Bukkit.createInventory(this, size, title);
        draw(player);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null) {
                inventory.setItem(slot, DialogIcons.forViewer(player, item));
            }
        }
        if (playOpenSound) {
            openSound(player);
        }
        player.openInventory(inventory);
    }

    protected void openSound(Player player) {
        plugin.getEditorSounds().open(player);
    }

    protected abstract void draw(Player viewer);

    public abstract void handleClick(InventoryClickEvent event);

    public void handleClose(InventoryCloseEvent event) {
    }

    protected void fill(Material material) {
        ItemStack filler = GuiItems.create(material, " ", Collections.emptyList());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    protected void setItem(int slot, ItemStack itemStack) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, itemStack);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

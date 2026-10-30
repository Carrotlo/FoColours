package me.foesio.foColours.gui;

import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.foColours.color.ChatColorDefinition;
import me.foesio.foColours.color.ColorType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlayerColorMenuAppearanceTest {

    @Test
    void unlockedColourMaterialKeepsEveryConfiguredPresentationField() {
        PublicGuiDefinition.FixedItem configured = new PublicGuiDefinition.FixedItem(
                Material.PLAYER_HEAD, "Grün {colour}", List.of("Blå {status}"), 2,
                12345, true, Set.of(ItemFlag.HIDE_ATTRIBUTES));
        ChatColorDefinition colour = new ChatColorDefinition("green", "Grün", List.of(),
                ColorType.LEGACY, "&a", Material.GREEN_DYE, true, true, false);

        PublicGuiDefinition.FixedItem appearance = PlayerColorMenu.appearanceForColour(configured, colour, true);

        assertEquals(Material.GREEN_DYE, appearance.material());
        assertEquals(configured.name(), appearance.name());
        assertEquals(configured.lore(), appearance.lore());
        assertEquals(configured.amount(), appearance.amount());
        assertEquals(configured.customModelData(), appearance.customModelData());
        assertEquals(configured.glow(), appearance.glow());
        assertEquals(configured.itemFlags(), appearance.itemFlags());
        assertSame(configured, PlayerColorMenu.appearanceForColour(configured, colour, false));
    }
}

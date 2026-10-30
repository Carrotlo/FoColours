package me.foesio.foColours.api;

import me.foesio.foColours.FoColours;
import me.foesio.foColours.color.ChatColorDefinition;
import me.foesio.foColours.color.ColorRegistry;
import me.foesio.foColours.color.ColorType;
import me.foesio.foColours.compat.EzColorsCompatService;
import me.foesio.foColours.config.ConfigManager;
import me.foesio.foColours.data.PlayerColorData;
import me.foesio.foColours.data.PlayerDataManager;
import me.foesio.foColours.service.ChatColorService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoColoursApiTest {
    @Test
    void selectionReportsStoredNoOpEvenWhenEffectiveColourDiffers() {
        FoColours plugin = mock(FoColours.class);
        ChatColorService service = mock(ChatColorService.class);
        PlayerDataManager dataManager = mock(PlayerDataManager.class);
        EzColorsCompatService compat = mock(EzColorsCompatService.class);
        Player player = mock(Player.class);
        ChatColorDefinition red = new ChatColorDefinition("red", "Red", List.of("red"),
                ColorType.LEGACY, "&c", Material.RED_DYE, true, true, false);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getChatColorService()).thenReturn(service);
        when(plugin.getPlayerDataManager()).thenReturn(dataManager);
        when(plugin.getEzColorsCompatService()).thenReturn(compat);
        when(service.hasEnabledColours()).thenReturn(true);
        when(service.hasUnlocked(player, "red")).thenReturn(true);
        when(compat.findCompatibleColour("red")).thenReturn(Optional.of(red));
        when(dataManager.loadPlayer(player)).thenReturn(new PlayerColorData("test", "red", false,
                List.of("red"), List.of(), List.of()));

        FoColoursApi api = new FoColoursApiImpl(plugin);
        assertEquals(ColourChangeResult.NO_CHANGE, api.selectColour(player, "red"));
        verify(service, never()).selectColour(any(), anyString(), any());

        when(dataManager.loadPlayer(player)).thenReturn(new PlayerColorData("test", "light_gray", false,
                List.of("red"), List.of(), List.of()));
        when(service.selectColour(player, "red", ColourChangeCause.API)).thenReturn(true);
        assertEquals(ColourChangeResult.CHANGED, api.selectColour(player, "red"));
    }

    @Test
    void renderTextUsesRainbowEvenOutsideAutomaticChatMode() {
        FoColours plugin = mock(FoColours.class);
        ChatColorService service = mock(ChatColorService.class);
        ConfigManager config = mock(ConfigManager.class);
        Player player = mock(Player.class);
        ChatColorDefinition rainbow = new ChatColorDefinition("rainbow", "Rainbow", List.of("rainbow"),
                ColorType.RAINBOW, "", Material.NAME_TAG, true, true, false);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getChatColorService()).thenReturn(service);
        when(plugin.getConfigManager()).thenReturn(config);
        when(service.hasEnabledColours()).thenReturn(true);
        when(service.getActiveColour(player)).thenReturn(rainbow);
        when(config.getRainbowPalette()).thenReturn(List.of("#ff0000", "#00ff00"));

        String rendered = new FoColoursApiImpl(plugin).renderText(player, "&bHi");
        assertTrue(rendered.contains("H"));
        assertTrue(rendered.contains("i"));
        assertFalse(rendered.contains("&b"));
        assertTrue(rendered.startsWith("§x"));
    }
}

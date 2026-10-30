package me.foesio.foColours.api;

import me.foesio.foColours.FoColours;
import me.foesio.foColours.api.event.PlayerColourAccessChangedEvent;
import me.foesio.foColours.api.event.PlayerColourSelectionChangedEvent;
import me.foesio.foColours.config.ConfigManager;
import me.foesio.foColours.data.KnownPlayer;
import me.foesio.foColours.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerColourEventsTest {
    @TempDir Path folder;

    @Test
    void committedGrantSelectionAndTakeEmitOrderedEventsAndPersist() {
        FoColours plugin = mock(FoColours.class);
        ConfigManager config = mock(ConfigManager.class);
        PluginManager events = mock(PluginManager.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(config.getDefaultColourKey()).thenReturn("light_gray");
        when(config.getDefaultUnlockedColours()).thenReturn(Set.of("light_gray"));
        when(config.normalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID uuid = UUID.randomUUID();
        KnownPlayer known = new KnownPlayer(uuid, "Tester");
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Tester");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            PlayerDataManager data = new PlayerDataManager(plugin, config);
            data.initialize();
            assertTrue(data.grantColour(known, "red", ColourChangeCause.API));
            assertFalse(data.grantColour(known, "red", ColourChangeCause.API));
            assertTrue(data.setSelectedColour(player, "red", ColourChangeCause.PLAYER_COMMAND));
            assertTrue(data.takeColour(known, "red", "light_gray", false, ColourChangeCause.API));

            ArgumentCaptor<Event> captured = ArgumentCaptor.forClass(Event.class);
            verify(events, times(4)).callEvent(captured.capture());
            List<Event> emitted = captured.getAllValues();
            assertInstanceOf(PlayerColourAccessChangedEvent.class, emitted.get(0));
            assertTrue(((PlayerColourAccessChangedEvent) emitted.get(0)).isUnlocked());
            assertInstanceOf(PlayerColourSelectionChangedEvent.class, emitted.get(1));
            assertEquals("red", ((PlayerColourSelectionChangedEvent) emitted.get(1)).getNewKey());
            assertInstanceOf(PlayerColourAccessChangedEvent.class, emitted.get(2));
            assertFalse(((PlayerColourAccessChangedEvent) emitted.get(2)).isUnlocked());
            assertInstanceOf(PlayerColourSelectionChangedEvent.class, emitted.get(3));
            assertEquals("light_gray", ((PlayerColourSelectionChangedEvent) emitted.get(3)).getNewKey());
            assertFalse(emitted.get(0).isAsynchronous());

            data.flushAll();
            PlayerDataManager reopened = new PlayerDataManager(plugin, config);
            reopened.initialize();
            assertEquals("light_gray", reopened.getOrLoad(known).getSelectedColour());
            assertFalse(reopened.getOrLoad(known).hasUnlocked("red"));
            reopened.flushAll();
        }
    }

    @Test
    void failedSaveLeavesCacheAndEventsUntouched() throws Exception {
        FoColours plugin = mock(FoColours.class);
        ConfigManager config = mock(ConfigManager.class);
        PluginManager events = mock(PluginManager.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(config.getDefaultColourKey()).thenReturn("light_gray");
        when(config.getDefaultUnlockedColours()).thenReturn(Set.of("light_gray"));
        when(config.normalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        KnownPlayer known = new KnownPlayer(UUID.randomUUID(), "Tester");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            PlayerDataManager data = new PlayerDataManager(plugin, config);
            data.initialize();
            data.getOrLoad(known);
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("data.db"));
                 var statement = connection.createStatement()) {
                statement.execute("DROP TABLE player_colours");
            }

            assertThrows(IllegalStateException.class,
                    () -> data.grantColour(known, "red", ColourChangeCause.API));
            assertFalse(data.getOrLoad(known).hasUnlocked("red"));
            verifyNoInteractions(events);
        }
    }
}

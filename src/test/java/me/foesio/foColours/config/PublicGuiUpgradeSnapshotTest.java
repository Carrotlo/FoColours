package me.foesio.foColours.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicGuiUpgradeSnapshotTest {

    @TempDir Path temporary;

    @Test
    void completedInstallDoesNotRecreateOwnerDeletedResourcesOnReload() {
        assertTrue(ConfigManager.resourcesNeeded(true, false));
        assertTrue(ConfigManager.resourcesNeeded(false, true));
        assertFalse(ConfigManager.resourcesNeeded(false, false));
    }

    @Test
    void laterResourceFailureRestoresEveryPreAttemptActiveFile() throws Exception {
        Path config = temporary.resolve("config.yml");
        Path gui = temporary.resolve("guis/player-menu.yml");
        Path messages = temporary.resolve("messages.yml");
        Path sounds = temporary.resolve("sounds.yml");
        Path marker = temporary.resolve(ConfigManager.PUBLIC_GUI_INSTALL_MARKER);
        Files.createDirectories(gui.getParent());
        byte[] configBefore = "gui: old\n".getBytes(StandardCharsets.UTF_8);
        byte[] messagesBefore = "messages: {select-success: 'Grün'}\n".getBytes(StandardCharsets.UTF_8);
        Files.write(config, configBefore);
        Files.write(messages, messagesBefore);
        byte[] markerBefore = "install in progress\n".getBytes(StandardCharsets.UTF_8);
        Files.write(marker, markerBefore);
        PublicGuiUpgradeSnapshot snapshot = PublicGuiUpgradeSnapshot.capture(temporary);

        Files.writeString(config, "public-gui-schema-version: 3\n");
        Files.writeString(gui, "menu-id: colours.player\n");
        Files.writeString(messages, "messages: {select-success: default}\n");
        Files.writeString(sounds, "player.open: {enabled: true}\n");
        Files.delete(marker);

        assertTrue(snapshot.rollback());

        assertArrayEquals(configBefore, Files.readAllBytes(config));
        assertArrayEquals(messagesBefore, Files.readAllBytes(messages));
        assertFalse(Files.exists(gui));
        assertFalse(Files.exists(sounds));
        assertArrayEquals(markerBefore, Files.readAllBytes(marker));
        assertFalse(snapshot.rollback());
    }
}

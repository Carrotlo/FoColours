package me.foesio.foColours.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PublicTextMigrationTest {

    @Test
    void releasedTextUpgradeKeepsCustomValuesAndOwnerRemovals() throws Exception {
        YamlConfiguration released = resource("migrations/player-menu-v1.yml");
        YamlConfiguration owner = new YamlConfiguration();
        owner.loadFromString(released.saveToString());
        owner.set(PlayerMenuGuiMigration.PENDING_PATH, true);
        owner.set("messages.prefix", "Mi prefijo");
        owner.set("style.tokens.theme", "#ff8800");
        owner.set("messages.select-success", "¡Listo, {colour}!");
        owner.set("messages.reset-disabled", "Reinicio desactivado");
        owner.set("messages.no-permission", null);
        owner.set("messages.already-selected", 17);
        YamlConfiguration bundled = resource("messages.yml");
        YamlConfiguration target = new YamlConfiguration();
        target.loadFromString(bundled.saveToString());
        List<String> warnings = new ArrayList<>();

        assertTrue(PublicTextMigration.fromReleasedConfig(owner, released, target, bundled, warnings::add));

        assertEquals("Mi prefijo", target.getString("tokens.prefix"));
        assertEquals("#ff8800", target.getString("tokens.theme"));
        assertEquals("¡Listo, {colour}!", target.getString("messages.select-success"));
        assertEquals("Reinicio desactivado", target.getString("messages.reset-disabled"));
        assertFalse(target.contains("messages.no-permission", false));
        assertEquals(bundled.getString("messages.player-only"), target.getString("messages.player-only"));
        assertEquals(bundled.getString("messages.already-selected"), target.getString("messages.already-selected"));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("messages.already-selected")));
    }

    @Test
    void freshCurrentConfigDoesNotEraseBundledMessages() throws Exception {
        YamlConfiguration owner = resource("config.yml");
        YamlConfiguration bundled = resource("messages.yml");
        YamlConfiguration target = new YamlConfiguration();
        target.loadFromString(bundled.saveToString());

        assertFalse(PublicTextMigration.fromReleasedConfig(owner, resource("migrations/player-menu-v1.yml"),
                target, bundled, line -> fail(line)));

        assertEquals(bundled.saveToString(), target.saveToString());
        assertNotNull(target.getString("messages.select-success"));
        assertNotNull(target.getString("tokens.prefix"));
    }

    @Test
    void pendingMigrationDetectsUnsavedMessageValues() throws Exception {
        YamlConfiguration saved = resource("messages.yml");
        YamlConfiguration served = new YamlConfiguration();
        served.loadFromString(saved.saveToString());
        assertTrue(PublicTextMigration.sameValues(served, saved));

        served.set("messages.select-success", "Grün bestätigt");
        assertFalse(PublicTextMigration.sameValues(served, saved));
        saved.set("messages.select-success", "Grün bestätigt");
        assertTrue(PublicTextMigration.sameValues(served, saved));
    }

    private YamlConfiguration resource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}

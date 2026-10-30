package me.foesio.foColours.color;

import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorRegistryCompatibilityTest {

    @Test
    void releasedOwnerColourOmissionsKeepTheirOldMeaning() {
        YamlConfiguration owner = new YamlConfiguration();
        assertEquals(Optional.of(ColorType.HEX), ColorRegistry.readType(owner, true));
        assertEquals(Optional.of(ColorType.LEGACY), ColorRegistry.readType(owner, false));
        owner.set("type", "NOT_A_TYPE");
        assertEquals(Optional.empty(), ColorRegistry.readType(owner, true));
        owner.set("type", 17);
        assertEquals(Optional.empty(), ColorRegistry.readType(owner, true));
        owner.set("type", null);
        assertEquals("Deep Blue", ColorRegistry.humanize("deep_blue"));
        assertEquals("#ffffff", ColorRegistry.readColourValue(owner, ColorType.HEX));
        assertEquals("&f", ColorRegistry.readColourValue(owner, ColorType.LEGACY));
        owner.set("hex", "invalid");
        assertEquals("invalid", ColorRegistry.readColourValue(owner, ColorType.HEX));
    }
}

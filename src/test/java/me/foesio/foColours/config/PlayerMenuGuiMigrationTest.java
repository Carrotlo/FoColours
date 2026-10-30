package me.foesio.foColours.config;

import me.foesio.core.gui.publicgui.ClickKind;
import me.foesio.core.gui.publicgui.GuiSnapshot;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiFrame;
import me.foesio.core.gui.publicgui.PublicGuiPlanner;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.gui.publicgui.PublicGuiText;
import me.foesio.foColours.gui.PlayerColorMenu;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlayerMenuGuiMigrationTest {

    @TempDir Path temporary;

    @Test
    void releasedConfigUpgradeKeepsCustomSiblingsAndReplacesOnlyInvalidFields() throws Exception {
        YamlConfiguration old = resource("migrations/player-menu-v1.yml");
        old.set("gui.shared.filler-material", "BLACK_STAINED_GLASS_PANE");
        old.set("gui.shared.colour-slots", List.of(10, 10));
        old.set("gui.player-menu.locked-material", "NOT_A_MATERIAL");
        old.set("gui.player-menu.items.colour-unlocked.name", "{theme}Grün {colour}");
        old.set("gui.player-menu.items.reset.lore", List.of());
        old.set("gui.player-menu.items.info.empty-unlocked", 17);
        List<String> warnings = new java.util.ArrayList<>();

        YamlConfiguration result = PlayerMenuGuiMigration.candidate(old,
                resource("migrations/player-menu-v1.yml"), resource("guis/player-menu.yml"), warnings::add);

        assertEquals("BLACK_STAINED_GLASS_PANE", result.getString("filler.material"));
        assertEquals(List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34), result.getIntegerList("entries.colours.slots"));
        assertEquals("BARRIER", result.getString("states.colour-locked.material"));
        assertEquals("{theme}Grün {colour}", result.getString("states.colour-unlocked.name"));
        assertEquals("{theme}Grün {colour}", result.getString("states.colour-selected.name"));
        assertEquals(List.of(), result.getStringList("buttons.reset.lore"));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("entries.colours.slots")));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("states.colour-locked.material")));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("empty-unlocked")));
        assertEquals("{white}• {theme}{available}",
                result.getStringList("buttons.info.states.empty.lore").getLast());
        PublicGuiSchema.parse(PlayerColorMenu.MENU_ID, "guis/player-menu.yml", result, PlayerColorMenu.contract());
    }

    @Test
    void releasedCleanupRetainsSharedAdminSettings() throws Exception {
        YamlConfiguration config = resource("migrations/player-menu-v1.yml");
        config.set("gui.shared.filler-material", "BLACK_STAINED_GLASS_PANE");
        config.set("gui.states.enabled", "Aktiv");
        config.set("features.reset-enabled", false);

        PlayerMenuGuiMigration.removeLegacyPublicGuiKeys(config);

        assertEquals("BLACK_STAINED_GLASS_PANE", config.getString("gui.shared.filler-material"));
        assertEquals("Aktiv", config.getString("gui.states.enabled"));
        assertFalse(config.getBoolean("features.reset-enabled"));
        assertFalse(config.contains("gui.shared.colour-slots"));
        assertFalse(config.contains("gui.player-menu"));
    }

    @Test
    void interimGuiUpgradeMovesPageSlotAndKeepsRemovedLore() throws Exception {
        YamlConfiguration old = resource("migrations/player-menu-v2.yml");
        old.set("gui.player-menu.items.previous-page.slot", 46);
        old.set("gui.player-menu.items.previous-page.name", "Vorherige Seite");
        old.set("gui.player-menu.items.colour-locked.lore", List.of());
        old.set("gui.player-menu.items.colour-unlocked.selected-glow", false);
        old.set("gui.player-menu.items.info.unlocked-entry", "• {colour}");

        YamlConfiguration result = PlayerMenuGuiMigration.candidate(old,
                resource("migrations/player-menu-v2.yml"), resource("guis/player-menu.yml"), line -> fail(line));

        assertEquals(List.of(46), result.getIntegerList("entries.previous.slots"));
        assertEquals("Vorherige Seite", result.getString("entries.previous.item.name"));
        assertEquals(List.of(), result.getStringList("states.colour-locked.lore"));
        assertFalse(result.getBoolean("states.colour-selected.glow"));
        assertEquals("• {option}", result.getString("buttons.info.cycle.unselected-format"));
        PublicGuiDefinition definition = PublicGuiSchema.parse(PlayerColorMenu.MENU_ID,
                "guis/player-menu.yml", result, PlayerColorMenu.contract());
        assertEquals(46, definition.entries().get("previous").slots().get(0));
    }

    @Test
    void releasedInfoLoreAppearsInBothUnlockedAndEmptyStatesWithoutDefaultLines() throws Exception {
        YamlConfiguration old = resource("migrations/player-menu-v1.yml");
        old.set("gui.player-menu.items.info.lore", List.of("Mi color actual"));
        old.set("gui.player-menu.items.info.empty-unlocked", "Todavía ninguno");

        YamlConfiguration result = PlayerMenuGuiMigration.candidate(old,
                resource("migrations/player-menu-v1.yml"), resource("guis/player-menu.yml"), line -> fail(line));

        assertEquals(List.of("Mi color actual", "{options}"), result.getStringList("buttons.info.lore"));
        assertEquals(List.of("Mi color actual", "Todavía ninguno"),
                result.getStringList("buttons.info.states.empty.lore"));
        PublicGuiDefinition definition = PublicGuiSchema.parse(PlayerColorMenu.MENU_ID,
                "guis/player-menu.yml", result, PlayerColorMenu.contract());
        PublicGuiFrame frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1,
                        Map.of(PlayerColorMenu.MENU_ID, definition)), PlayerColorMenu.MENU_ID,
                new PublicGuiRenderInput(Map.of(), Map.of("info", "empty"), Map.of(), Map.of()));
        assertEquals(List.of("Mi color actual", "Todavía ninguno"), frame.visuals().get(50).fixed().lore());

        old.set("gui.player-menu.items.info.lore", List.of());
        old.set("gui.player-menu.items.info.empty-unlocked", null);
        result = PlayerMenuGuiMigration.candidate(old,
                resource("migrations/player-menu-v1.yml"), resource("guis/player-menu.yml"), line -> fail(line));
        assertEquals(List.of("{options}"), result.getStringList("buttons.info.lore"));
        assertEquals(List.of(), result.getStringList("buttons.info.states.empty.lore"));
        definition = PublicGuiSchema.parse(PlayerColorMenu.MENU_ID,
                "guis/player-menu.yml", result, PlayerColorMenu.contract());
        frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1,
                        Map.of(PlayerColorMenu.MENU_ID, definition)), PlayerColorMenu.MENU_ID,
                new PublicGuiRenderInput(Map.of(), Map.of("info", "empty"), Map.of(), Map.of()));
        assertEquals(List.of(), frame.visuals().get(50).fixed().lore());
    }

    @Test
    void currentGuiMovesActionWithSlotAndPreservesEmptyLore() throws Exception {
        YamlConfiguration owner = resource("guis/player-menu.yml");
        owner.set("title", "{theme}Título");
        owner.set("buttons.reset.slot", 47);
        owner.set("buttons.reset.name", "Grün reset");
        owner.set("buttons.reset.lore", List.of());
        PublicGuiDefinition definition = PublicGuiSchema.parse(PlayerColorMenu.MENU_ID,
                "guis/player-menu.yml", owner, PlayerColorMenu.contract());
        PublicGuiFrame frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1, Map.of(PlayerColorMenu.MENU_ID, definition)),
                PlayerColorMenu.MENU_ID, new PublicGuiRenderInput(
                        Map.of("theme", "#aa22cc", "current_colour", "Blå", "default_colour", "Gray", "page", "1", "pages", "1"),
                        Map.of(), Map.of("info", new PublicGuiText.CycleSelection(List.of("Blå"), -1)), Map.of()));

        assertEquals("#aa22ccTítulo", frame.title());
        assertEquals("colours.reset", frame.bindings().get(47).actionFor(ClickKind.LEFT));
        assertEquals("Grün reset", frame.visuals().get(47).fixed().name());
        assertFalse(frame.bindings().containsKey(48));
        assertEquals(List.of(), frame.visuals().get(47).fixed().lore());
        assertEquals("filler", frame.visuals().get(48).path());
        assertFalse(frame.bindings().containsKey(45));
        assertFalse(frame.bindings().containsKey(53));
    }

    @Test
    void pageEntryKeepsItsConfiguredAction() throws Exception {
        YamlConfiguration owner = resource("guis/player-menu.yml");
        owner.set("entries.next.slots", List.of(46));
        owner.set("entries.next.item.name", "Blå next");
        PublicGuiDefinition definition = PublicGuiSchema.parse(PlayerColorMenu.MENU_ID,
                "guis/player-menu.yml", owner, PlayerColorMenu.contract());
        PublicGuiFrame frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1, Map.of(PlayerColorMenu.MENU_ID, definition)),
                PlayerColorMenu.MENU_ID, new PublicGuiRenderInput(Map.of("page", "1", "pages", "2", "target_page", "2"),
                        Map.of("info", "empty"), Map.of(), Map.of(
                        "next", Map.of(46, new PublicGuiRenderInput.EntryValue("1", null, Map.of("target_page", "2"))))));

        assertEquals("colours.next", frame.bindings().get(46).actionFor(ClickKind.LEFT));
        assertEquals("Blå next", frame.visuals().get(46).fixed().name());
        assertFalse(frame.bindings().containsKey(45));
    }

    @Test
    void currentOwnerCanRemoveOptionalControlsWithoutGhostClicks() throws Exception {
        YamlConfiguration owner = resource("guis/player-menu.yml");
        owner.set("buttons.reset", null);
        owner.set("buttons.info", null);
        owner.set("entries.previous", null);
        owner.set("entries.next", null);
        PublicGuiDefinition definition = PublicGuiSchema.parse(PlayerColorMenu.MENU_ID,
                "guis/player-menu.yml", owner, PlayerColorMenu.contract());
        PublicGuiFrame frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1, Map.of(PlayerColorMenu.MENU_ID, definition)),
                PlayerColorMenu.MENU_ID, new PublicGuiRenderInput(Map.of(), Map.of(), Map.of(), Map.of()));

        for (int slot : List.of(45, 48, 50, 53)) {
            assertFalse(frame.bindings().containsKey(slot));
        }
    }

    @Test
    void upgradeKeepsExactOriginalBackupsAndInstallsNewFiles() throws Exception {
        Path gui = temporary.resolve("guis/player-menu.yml");
        Path config = temporary.resolve("config.yml");
        Files.createDirectories(gui.getParent());
        byte[] oldGui = "old GUI\n".getBytes(StandardCharsets.UTF_8);
        byte[] oldConfig = "old config\n".getBytes(StandardCharsets.UTF_8);
        Files.write(gui, oldGui);
        Files.write(config, oldConfig);
        Path stagedGui = Files.write(temporary.resolve("guis/staged-gui"), "new GUI\n".getBytes(StandardCharsets.UTF_8));
        Path stagedConfig = Files.write(temporary.resolve("staged-config"), "new config\n".getBytes(StandardCharsets.UTF_8));

        PlayerMenuGuiMigration.commitStaged(gui, config, stagedGui, stagedConfig, 2, false, () -> {});

        assertEquals("new GUI\n", Files.readString(gui));
        assertEquals("new config\n", Files.readString(config));
        assertArrayEquals(oldGui, Files.readAllBytes(gui.resolveSibling("player-menu.yml.gui-v2-backup")));
        assertArrayEquals(oldConfig, Files.readAllBytes(config.resolveSibling("config.yml.gui-v2-backup")));
    }

    @Test
    void releasedInstallCanUpgradeWithoutAnExistingGuisDirectory() throws Exception {
        Path gui = temporary.resolve("guis/player-menu.yml");
        Path config = temporary.resolve("config.yml");
        byte[] oldConfig = "gui: {player-menu: {title: 'Owner'}}\n".getBytes(StandardCharsets.UTF_8);
        Files.write(config, oldConfig);
        assertFalse(Files.exists(gui.getParent()));

        PlayerMenuGuiMigration.stageAndCommit(gui, config,
                resource("guis/player-menu.yml").saveToString().getBytes(StandardCharsets.UTF_8),
                "public-gui-schema-version: 3\n".getBytes(StandardCharsets.UTF_8), 1, true);

        assertTrue(Files.isRegularFile(gui));
        assertEquals(3, YamlConfiguration.loadConfiguration(config.toFile()).getInt("public-gui-schema-version"));
        assertArrayEquals(oldConfig, Files.readAllBytes(config.resolveSibling("config.yml.gui-v1-backup")));
        assertFalse(Files.exists(gui.resolveSibling("player-menu.yml.gui-v1-backup")));
    }

    @Test
    void interruptedUpgradeCanFinishConfigAfterGuiWasAlreadyReplaced() throws Exception {
        Path config = temporary.resolve("config.yml");
        byte[] oldConfig = "gui: {shared: {filler-material: BLACK_STAINED_GLASS_PANE}, player-menu: {title: Old}}\n"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(config, oldConfig);
        YamlConfiguration parsed = YamlConfiguration.loadConfiguration(config.toFile());

        PlayerMenuGuiMigration.finishInterruptedConfigUpgrade(config, parsed);

        YamlConfiguration result = YamlConfiguration.loadConfiguration(config.toFile());
        assertEquals(3, result.getInt("public-gui-schema-version"));
        assertTrue(result.getBoolean(PlayerMenuGuiMigration.PENDING_PATH));
        assertEquals("BLACK_STAINED_GLASS_PANE", result.getString("gui.shared.filler-material"));
        assertFalse(result.contains("gui.player-menu"));
        assertArrayEquals(oldConfig, Files.readAllBytes(config.resolveSibling("config.yml.gui-v1-backup")));
    }

    @Test
    void failedConfigCommitRestoresPreAttemptBytesAndRetainsBackup() throws Exception {
        Path gui = temporary.resolve("guis/player-menu.yml");
        Path config = temporary.resolve("config.yml");
        Files.createDirectories(gui.getParent());
        byte[] oldGui = "owner GUI\n".getBytes(StandardCharsets.UTF_8);
        byte[] oldConfig = "owner config\n".getBytes(StandardCharsets.UTF_8);
        Files.write(gui, oldGui);
        Files.write(config, oldConfig);
        Path stagedGui = Files.write(temporary.resolve("guis/staged-gui"), "new GUI\n".getBytes(StandardCharsets.UTF_8));
        Path stagedConfig = Files.write(temporary.resolve("staged-config"), "new config\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> PlayerMenuGuiMigration.commitStaged(
                gui, config, stagedGui, stagedConfig, 2, false,
                () -> { throw new IllegalStateException("simulated config failure"); }));

        assertArrayEquals(oldGui, Files.readAllBytes(gui));
        assertArrayEquals(oldConfig, Files.readAllBytes(config));
        assertArrayEquals(oldGui, Files.readAllBytes(gui.resolveSibling("player-menu.yml.gui-v2-backup")));
    }

    private YamlConfiguration resource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "Missing test resource " + path);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}

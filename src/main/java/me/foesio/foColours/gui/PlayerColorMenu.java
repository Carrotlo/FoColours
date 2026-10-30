package me.foesio.foColours.gui;

import me.foesio.core.gui.publicgui.GuiLoadReport;
import me.foesio.core.gui.publicgui.PublicGuiClick;
import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiItemRenderer;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiService;
import me.foesio.core.gui.publicgui.PublicGuiText;
import me.foesio.foColours.FoColours;
import me.foesio.foColours.color.ChatColorDefinition;
import me.foesio.foColours.service.ChatColorService;
import me.foesio.foColours.api.ColourChangeCause;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The single player inventory screen, rendered and dispatched by Core. */
public final class PlayerColorMenu implements AutoCloseable {

    public static final String MENU_ID = "colours.player";
    private final FoColours plugin;
    private final ChatColorService service;
    private final PublicGuiService gui;
    private final Map<UUID, Integer> openPages = new HashMap<>();
    private boolean migrationBlocked;

    public PlayerColorMenu(FoColours plugin) {
        this.plugin = plugin;
        this.service = plugin.getChatColorService();
        Path installed = plugin.getDataFolder().toPath().resolve("guis/player-menu.yml");
        this.gui = new PublicGuiService(plugin)
                .menu(MENU_ID, installed, contract())
                .entryPoint("command.focolours", MENU_ID)
                .action("colours.select", this::select)
                .action("colours.reset", this::reset)
                .action("colours.info", click -> { /* Informational item; intentional silence. */ })
                .action("colours.previous", click -> changePage(click, -1))
                .action("colours.next", click -> changePage(click, 1))
                .requireState(MENU_ID, "colour-unlocked")
                .requireState(MENU_ID, "colour-selected")
                .requireState(MENU_ID, "colour-locked")
                .requireState(MENU_ID, "status-unlocked")
                .requireState(MENU_ID, "status-locked")
                .requireEntry(MENU_ID, "colours", 0);
        gui.start();
        gui.reload();
    }

    public static PublicGuiContract contract() {
        return new PublicGuiContract(
                java.util.Set.of("colours.select", "colours.reset", "colours.info", "colours.previous", "colours.next"),
                Map.of(),
                java.util.Set.of("colour", "preview", "status", "current_colour", "default_colour", "available",
                        "page", "pages", "target_page"),
                PublicGuiContract.InteractionMode.CORE);
    }

    public GuiLoadReport reload() {
        return gui.reload();
    }

    public GuiLoadReport validateInstalled() {
        return gui.validateInstalled();
    }

    public void blockForFailedMigration() {
        migrationBlocked = true;
    }

    public void completeMigration() {
        migrationBlocked = false;
    }

    public boolean isAvailable() {
        return !migrationBlocked && gui.snapshot() != null;
    }

    public void open(Player player) {
        open(player, 0, true);
    }

    private void open(Player player, int requestedPage, boolean playSound) {
        if (!isAvailable()) {
            plugin.getConfigManager().sendMessage(player, "messages.gui-unavailable");
            return;
        }
        PublicGuiDefinition definition = gui.snapshot().require(MENU_ID);
        List<Integer> slots = definition.entries().get("colours").slots();
        List<ChatColorDefinition> colours = visibleColours(player);
        int pageSize = slots.size();
        int maxPage = colours.isEmpty() ? 0 : (colours.size() - 1) / pageSize;
        int page = Math.min(Math.max(requestedPage, 0), maxPage);
        Map<String, String> placeholders = new HashMap<>(plugin.getMessages().tokenValues());
        placeholders.putAll(service.commandContext(player));
        placeholders.put("page", plugin.getConfigManager().formatNumber(page + 1));
        placeholders.put("pages", plugin.getConfigManager().formatNumber(maxPage + 1));

        Map<Integer, PublicGuiRenderInput.EntryValue> colourEntries = new HashMap<>();
        for (int index = 0; index < slots.size() && page * pageSize + index < colours.size(); index++) {
            ChatColorDefinition colour = colours.get(page * pageSize + index);
            boolean unlocked = service.hasUnlocked(player, colour.getKey());
            boolean selected = unlocked && service.isSelected(player, colour.getKey());
            String state = !unlocked ? "colour-locked" : selected ? "colour-selected" : "colour-unlocked";
            Map<String, String> values = new HashMap<>(plugin.getMessages().tokenValues());
            values.put("colour", colour.getDisplayName());
            values.put("preview", colour.getPreviewLabel(plugin.getConfigManager()));
            values.put("status", plugin.getMessages().renderTemplateForViewer(player,
                    java.util.Objects.toString(definition.states()
                            .get(unlocked ? "status-unlocked" : "status-locked").name(), ""), Map.of()));
            PublicGuiDefinition.FixedItem appearance = appearanceForColour(
                    definition.states().get(state), colour, unlocked);
            ItemStack source = PublicGuiItemRenderer.fixed(player, appearance,
                    values, null, null, "states." + state);
            colourEntries.put(slots.get(index), new PublicGuiRenderInput.EntryValue(
                    colour.getKey(), source, Map.of()));
        }

        Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries = new HashMap<>();
        entries.put("colours", colourEntries);
        if (page > 0 && definition.entries().containsKey("previous")) {
            int slot = definition.entries().get("previous").slots().get(0);
            entries.put("previous", Map.of(slot, new PublicGuiRenderInput.EntryValue(
                    Integer.toString(page - 1), null, Map.of("target_page", plugin.getConfigManager().formatNumber(page)))));
        }
        if (page < maxPage && definition.entries().containsKey("next")) {
            int slot = definition.entries().get("next").slots().get(0);
            entries.put("next", Map.of(slot, new PublicGuiRenderInput.EntryValue(
                    Integer.toString(page + 1), null, Map.of("target_page", plugin.getConfigManager().formatNumber(page + 2)))));
        }
        List<String> unlockedNames = service.getUnlockedColours(player).stream()
                .map(ChatColorDefinition::getDisplayName).toList();
        boolean infoEnabled = definition.buttons().containsKey("info")
                && definition.buttons().get("info").enabled();
        Map<String, String> states = infoEnabled && unlockedNames.isEmpty() ? Map.of("info", "empty") : Map.of();
        Map<String, PublicGuiText.CycleSelection> cycles = !infoEnabled || unlockedNames.isEmpty() ? Map.of()
                : Map.of("info", new PublicGuiText.CycleSelection(unlockedNames, -1));
        gui.openEntryPoint(player, "command.focolours",
                new PublicGuiRenderInput(placeholders, states, cycles, entries));
        openPages.put(player.getUniqueId(), page);
        if (playSound) {
            plugin.getSounds().play(player, "player.open");
        }
    }

    private List<ChatColorDefinition> visibleColours(Player player) {
        return plugin.getColorRegistry().getEnabledColours().stream()
                .filter(colour -> plugin.getConfigManager().isShowLockedColours()
                        || service.hasUnlocked(player, colour.getKey())).toList();
    }

    static PublicGuiDefinition.FixedItem appearanceForColour(PublicGuiDefinition.FixedItem configured,
                                                              ChatColorDefinition colour, boolean unlocked) {
        if (!unlocked || !colour.hasConfiguredMaterial()) {
            return configured;
        }
        return new PublicGuiDefinition.FixedItem(colour.getMaterial(), configured.name(),
                configured.lore(), configured.amount(), configured.customModelData(),
                configured.glow(), configured.itemFlags());
    }

    private void changePage(PublicGuiClick click, int direction) {
        int page = openPages.getOrDefault(click.player().getUniqueId(), 0);
        plugin.getSounds().play(click.player(), direction < 0 ? "player.page-previous" : "player.page-next");
        open(click.player(), page + direction, false);
    }

    private void reset(PublicGuiClick click) {
        Player player = click.player();
        if (!plugin.getConfigManager().isResetEnabled()) {
            plugin.getSounds().play(player, "player.error");
            plugin.getConfigManager().sendMessage(player, "messages.reset-disabled");
            return;
        }
        if (!service.hasEnabledColours()) {
            plugin.getSounds().play(player, "player.error");
            plugin.getConfigManager().sendMessage(player, "messages.gui-unavailable");
            return;
        }
        if (service.isDefaultSelected(player)) {
            plugin.getSounds().play(player, "player.error");
            plugin.getConfigManager().sendMessage(player, "messages.already-default", service.commandContext(player));
            return;
        }
        service.resetColour(player, ColourChangeCause.PLAYER_MENU);
        plugin.getSounds().play(player, "player.reset");
        plugin.getConfigManager().sendMessage(player, "messages.reset-success", service.commandContext(player));
        open(player, openPages.getOrDefault(player.getUniqueId(), 0), false);
    }

    private void select(PublicGuiClick click) {
        Player player = click.player();
        ChatColorDefinition colour = plugin.getColorRegistry().getEnabledColour(click.entryId()).orElse(null);
        if (colour == null || !visibleColours(player).contains(colour)) {
            return;
        }
        if (!service.hasUnlocked(player, colour.getKey())) {
            plugin.getSounds().play(player, "player.error");
            plugin.getConfigManager().sendMessage(player, "messages.not-unlocked",
                    service.commandContext(player, "colour", colour.getDisplayName()));
            return;
        }
        if (service.isSelected(player, colour.getKey())) {
            plugin.getSounds().play(player, "player.error");
            plugin.getConfigManager().sendMessage(player, "messages.already-selected",
                    service.commandContext(player, "colour", colour.getDisplayName()));
            return;
        }
        if (!service.selectColour(player, colour.getKey(), ColourChangeCause.PLAYER_MENU)) {
            plugin.getSounds().play(player, "player.error");
            plugin.getConfigManager().sendMessage(player, "messages.not-unlocked",
                    service.commandContext(player, "colour", colour.getDisplayName()));
            return;
        }
        plugin.getSounds().play(player, "player.select");
        plugin.getConfigManager().sendMessage(player, "messages.select-success",
                service.commandContext(player, "colour", colour.getDisplayName()));
        open(player, openPages.getOrDefault(player.getUniqueId(), 0), false);
    }

    @Override
    public void close() {
        gui.close();
        openPages.clear();
    }
}

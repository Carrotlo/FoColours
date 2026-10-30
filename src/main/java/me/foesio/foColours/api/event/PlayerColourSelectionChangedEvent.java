package me.foesio.foColours.api.event;

import me.foesio.foColours.api.ColourChangeCause;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after a stored selection change is saved. Startup recovery and no-ops are silent. */
public final class PlayerColourSelectionChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String oldKey;
    private final String newKey;
    private final ColourChangeCause cause;

    public PlayerColourSelectionChangedEvent(UUID playerId, String oldKey, String newKey, ColourChangeCause cause) {
        super(!Bukkit.isPrimaryThread());
        this.playerId = playerId;
        this.oldKey = oldKey;
        this.newKey = newKey;
        this.cause = cause;
    }

    public UUID getPlayerId() { return playerId; }
    public String getOldKey() { return oldKey; }
    public String getNewKey() { return newKey; }
    public ColourChangeCause getCause() { return cause; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

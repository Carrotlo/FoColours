package me.foesio.foColours.api.event;

import me.foesio.foColours.api.ColourChangeCause;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after a stored unlock or revocation is saved; live permission changes alone are silent. */
public final class PlayerColourAccessChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String colourKey;
    private final boolean unlocked;
    private final ColourChangeCause cause;

    public PlayerColourAccessChangedEvent(UUID playerId, String colourKey, boolean unlocked, ColourChangeCause cause) {
        super(!Bukkit.isPrimaryThread());
        this.playerId = playerId;
        this.colourKey = colourKey;
        this.unlocked = unlocked;
        this.cause = cause;
    }

    public UUID getPlayerId() { return playerId; }
    public String getColourKey() { return colourKey; }
    public boolean isUnlocked() { return unlocked; }
    public ColourChangeCause getCause() { return cause; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

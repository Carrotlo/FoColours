package me.foesio.foColours.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * Supported FoColours integration service, obtained from Bukkit's ServicesManager.
 * Call on the target player's owning server context; never from an asynchronous chat handler.
 * Calls are silent and do not check the calling plugin's permissions.
 */
public interface FoColoursApi {
    List<ColourSnapshot> enabledColours();

    Optional<ColourSnapshot> findEnabledColour(String keyOrAlias);

    /** Returns an online player's stored choice, effective colour, and currently usable keys. */
    PlayerColourSnapshot playerColours(Player player);

    ColourChangeResult grantColour(OfflinePlayer player, String keyOrAlias);

    ColourChangeResult takeColour(OfflinePlayer player, String keyOrAlias);

    ColourChangeResult selectColour(Player player, String keyOrAlias);

    ColourChangeResult resetColour(Player player);

    /** Applies the effective colour regardless of chat.mode, stripping existing colour codes first. */
    String renderText(Player player, String text);
}

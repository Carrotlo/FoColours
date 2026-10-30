package me.foesio.foColours.api;

import java.util.List;

/** An immutable, currently enabled colour definition. Keys remain stable until an owner edits config. */
public record ColourSnapshot(String key, String displayName, ColourType type, String value, List<String> aliases) {
    public ColourSnapshot {
        aliases = List.copyOf(aliases);
    }
}

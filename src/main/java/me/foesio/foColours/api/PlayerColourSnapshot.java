package me.foesio.foColours.api;

import java.util.Set;

/** A point-in-time view for an online player. The effective key may differ from the stored choice. */
public record PlayerColourSnapshot(String storedSelectedKey, String effectiveKey, Set<String> unlockedKeys) {
    public PlayerColourSnapshot {
        unlockedKeys = Set.copyOf(unlockedKeys);
    }
}

package me.foesio.foColours.data;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PlayerColorData {

    private String lastKnownName;
    private String selectedColour;
    private boolean ezColorsConverted;
    private final Set<String> unlockedColours;
    private final Set<String> legacyPermissionGrants;
    private final Set<String> revokedColours;

    public PlayerColorData(String lastKnownName, String selectedColour, boolean ezColorsConverted, Collection<String> unlockedColours, Collection<String> legacyPermissionGrants, Collection<String> revokedColours) {
        this.lastKnownName = lastKnownName;
        this.selectedColour = selectedColour;
        this.ezColorsConverted = ezColorsConverted;
        this.unlockedColours = new LinkedHashSet<>(unlockedColours);
        this.legacyPermissionGrants = new LinkedHashSet<>(legacyPermissionGrants);
        this.revokedColours = new LinkedHashSet<>(revokedColours);
    }

    public synchronized String getLastKnownName() {
        return lastKnownName;
    }

    public synchronized void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public synchronized String getSelectedColour() {
        return selectedColour;
    }

    public synchronized void setSelectedColour(String selectedColour) {
        this.selectedColour = selectedColour;
    }

    public synchronized boolean isEzColorsConverted() {
        return ezColorsConverted;
    }

    public synchronized void setEzColorsConverted(boolean ezColorsConverted) {
        this.ezColorsConverted = ezColorsConverted;
    }

    public synchronized boolean hasUnlocked(String key) {
        return unlockedColours.contains(key) && !revokedColours.contains(key);
    }

    public synchronized boolean hasStoredUnlock(String key) {
        return unlockedColours.contains(key);
    }

    public synchronized boolean addUnlocked(String key) {
        boolean unrevoked = revokedColours.remove(key);
        boolean added = unlockedColours.add(key);
        return unrevoked || added;
    }

    public synchronized boolean removeUnlocked(String key) {
        return unlockedColours.remove(key);
    }

    public synchronized Set<String> snapshotUnlockedColours() {
        return new LinkedHashSet<>(unlockedColours);
    }

    public synchronized void ensureUnlocked(Collection<String> defaults) {
        unlockedColours.addAll(defaults);
    }

    public synchronized Set<String> snapshotLegacyPermissionGrants() {
        return new LinkedHashSet<>(legacyPermissionGrants);
    }

    public synchronized void setLegacyPermissionGrants(Collection<String> grants) {
        legacyPermissionGrants.clear();
        legacyPermissionGrants.addAll(grants);
    }

    public synchronized boolean hasLegacyPermissionGrant(String key) {
        return legacyPermissionGrants.contains(key);
    }

    public synchronized boolean removeLegacyPermissionGrant(String key) {
        return legacyPermissionGrants.remove(key);
    }

    public synchronized boolean isRevoked(String key) {
        return revokedColours.contains(key);
    }

    public synchronized boolean revokeColour(String key) {
        return revokedColours.add(key);
    }

    public synchronized Set<String> snapshotRevokedColours() {
        return new LinkedHashSet<>(revokedColours);
    }

    public synchronized PlayerColorData copy() {
        return new PlayerColorData(lastKnownName, selectedColour, ezColorsConverted,
                unlockedColours, legacyPermissionGrants, revokedColours);
    }
}

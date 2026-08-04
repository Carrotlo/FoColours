package me.foesio.foColours.data;

import me.foesio.core.config.ResourceFiles;
import me.foesio.foColours.FoColours;
import me.foesio.foColours.color.ColorRegistry;
import me.foesio.foColours.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PlayerDataManager {

    private final FoColours plugin;
    private final ConfigManager configManager;
    private final Map<UUID, PlayerColorData> cache = new ConcurrentHashMap<>();
    private File userDataFolder;
    private File databaseFile;
    private Connection connection;
    private boolean sqliteAvailable;

    public PlayerDataManager(FoColours plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void initialize() {
        this.userDataFolder = ResourceFiles.dataFile(plugin, "userdata");
        this.databaseFile = ResourceFiles.dataFile(plugin, "data.db");
        initializeSqlite();

        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player);
        }
    }

    public PlayerColorData loadPlayer(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), uuid -> load(uuid, player.getName()));
    }

    public Optional<PlayerColorData> getLoaded(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public PlayerColorData getOrLoad(KnownPlayer knownPlayer) {
        return cache.computeIfAbsent(knownPlayer.uuid(), uuid -> load(uuid, knownPlayer.name()));
    }

    public Set<String> getUnlockedColours(Player player) {
        return loadPlayer(player).snapshotUnlockedColours();
    }

    public void saveAndUnload(Player player) {
        PlayerColorData data = cache.remove(player.getUniqueId());
        if (data != null) {
            data.setLastKnownName(player.getName());
            save(player.getUniqueId(), data);
        }
    }

    public void flushAll() {
        for (Map.Entry<UUID, PlayerColorData> entry : cache.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
        closeConnection();
    }

    public Optional<KnownPlayer> resolveKnownPlayer(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return Optional.of(new KnownPlayer(online.getUniqueId(), online.getName()));
        }

        for (Map.Entry<UUID, PlayerColorData> entry : cache.entrySet()) {
            if (entry.getValue().getLastKnownName().equalsIgnoreCase(input)) {
                return Optional.of(new KnownPlayer(entry.getKey(), entry.getValue().getLastKnownName()));
            }
        }

        if (sqliteAvailable) {
            Optional<KnownPlayer> storedPlayer = findKnownPlayerInDatabase(input);
            if (storedPlayer.isPresent()) {
                return storedPlayer;
            }
        }

        try {
            UUID uuid = UUID.fromString(input);
            PlayerColorData cachedData = cache.get(uuid);
            if (cachedData != null) {
                return Optional.of(new KnownPlayer(uuid, cachedData.getLastKnownName()));
            }

            if (sqliteAvailable) {
                Optional<PlayerColorData> storedData = loadFromDatabase(uuid);
                if (storedData.isPresent()) {
                    return Optional.of(new KnownPlayer(uuid, storedData.get().getLastKnownName()));
                }
            }

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            if (offlinePlayer.getName() != null) {
                return Optional.of(new KnownPlayer(uuid, offlinePlayer.getName()));
            }
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    public List<String> getKnownPlayerNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }

        for (PlayerColorData data : cache.values()) {
            names.add(data.getLastKnownName());
        }

        if (sqliteAvailable) {
            names.addAll(getKnownPlayerNamesFromDatabase());
        }
        return new ArrayList<>(names);
    }

    public boolean grantColour(KnownPlayer knownPlayer, String key) {
        PlayerColorData data = getOrLoad(knownPlayer);
        boolean changed = data.addUnlocked(key);
        if (changed) {
            save(knownPlayer.uuid(), data);
        }
        return changed;
    }

    public boolean takeColour(KnownPlayer knownPlayer, String key, String fallbackKey, boolean hasLivePermissionAccess) {
        PlayerColorData data = getOrLoad(knownPlayer);
        boolean changed = data.removeUnlocked(key);
        changed |= data.removeLegacyPermissionGrant(key);
        if (changed || hasLivePermissionAccess) {
            changed |= data.revokeColour(key);
        }

        if (!changed) {
            return false;
        }

        if (key.equals(data.getSelectedColour())) {
            data.ensureUnlocked(List.of(fallbackKey));
            data.setSelectedColour(fallbackKey);
        }

        save(knownPlayer.uuid(), data);
        return true;
    }

    public boolean setSelectedColour(Player player, String key) {
        PlayerColorData data = loadPlayer(player);
        if (key.equals(data.getSelectedColour())) {
            return false;
        }

        data.setSelectedColour(key);
        save(player.getUniqueId(), data);
        return true;
    }

    public boolean grantColour(Player player, String key) {
        PlayerColorData data = loadPlayer(player);
        boolean changed = data.addUnlocked(key);
        if (changed) {
            save(player.getUniqueId(), data);
        }
        return changed;
    }

    public me.foesio.foColours.compat.EzColorsCompatService.SyncResult syncLegacyPermissionGrants(Player player, Collection<String> permissionGrants, boolean revokeMissing, String fallbackKey, boolean markConverted) {
        PlayerColorData data = loadPlayer(player);
        if (data.isEzColorsConverted()) {
            return new me.foesio.foColours.compat.EzColorsCompatService.SyncResult(0, 0, data.snapshotLegacyPermissionGrants().size(), true);
        }

        LinkedHashSet<String> normalizedGrants = new LinkedHashSet<>();
        for (String permissionGrant : permissionGrants) {
            normalizedGrants.add(configManager.normalize(permissionGrant));
        }

        int added = 0;
        int removed = 0;
        boolean changed = false;

        for (String grant : normalizedGrants) {
            if (!data.isRevoked(grant) && data.addUnlocked(grant)) {
                added++;
                changed = true;
            }
        }

        LinkedHashSet<String> storedLegacyGrants = new LinkedHashSet<>();
        for (String grant : normalizedGrants) {
            if (!data.isRevoked(grant)) {
                storedLegacyGrants.add(grant);
            }
        }

        Set<String> previousLegacyGrants = data.snapshotLegacyPermissionGrants();
        if (revokeMissing) {
            for (String previousGrant : previousLegacyGrants) {
                if (!storedLegacyGrants.contains(previousGrant) && !configManager.getDefaultUnlockedColours().contains(previousGrant)) {
                    if (data.removeUnlocked(previousGrant)) {
                        removed++;
                        changed = true;
                    }
                }
            }
        }

        if (!previousLegacyGrants.equals(storedLegacyGrants)) {
            data.setLegacyPermissionGrants(storedLegacyGrants);
            changed = true;
        }

        if (markConverted) {
            data.setEzColorsConverted(true);
            changed = true;
        }

        if (!data.hasUnlocked(data.getSelectedColour())) {
            data.ensureUnlocked(List.of(fallbackKey));
            data.setSelectedColour(fallbackKey);
            changed = true;
        }

        if (changed) {
            save(player.getUniqueId(), data);
        }

        return new me.foesio.foColours.compat.EzColorsCompatService.SyncResult(added, removed, normalizedGrants.size(), false);
    }

    public void normalizeOnlineSelections(ColorRegistry registry, String fallbackKey) {
        for (Map.Entry<UUID, PlayerColorData> entry : cache.entrySet()) {
            PlayerColorData data = entry.getValue();
            data.ensureUnlocked(configManager.getDefaultUnlockedColours());
            if (!registry.getEnabledColour(data.getSelectedColour()).isPresent() || !data.hasUnlocked(data.getSelectedColour())) {
                data.setSelectedColour(fallbackKey);
                data.ensureUnlocked(List.of(fallbackKey));
                save(entry.getKey(), data);
            }
        }
    }

    private PlayerColorData load(UUID uuid, String fallbackName) {
        if (sqliteAvailable) {
            Optional<PlayerColorData> storedData = loadFromDatabase(uuid);
            if (storedData.isPresent()) {
                PlayerColorData data = storedData.get();
                data.ensureUnlocked(configManager.getDefaultUnlockedColours());
                if (!data.hasUnlocked(data.getSelectedColour())) {
                    data.setSelectedColour(configManager.getDefaultColourKey());
                    save(uuid, data);
                }
                return data;
            }

            PlayerColorData data = createDefaultData(fallbackName);
            save(uuid, data);
            return data;
        }

        throw storageUnavailable();
    }

    private PlayerColorData createDefaultData(String fallbackName) {
        return new PlayerColorData(fallbackName, configManager.getDefaultColourKey(), false, configManager.getDefaultUnlockedColours(), List.of(), List.of());
    }

    private PlayerColorData loadYamlFile(File file, String fallbackName) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        String lastKnownName = configuration.getString("last-known-name", fallbackName);
        String selectedColour = configManager.normalize(configuration.getString("selected-colour", configManager.getDefaultColourKey()));
        boolean ezColorsConverted = configuration.getBoolean("ezcolors-converted", false);
        Set<String> unlockedColours = new LinkedHashSet<>(configuration.getStringList("unlocked-colours").stream().map(configManager::normalize).toList());
        Set<String> legacyPermissionGrants = new LinkedHashSet<>(configuration.getStringList("legacy-permission-grants").stream().map(configManager::normalize).toList());
        Set<String> revokedColours = new LinkedHashSet<>(configuration.getStringList("revoked-colours").stream().map(configManager::normalize).toList());
        return new PlayerColorData(lastKnownName, selectedColour, ezColorsConverted, unlockedColours, legacyPermissionGrants, revokedColours);
    }

    private void save(UUID uuid, PlayerColorData data) {
        if (sqliteAvailable) {
            saveToDatabase(uuid, data);
            return;
        }

        throw storageUnavailable();
    }

    private void initializeSqlite() {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            this.sqliteAvailable = true;
            configureDatabase();
            createTables();
            migrateYamlFilesToDatabase();
        } catch (ClassNotFoundException | SQLException exception) {
            this.sqliteAvailable = false;
            closeConnection();
            throw new IllegalStateException("SQLite unavailable. Player colour data cannot be loaded safely.", exception);
        }
    }

    private synchronized void configureDatabase() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    private synchronized void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_colours (
                        uuid TEXT PRIMARY KEY,
                        last_known_name TEXT NOT NULL,
                        selected_colour TEXT NOT NULL,
                        ezcolors_converted INTEGER NOT NULL,
                        unlocked_colours TEXT NOT NULL,
                        legacy_permission_grants TEXT NOT NULL,
                        revoked_colours TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_colours_name ON player_colours(last_known_name)");
        }
    }

    private void migrateYamlFilesToDatabase() {
        if (!sqliteAvailable) {
            return;
        }

        int migrated = 0;
        for (File file : listUserFiles()) {
            String uuidRaw = file.getName().replace(".yml", "");
            try {
                UUID uuid = UUID.fromString(uuidRaw);
                if (databaseHasPlayer(uuid)) {
                    continue;
                }

                PlayerColorData data = loadYamlFile(file, uuidRaw);
                data.ensureUnlocked(configManager.getDefaultUnlockedColours());
                if (!data.hasUnlocked(data.getSelectedColour())) {
                    data.setSelectedColour(configManager.getDefaultColourKey());
                }
                saveToDatabase(uuid, data);
                migrated++;
            } catch (IllegalArgumentException exception) {
                plugin.logWarning("Skipped invalid userdata file name: " + file.getName());
            }
        }

        if (migrated > 0) {
            plugin.logInfo("Migrated " + migrated + " userdata files to SQLite.");
        }
    }

    private synchronized boolean databaseHasPlayer(UUID uuid) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM player_colours WHERE uuid = ? LIMIT 1")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check SQLite player data for " + uuid, exception);
        }
    }

    private synchronized Optional<PlayerColorData> loadFromDatabase(UUID uuid) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT last_known_name, selected_colour, ezcolors_converted, unlocked_colours, legacy_permission_grants, revoked_colours
                FROM player_colours
                WHERE uuid = ?
                """)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new PlayerColorData(
                        resultSet.getString("last_known_name"),
                        configManager.normalize(resultSet.getString("selected_colour")),
                        resultSet.getInt("ezcolors_converted") != 0,
                        decodeList(resultSet.getString("unlocked_colours")),
                        decodeList(resultSet.getString("legacy_permission_grants")),
                        decodeList(resultSet.getString("revoked_colours"))
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load SQLite player data for " + uuid, exception);
        }
    }

    private synchronized void saveToDatabase(UUID uuid, PlayerColorData data) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_colours (uuid, last_known_name, selected_colour, ezcolors_converted, unlocked_colours, legacy_permission_grants, revoked_colours)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    last_known_name = excluded.last_known_name,
                    selected_colour = excluded.selected_colour,
                    ezcolors_converted = excluded.ezcolors_converted,
                    unlocked_colours = excluded.unlocked_colours,
                    legacy_permission_grants = excluded.legacy_permission_grants,
                    revoked_colours = excluded.revoked_colours
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, data.getLastKnownName());
            statement.setString(3, data.getSelectedColour());
            statement.setInt(4, data.isEzColorsConverted() ? 1 : 0);
            statement.setString(5, encodeList(data.snapshotUnlockedColours()));
            statement.setString(6, encodeList(data.snapshotLegacyPermissionGrants()));
            statement.setString(7, encodeList(data.snapshotRevokedColours()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save SQLite player data for " + uuid, exception);
        }
    }

    private synchronized Optional<KnownPlayer> findKnownPlayerInDatabase(String input) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT uuid, last_known_name
                FROM player_colours
                WHERE lower(last_known_name) = lower(?)
                LIMIT 1
                """)) {
            statement.setString(1, input);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new KnownPlayer(UUID.fromString(resultSet.getString("uuid")), resultSet.getString("last_known_name")));
            }
        } catch (IllegalArgumentException exception) {
            plugin.logWarning("Failed to resolve SQLite player '" + input + "': " + exception.getMessage());
            return Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to resolve SQLite player '" + input + "'", exception);
        }
    }

    private synchronized List<String> getKnownPlayerNamesFromDatabase() {
        List<String> names = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT last_known_name FROM player_colours ORDER BY lower(last_known_name)");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String name = resultSet.getString("last_known_name");
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list SQLite players", exception);
        }
        return names;
    }

    private String encodeList(Collection<String> values) {
        return String.join(",", values);
    }

    private Set<String> decodeList(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>();
        }

        return Arrays.stream(raw.split(","))
                .map(configManager::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private synchronized void closeConnection() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.logWarning("Failed to close SQLite database: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }

    private Collection<File> listUserFiles() {
        if (userDataFolder == null || !userDataFolder.isDirectory()) {
            return List.of();
        }
        File[] files = userDataFolder.listFiles((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        return files == null ? List.of() : List.of(files);
    }

    private IllegalStateException storageUnavailable() {
        return new IllegalStateException("SQLite is unavailable. Player colour data cannot be loaded or saved safely.");
    }
}

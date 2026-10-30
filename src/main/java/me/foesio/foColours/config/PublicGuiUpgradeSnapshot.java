package me.foesio.foColours.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pre-attempt bytes for the files touched while a public GUI upgrade starts. */
public final class PublicGuiUpgradeSnapshot {

    private final Map<Path, byte[]> originals;

    private PublicGuiUpgradeSnapshot(Map<Path, byte[]> originals) {
        this.originals = originals;
    }

    public static PublicGuiUpgradeSnapshot capture(Path dataFolder) throws IOException {
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        for (String filename : new String[] {
                "config.yml", "guis/player-menu.yml", "messages.yml", "sounds.yml",
                ConfigManager.PUBLIC_GUI_INSTALL_MARKER}) {
            Path file = dataFolder.resolve(filename);
            if (Files.isSymbolicLink(file)) {
                continue;
            }
            if (Files.exists(file) && !Files.isRegularFile(file)) {
                continue;
            }
            originals.put(file, Files.isRegularFile(file) ? Files.readAllBytes(file) : null);
        }
        return new PublicGuiUpgradeSnapshot(originals);
    }

    /** Restore the active files; the labeled upgrade backups remain available. */
    public boolean rollback() throws IOException {
        IOException failure = null;
        boolean restored = false;
        for (Map.Entry<Path, byte[]> entry : originals.entrySet()) {
            try {
                Path file = entry.getKey();
                byte[] original = entry.getValue();
                if (original == null) {
                    restored |= Files.deleteIfExists(file);
                } else if (!Files.isRegularFile(file)
                        || !Arrays.equals(original, Files.readAllBytes(file))) {
                    PlayerMenuGuiMigration.atomicWrite(file, original);
                    restored = true;
                }
            } catch (IOException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        return restored;
    }

}

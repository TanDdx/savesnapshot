package com.savesnapshot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultsWhenFileMissing() {
        SnapshotConfig c = SnapshotConfig.load(tempDir.resolve("nope.json"));
        assertTrue(c.autoEnabled);
        assertEquals(10, c.autoIntervalMinutes);
        assertEquals(10, c.autoKeep);
    }

    @Test
    void roundTrip() throws Exception {
        Path p = tempDir.resolve("cfg.json");
        SnapshotConfig c = new SnapshotConfig();
        c.autoEnabled = false;
        c.autoIntervalMinutes = 30;
        c.autoKeep = 3;
        c.save(p);
        SnapshotConfig loaded = SnapshotConfig.load(p);
        assertFalse(loaded.autoEnabled);
        assertEquals(30, loaded.autoIntervalMinutes);
        assertEquals(3, loaded.autoKeep);
    }

    @Test
    void clampsInvalidValues() throws Exception {
        Path p = tempDir.resolve("cfg.json");
        Files.writeString(p, "{\"autoIntervalMinutes\": 0, \"autoKeep\": -5}");
        SnapshotConfig c = SnapshotConfig.load(p);
        assertEquals(1, c.autoIntervalMinutes);
        assertEquals(1, c.autoKeep);
    }

    @Test
    void corruptFileFallsBackToDefaults() throws Exception {
        Path p = tempDir.resolve("cfg.json");
        Files.writeString(p, "not json {{{");
        SnapshotConfig c = SnapshotConfig.load(p);
        assertTrue(c.autoEnabled);
        assertEquals(10, c.autoIntervalMinutes);
    }
}

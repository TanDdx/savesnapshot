package com.savesnapshot.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotMetaTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTrip() throws Exception {
        Path dir = tempDir.resolve("snap1");
        SnapshotMeta meta = new SnapshotMeta("snap1", 1725000000000L, false, "26.2", 153);
        meta.save(dir);
        SnapshotMeta loaded = SnapshotMeta.load(dir);
        assertNotNull(loaded);
        assertEquals("snap1", loaded.name());
        assertEquals(1725000000000L, loaded.createdAtMillis());
        assertFalse(loaded.automatic());
        assertEquals("26.2", loaded.gameVersion());
        assertEquals(153, loaded.chunkCount());
    }

    @Test
    void loadReturnsNullWhenMissing() {
        assertNull(SnapshotMeta.load(tempDir.resolve("nothing")));
    }

    @Test
    void loadReturnsNullWhenCorrupt() throws Exception {
        Path dir = tempDir.resolve("bad");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("meta.json"), "{{{{");
        assertNull(SnapshotMeta.load(dir));
    }
}

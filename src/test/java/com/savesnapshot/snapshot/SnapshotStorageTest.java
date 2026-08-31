package com.savesnapshot.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotStorageTest {
    @TempDir
    Path worldDir;

    private SnapshotStorage storage;

    private void initStorage() {
        storage = new SnapshotStorage(worldDir);
    }

    @Test
    void listEmptyWhenNoSnapshots() throws Exception {
        initStorage();
        assertTrue(storage.list().isEmpty());
    }

    @Test
    void listSortedNewestFirst() throws Exception {
        initStorage();
        new SnapshotMeta("old", 1000, false, "26.2", 1).save(storage.dir("old"));
        new SnapshotMeta("new", 2000, false, "26.2", 1).save(storage.dir("new"));
        List<SnapshotMeta> list = storage.list();
        assertEquals(List.of("new", "old"), list.stream().map(SnapshotMeta::name).toList());
    }

    @Test
    void existsAndDelete() throws Exception {
        initStorage();
        Path dir = storage.dir("x");
        new SnapshotMeta("x", 1, false, "26.2", 1).save(dir);
        Files.writeString(dir.resolve("dummy.nbt"), "data");
        assertTrue(storage.exists("x"));
        storage.delete("x");
        assertFalse(storage.exists("x"));
    }

    @Test
    void renameMovesDirAndUpdatesMeta() throws Exception {
        initStorage();
        new SnapshotMeta("before", 42, false, "26.2", 1).save(storage.dir("before"));
        storage.rename("before", "after");
        assertFalse(storage.exists("before"));
        assertTrue(storage.exists("after"));
        assertEquals("after", SnapshotMeta.load(storage.dir("after")).name());
        assertEquals(42, SnapshotMeta.load(storage.dir("after")).createdAtMillis());
    }

    @Test
    void autoOldestFirstFiltersAndSorts() throws Exception {
        initStorage();
        new SnapshotMeta("manual1", 500, false, "26.2", 1).save(storage.dir("manual1"));
        new SnapshotMeta("auto-b", 300, true, "26.2", 1).save(storage.dir("auto-b"));
        new SnapshotMeta("auto-a", 100, true, "26.2", 1).save(storage.dir("auto-a"));
        List<SnapshotMeta> autos = storage.autoOldestFirst();
        assertEquals(List.of("auto-a", "auto-b"), autos.stream().map(SnapshotMeta::name).toList());
    }

    @Test
    void corruptMetaSkippedInList() throws Exception {
        initStorage();
        Path bad = storage.dir("bad");
        Files.createDirectories(bad);
        Files.writeString(bad.resolve("meta.json"), "not json");
        new SnapshotMeta("good", 1, false, "26.2", 1).save(storage.dir("good"));
        assertEquals(List.of("good"), storage.list().stream().map(SnapshotMeta::name).toList());
    }
}

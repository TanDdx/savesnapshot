package com.savesnapshot.snapshot;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RotationPolicyTest {
    private static SnapshotMeta auto(String name, long t) {
        return new SnapshotMeta(name, t, true, "26.2", 10);
    }

    @Test
    void nothingToDeleteWhenUnderLimit() {
        List<SnapshotMeta> autos = List.of(auto("a", 1), auto("b", 2));
        assertTrue(RotationPolicy.namesToDelete(autos, 10).isEmpty());
        assertTrue(RotationPolicy.namesToDelete(autos, 2).isEmpty());
    }

    @Test
    void deletesOldestFirst() {
        List<SnapshotMeta> autos = List.of(auto("a", 1), auto("b", 2), auto("c", 3));
        assertEquals(List.of("a"), RotationPolicy.namesToDelete(autos, 2));
        assertEquals(List.of("a", "b"), RotationPolicy.namesToDelete(autos, 1));
    }
}

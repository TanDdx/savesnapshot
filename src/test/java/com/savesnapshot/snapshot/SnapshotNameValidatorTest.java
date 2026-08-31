package com.savesnapshot.snapshot;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotNameValidatorTest {
    @Test
    void validNamePasses() {
        assertNull(SnapshotNameValidator.validate("我的基地", Set.of()));
        assertNull(SnapshotNameValidator.validate("before-nether-01", Set.of("other")));
    }

    @Test
    void blankNameRejected() {
        assertNotNull(SnapshotNameValidator.validate("", Set.of()));
        assertNotNull(SnapshotNameValidator.validate("   ", Set.of()));
        assertNotNull(SnapshotNameValidator.validate(null, Set.of()));
    }

    @Test
    void illegalCharsRejected() {
        for (String bad : new String[]{"a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b"}) {
            assertNotNull(SnapshotNameValidator.validate(bad, Set.of()), "应拒绝: " + bad);
        }
    }

    @Test
    void duplicateRejected() {
        assertNotNull(SnapshotNameValidator.validate("base", Set.of("base")));
    }

    @Test
    void windowsReservedRejected() {
        assertNotNull(SnapshotNameValidator.validate("CON", Set.of()));
        assertNotNull(SnapshotNameValidator.validate("com1", Set.of()));
    }

    @Test
    void dotAndSpaceEdgeRejected() {
        assertNotNull(SnapshotNameValidator.validate(".hidden", Set.of()));
        assertNotNull(SnapshotNameValidator.validate("name.", Set.of()));
        assertNotNull(SnapshotNameValidator.validate("name ", Set.of()));
    }

    @Test
    void tooLongRejected() {
        assertNotNull(SnapshotNameValidator.validate("a".repeat(65), Set.of()));
    }
}

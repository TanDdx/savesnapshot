package com.savesnapshot.snapshot;

import java.util.List;

/** 纯逻辑：自动快照轮转。输入按创建时间升序（最旧在前）的自动快照列表。 */
public final class RotationPolicy {
    private RotationPolicy() {
    }

    /** @return 需要删除的快照名（最旧的在前）。 */
    public static List<String> namesToDelete(List<SnapshotMeta> autoSnapshotsOldestFirst, int keep) {
        int excess = autoSnapshotsOldestFirst.size() - keep;
        if (excess <= 0) {
            return List.of();
        }
        return autoSnapshotsOldestFirst.subList(0, excess).stream()
                .map(SnapshotMeta::name)
                .toList();
    }
}

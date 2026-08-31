package com.savesnapshot.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** 快照目录 CRUD。世界存档下 <world>/snapshots/<名称>/。纯文件 IO + SnapshotMeta，可单测。 */
public final class SnapshotStorage {
    private final Path worldDir;

    public SnapshotStorage(Path worldDir) {
        this.worldDir = worldDir;
    }

    public Path snapshotsDir() {
        return worldDir.resolve("snapshots");
    }

    public Path dir(String name) {
        return snapshotsDir().resolve(name);
    }

    public boolean exists(String name) {
        return Files.isDirectory(dir(name));
    }

    /** 按创建时间倒序（最新在前）；meta 损坏的目录跳过。 */
    public List<SnapshotMeta> list() throws IOException {
        Path root = snapshotsDir();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<SnapshotMeta> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            for (Path p : stream.filter(Files::isDirectory).toList()) {
                SnapshotMeta meta = SnapshotMeta.load(p);
                if (meta != null) {
                    result.add(meta);
                }
            }
        }
        result.sort(Comparator.comparingLong(SnapshotMeta::createdAtMillis).reversed());
        return result;
    }

    /** 自动快照，按创建时间升序（最旧在前），供 RotationPolicy 使用。 */
    public List<SnapshotMeta> autoOldestFirst() throws IOException {
        return list().stream()
                .filter(SnapshotMeta::automatic)
                .sorted(Comparator.comparingLong(SnapshotMeta::createdAtMillis))
                .toList();
    }

    public void delete(String name) throws IOException {
        deleteRecursively(dir(name));
    }

    public void rename(String oldName, String newName) throws IOException {
        Files.move(dir(oldName), dir(newName));
        SnapshotMeta meta = SnapshotMeta.load(dir(newName));
        if (meta != null) {
            new SnapshotMeta(newName, meta.createdAtMillis(), meta.automatic(), meta.gameVersion(), meta.chunkCount())
                    .save(dir(newName));
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}

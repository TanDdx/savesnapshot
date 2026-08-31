package com.savesnapshot.snapshot;

import com.savesnapshot.SaveSnapshotMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.DimensionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

public final class SnapshotRestorer {
    private static final List<String> LEVEL_DATA_KEYS = List.of(
            "DayTime", "GameTime", "raining", "thundering", "rainTime", "thunderTime", "clearWeatherTime");

    private SnapshotRestorer() {}

    public static void restoreFiles(Path worldDir, String name) throws IOException {
        SnapshotStorage storage = new SnapshotStorage(worldDir);
        if (!storage.exists(name)) {
            throw new IOException("Snapshot does not exist: " + name);
        }

        Path snapshotDir = storage.dir(name);
        Path chunksRoot = snapshotDir.resolve("chunks");
        if (Files.isDirectory(chunksRoot)) {
            restoreChunks(worldDir, chunksRoot);
        }

        Path snapPlayerDir = snapshotDir.resolve("playerdata");
        if (Files.isDirectory(snapPlayerDir)) {
            restorePlayers(worldDir, snapPlayerDir);
        }

        Path snapshotLevelData = snapshotDir.resolve("leveldata.dat");
        if (Files.isRegularFile(snapshotLevelData)) {
            restoreLevelData(worldDir, snapshotLevelData);
        }

        SaveSnapshotMod.LOGGER.info("Restored snapshot {} into {}", name, worldDir);
    }

    private static void restoreChunks(Path worldDir, Path chunksRoot) throws IOException {
        String levelId = worldDir.getFileName().toString();

        try (Stream<Path> dimStream = Files.list(chunksRoot)) {
            for (Path dimDir : dimStream.filter(Files::isDirectory).toList()) {
                String dimName = dimDir.getFileName().toString();
                Identifier dimId = parseDimensionId(dimName);
                if (dimId == null) {
                    SaveSnapshotMod.LOGGER.warn("Skipping unrecognized dimension directory: {}", dimName);
                    continue;
                }

                ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimId);
                Path dimWorldDir = DimensionType.getStorageFolder(levelKey, worldDir);
                Path regionDir = dimWorldDir.resolve("region");
                Files.createDirectories(regionDir);

                RegionStorageInfo info = new RegionStorageInfo(levelId, levelKey, "chunk");
                try (RegionFileStorage regionStorage = new RegionFileStorage(info, regionDir, true)) {
                    try (Stream<Path> chunkStream = Files.list(dimDir)) {
                        for (Path chunkFile : chunkStream.filter(Files::isRegularFile).toList()) {
                            ChunkPos pos = parseChunkFileName(chunkFile.getFileName().toString());
                            if (pos == null) {
                                SaveSnapshotMod.LOGGER.warn("Skipping unrecognized chunk file: {}", chunkFile);
                                continue;
                            }

                            CompoundTag tag = NbtIo.readCompressed(chunkFile, NbtAccounter.unlimitedHeap());
                            regionStorage.write(pos, tag);
                        }
                    }
                }
            }
        }
    }

    private static Identifier parseDimensionId(String dirName) {
        int firstUnderscore = dirName.indexOf('_');
        if (firstUnderscore < 0) {
            return null;
        }
        String namespace = dirName.substring(0, firstUnderscore);
        String path = dirName.substring(firstUnderscore + 1);
        return Identifier.tryParse(namespace + ":" + path);
    }

    private static ChunkPos parseChunkFileName(String fileName) {
        if (!fileName.endsWith(".nbt")) {
            return null;
        }
        String base = fileName.substring(0, fileName.length() - 4);
        if (!base.startsWith("c.")) {
            return null;
        }
        String[] parts = base.substring(2).split("\\.");
        if (parts.length != 2) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int z = Integer.parseInt(parts[1]);
            return new ChunkPos(x, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void restorePlayers(Path worldDir, Path snapPlayerDir) throws IOException {
        Path targetDir = worldDir.resolve("playerdata");
        Files.createDirectories(targetDir);

        try (Stream<Path> stream = Files.list(snapPlayerDir)) {
            for (Path source : stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".dat")).toList()) {
                Path target = targetDir.resolve(source.getFileName().toString());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void restoreLevelData(Path worldDir, Path snapshotLevelData) throws IOException {
        CompoundTag snapshotTag = NbtIo.readCompressed(snapshotLevelData, NbtAccounter.unlimitedHeap());

        Path levelDat = worldDir.resolve("level.dat");
        if (!Files.isRegularFile(levelDat)) {
            throw new IOException("level.dat not found in " + worldDir);
        }

        CompoundTag root = NbtIo.readCompressed(levelDat, NbtAccounter.unlimitedHeap());
        CompoundTag data = root.getCompound("Data")
                .orElseThrow(() -> new IOException("level.dat missing Data compound"));

        for (String key : LEVEL_DATA_KEYS) {
            Tag tag = snapshotTag.get(key);
            if (tag != null) {
                data.put(key, tag);
            }
        }

        NbtIo.writeCompressed(root, levelDat);
    }
}

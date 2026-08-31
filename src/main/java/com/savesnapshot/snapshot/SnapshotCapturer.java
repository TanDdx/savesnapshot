package com.savesnapshot.snapshot;

import com.savesnapshot.SaveSnapshotMod;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class SnapshotCapturer {
    public record CaptureResult(int chunkCount, int playerCount, Path dir) {}

    private SnapshotCapturer() {}

    public static CaptureResult capture(MinecraftServer server, String name, boolean automatic) throws IOException {
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        SnapshotStorage storage = new SnapshotStorage(worldDir);
        Path snapshotDir = storage.dir(name);
        Files.createDirectories(snapshotDir);

        int chunkCount = 0;
        int playerCount = 0;

        for (ServerLevel level : server.getAllLevels()) {
            String dimName = dimensionDirName(level);
            Path chunkDir = snapshotDir.resolve("chunks").resolve(dimName);
            Files.createDirectories(chunkDir);

            for (ChunkHolder holder : getLoadedChunkHolders(level)) {
                LevelChunk chunk = holder.getTickingChunk();
                if (chunk == null) {
                    continue;
                }

                CompoundTag tag = SerializableChunkData.copyOf(level, chunk).write();
                ChunkPos pos = chunk.getPos();
                Path chunkFile = chunkDir.resolve("c." + pos.x() + "." + pos.z() + ".nbt");
                NbtIo.writeCompressed(tag, chunkFile);
                chunkCount++;
            }
        }

        Path playerDir = snapshotDir.resolve("playerdata");
        Files.createDirectories(playerDir);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, player.registryAccess());
            player.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            UUID uuid = player.getUUID();
            Path playerFile = playerDir.resolve(uuid + ".dat");
            NbtIo.writeCompressed(tag, playerFile);
            playerCount++;
        }

        saveLevelData(server, snapshotDir);

        String gameVersion = SharedConstants.getCurrentVersion().name();
        SnapshotMeta meta = new SnapshotMeta(name, System.currentTimeMillis(), automatic, gameVersion, chunkCount);
        meta.save(snapshotDir);

        SaveSnapshotMod.LOGGER.info(
            "Captured snapshot {} in {} (chunks={}, players={})",
            name, snapshotDir, chunkCount, playerCount);

        return new CaptureResult(chunkCount, playerCount, snapshotDir);
    }

    private static void saveLevelData(MinecraftServer server, Path snapshotDir) throws IOException {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            SaveSnapshotMod.LOGGER.warn("Overworld not available, skipping leveldata.dat");
            return;
        }

        CompoundTag levelTag = new CompoundTag();
        levelTag.putLong("GameTime", overworld.getLevelData().getGameTime());
        levelTag.putLong("DayTime", overworld.getOverworldClockTime());

        var weather = overworld.getWeatherData();
        levelTag.putBoolean("raining", weather.isRaining());
        levelTag.putBoolean("thundering", weather.isThundering());
        levelTag.putInt("rainTime", weather.getRainTime());
        levelTag.putInt("thunderTime", weather.getThunderTime());
        levelTag.putInt("clearWeatherTime", weather.getClearWeatherTime());

        NbtIo.writeCompressed(levelTag, snapshotDir.resolve("leveldata.dat"));
    }

    /**
     * Enumerates all loaded chunk holders for a dimension.
     * <p>
     * Minecraft 26.2 does not expose a public accessor for the full set of chunk holders.
     * We reflectively read {@code ChunkMap.visibleChunkMap}, which is a volatile snapshot of
     * the internal updating map and is safe to iterate on the server main thread.
     */
    @SuppressWarnings("unchecked")
    private static Iterable<ChunkHolder> getLoadedChunkHolders(ServerLevel level) {
        try {
            ChunkMap chunkMap = level.getChunkSource().chunkMap;
            Field visibleChunkMapField = ChunkMap.class.getDeclaredField("visibleChunkMap");
            visibleChunkMapField.setAccessible(true);
            Long2ObjectMap<ChunkHolder> visibleChunkMap =
                (Long2ObjectMap<ChunkHolder>) visibleChunkMapField.get(chunkMap);
            return visibleChunkMap.values();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to enumerate loaded chunks via reflection", e);
        }
    }

    public static String dimensionDirName(ServerLevel level) {
        return level.dimension().identifier().toString().replace(':', '_');
    }
}

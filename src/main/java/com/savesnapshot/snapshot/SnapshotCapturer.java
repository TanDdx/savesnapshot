package com.savesnapshot.snapshot;

import com.savesnapshot.SaveSnapshotMod;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SnapshotCapturer {
    public record CaptureResult(int chunkCount, int playerCount, Path dir) {}

    private SnapshotCapturer() {}

    public static CaptureResult capture(MinecraftServer server, String name, boolean automatic) throws IOException {
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        long freeBytes = Files.getFileStore(worldDir).getUsableSpace();
        if (freeBytes < 100L * 1024 * 1024) {
            throw new IOException("磁盘空间不足（剩余 " + freeBytes / 1024 / 1024 + " MB），已中止快照");
        }
        SnapshotStorage storage = new SnapshotStorage(worldDir);
        Path snapshotDir = storage.dir(name);
        Files.createDirectories(snapshotDir);

        int chunkCount = 0;
        int entityCount = 0;
        int playerCount = 0;

        for (ServerLevel level : server.getAllLevels()) {
            String dimName = dimensionDirName(level);
            Path chunkDir = snapshotDir.resolve("chunks").resolve(dimName);
            Files.createDirectories(chunkDir);

            LongSet chunkKeys = new LongOpenHashSet();
            for (ChunkHolder holder : getLoadedChunkHolders(level)) {
                LevelChunk chunk = holder.getTickingChunk();
                if (chunk == null) {
                    continue;
                }

                CompoundTag tag = SerializableChunkData.copyOf(level, chunk).write();
                ChunkPos pos = chunk.getPos();
                Path chunkFile = chunkDir.resolve("c." + pos.x() + "." + pos.z() + ".nbt");
                NbtIo.writeCompressed(tag, chunkFile);
                chunkKeys.add(pos.pack());
                chunkCount++;
            }

            // 实体独立存储（1.17+ 起实体不在区块 NBT 里，而在 <维度>/entities/ 的 region 文件）
            entityCount += captureEntities(level, chunkDir, chunkKeys);
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
            "Captured snapshot {} in {} (chunks={}, entities={}, players={})",
            name, snapshotDir, chunkCount, entityCount, playerCount);

        return new CaptureResult(chunkCount, playerCount, snapshotDir);
    }

    /**
     * 把维度内所有已加载实体按区块序列化为 e.<x>.<z>.nbt（原版 EntityStorage 格式：
     * {DataVersion, Entities: [...], Position: ChunkPos}）。
     * 每个快照覆盖的区块都会写一条实体记录（哪怕为空）——空记录用于读档时清掉后来出现的实体。
     * 玩家跳过：走 players/data 恢复。
     */
    private static int captureEntities(ServerLevel level, Path chunkDir, LongSet chunkKeys) throws IOException {
        Map<ChunkPos, List<CompoundTag>> byChunk = new HashMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            ChunkPos pos = entity.chunkPosition();
            if (!chunkKeys.contains(pos.pack())) {
                continue;
            }
            ProblemReporter.Collector reporter = new ProblemReporter.Collector();
            TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
            if (entity.save(output)) {
                byChunk.computeIfAbsent(pos, k -> new ArrayList<>()).add(output.buildResult());
            }
        }

        int count = 0;
        for (long key : chunkKeys) {
            ChunkPos pos = ChunkPos.unpack(key);
            List<CompoundTag> entities = byChunk.getOrDefault(pos, List.of());
            ListTag list = new ListTag();
            entities.forEach(list::add);
            CompoundTag chunkTag = NbtUtils.addCurrentDataVersion(new CompoundTag());
            chunkTag.put("Entities", list);
            chunkTag.store("Position", ChunkPos.CODEC, pos);
            NbtIo.writeCompressed(chunkTag, chunkDir.resolve("e." + pos.x() + "." + pos.z() + ".nbt"));
            count += entities.size();
        }
        return count;
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

package com.savesnapshot.snapshot;

import com.savesnapshot.RestoreState;
import com.savesnapshot.SaveSnapshotMod;
import com.savesnapshot.config.ConfigHolder;
import com.savesnapshot.config.SnapshotConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class AutoSnapshotTicker {
    private static final SimpleDateFormat NAME_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");
    private static int ticks;

    private AutoSnapshotTicker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.isDedicatedServer()) {
                return;
            }
            if (RestoreState.isRestoring()) {
                return;
            }
            SnapshotConfig config = ConfigHolder.get();
            if (!config.autoEnabled) {
                return;
            }
            if (server.getPlayerList().getPlayers().isEmpty()) {
                return;
            }
            if (++ticks < config.autoIntervalMinutes * 60 * 20) {
                return;
            }
            ticks = 0;
            String name = "auto-" + NAME_FORMAT.format(new Date());
            try {
                SnapshotCapturer.capture(server, name, true);
                SnapshotStorage storage = new SnapshotStorage(server.getWorldPath(LevelResource.ROOT));
                for (String toDelete : RotationPolicy.namesToDelete(storage.autoOldestFirst(), config.autoKeep)) {
                    storage.delete(toDelete);
                }
            } catch (IOException e) {
                SaveSnapshotMod.LOGGER.error("自动快照失败", e);
            }
        });
    }
}

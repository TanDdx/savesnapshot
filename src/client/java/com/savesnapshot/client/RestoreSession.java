package com.savesnapshot.client;

import com.savesnapshot.RestoreState;
import com.savesnapshot.SaveSnapshotMod;
import com.savesnapshot.snapshot.SnapshotRestorer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public final class RestoreSession {
    private RestoreSession() {}

    public static void begin(String snapshotName) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || !RestoreState.tryStart()) {
            return;
        }
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        String levelId = worldDir.getFileName().toString();

        ProgressScreen progress = new ProgressScreen(true);
        progress.progressStart(Component.translatable("savesnapshot.restoring"));
        mc.disconnect(progress, false);

        new Thread(() -> {
            try {
                waitForServerStop(mc);
                SnapshotRestorer.restoreFiles(worldDir, snapshotName);
                mc.execute(() -> rejoin(mc, levelId));
            } catch (Exception e) {
                SaveSnapshotMod.LOGGER.error("Restore failed", e);
                mc.execute(() -> {
                    RestoreState.finish();
                    mc.gui.setScreen(new TitleScreen());
                });
            }
        }, "SaveSnapshot-Restore").start();
    }

    private static void waitForServerStop(Minecraft mc) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (mc.getSingleplayerServer() != null) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Server stop timed out");
            }
            Thread.sleep(50);
        }
    }

    private static void rejoin(Minecraft mc, String levelId) {
        RestoreState.finish();
        mc.createWorldOpenFlows().openWorld(levelId, () -> mc.gui.setScreen(new TitleScreen()));
    }
}

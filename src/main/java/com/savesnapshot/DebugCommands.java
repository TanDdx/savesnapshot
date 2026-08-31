package com.savesnapshot;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.savesnapshot.snapshot.SnapshotCapturer;
import com.savesnapshot.snapshot.SnapshotStorage;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class DebugCommands {
    private DebugCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("savesnapshot")
                .then(Commands.literal("create")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            try {
                                var result = SnapshotCapturer.capture(ctx.getSource().getServer(), name, false);
                                var count = result.chunkCount();
                                ctx.getSource().sendSuccess(
                                    () -> Component.literal("快照 " + name + " 已保存（" + count + " 区块）"), false);
                                return 1;
                            } catch (java.io.IOException e) {
                                ctx.getSource().sendFailure(
                                    Component.literal("保存快照失败: " + e.getMessage()));
                                return 0;
                            }
                        })))
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        try {
                            var storage = new SnapshotStorage(
                                ctx.getSource().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
                            for (var meta : storage.list()) {
                                ctx.getSource().sendSuccess(
                                    () -> Component.literal(meta.name() + " chunks=" + meta.chunkCount()), false);
                            }
                            return 1;
                        } catch (java.io.IOException e) {
                            ctx.getSource().sendFailure(
                                Component.literal("列出快照失败: " + e.getMessage()));
                            return 0;
                        }
                    }))));
    }
}

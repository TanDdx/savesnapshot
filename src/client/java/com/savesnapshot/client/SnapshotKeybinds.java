package com.savesnapshot.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.savesnapshot.client.gui.SnapshotListScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class SnapshotKeybinds {
    private static KeyMapping openKey;

    private SnapshotKeybinds() {}

    public static void register() {
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.savesnapshot.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                if (mc.player != null && mc.getSingleplayerServer() != null) {
                    Screen current = mc.gui.screen();
                    mc.gui.setScreen(new SnapshotListScreen(current));
                }
            }
        });
    }
}

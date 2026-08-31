package com.savesnapshot;

import com.savesnapshot.client.SnapshotKeybinds;
import net.fabricmc.api.ClientModInitializer;

public class SaveSnapshotClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SnapshotKeybinds.register();
    }
}

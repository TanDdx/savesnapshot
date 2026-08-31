package com.savesnapshot;

import com.savesnapshot.client.gui.SnapshotListScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

public class SaveSnapshotModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SnapshotListScreen::new;
    }
}

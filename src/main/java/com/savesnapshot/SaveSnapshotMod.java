package com.savesnapshot;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaveSnapshotMod implements ModInitializer {
    public static final String MOD_ID = "savesnapshot";
    public static final Logger LOGGER = LoggerFactory.getLogger("SaveSnapshot");

    @Override
    public void onInitialize() {
        LOGGER.info("SaveSnapshot initialized");
    }
}

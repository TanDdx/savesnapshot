package com.savesnapshot.config;

import com.savesnapshot.SaveSnapshotMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;

/** 全局配置单例（common 侧，供 server tick 与 client 界面共用）。 */
public final class ConfigHolder {
    private static SnapshotConfig config;

    private ConfigHolder() {
    }

    public static synchronized SnapshotConfig get() {
        if (config == null) {
            config = SnapshotConfig.load(path());
        }
        return config;
    }

    public static synchronized void save() {
        try {
            get().save(path());
        } catch (IOException e) {
            SaveSnapshotMod.LOGGER.error("保存配置失败", e);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("savesnapshot.json");
    }
}

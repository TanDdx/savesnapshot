package com.savesnapshot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** 纯逻辑：JSON 配置。不引用 MC 类，可单测。 */
public class SnapshotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean autoEnabled = true;
    public int autoIntervalMinutes = 10;
    public int autoKeep = 10;

    public static SnapshotConfig load(Path path) {
        if (!Files.isRegularFile(path)) {
            return new SnapshotConfig();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            SnapshotConfig config = new SnapshotConfig();
            if (obj.has("autoEnabled")) {
                config.autoEnabled = obj.get("autoEnabled").getAsBoolean();
            }
            if (obj.has("autoIntervalMinutes")) {
                config.autoIntervalMinutes = Math.max(1, obj.get("autoIntervalMinutes").getAsInt());
            }
            if (obj.has("autoKeep")) {
                config.autoKeep = Math.max(1, obj.get("autoKeep").getAsInt());
            }
            return config;
        } catch (Exception e) {
            return new SnapshotConfig();
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(this, writer);
        }
    }
}

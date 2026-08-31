package com.savesnapshot.snapshot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** 快照元数据（meta.json）。纯逻辑，可单测。 */
public record SnapshotMeta(String name, long createdAtMillis, boolean automatic, String gameVersion, int chunkCount) {
    public static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new Gson();

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("formatVersion", FORMAT_VERSION);
        obj.addProperty("name", name);
        obj.addProperty("createdAtMillis", createdAtMillis);
        obj.addProperty("automatic", automatic);
        obj.addProperty("gameVersion", gameVersion);
        obj.addProperty("chunkCount", chunkCount);
        return obj;
    }

    public static SnapshotMeta fromJson(JsonObject obj) {
        return new SnapshotMeta(
                obj.get("name").getAsString(),
                obj.get("createdAtMillis").getAsLong(),
                obj.get("automatic").getAsBoolean(),
                obj.has("gameVersion") ? obj.get("gameVersion").getAsString() : "unknown",
                obj.has("chunkCount") ? obj.get("chunkCount").getAsInt() : 0);
    }

    public void save(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("meta.json"), GSON.toJson(toJson()));
    }

    /** @return null 表示 meta.json 缺失或损坏。 */
    public static SnapshotMeta load(Path dir) {
        Path file = dir.resolve("meta.json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            return null;
        }
    }
}

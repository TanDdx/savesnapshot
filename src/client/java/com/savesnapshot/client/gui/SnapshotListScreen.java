package com.savesnapshot.client.gui;

import com.savesnapshot.SaveSnapshotMod;
import com.savesnapshot.client.RestoreSession;
import com.savesnapshot.config.ConfigHolder;
import com.savesnapshot.config.SnapshotConfig;
import com.savesnapshot.snapshot.SnapshotMeta;
import com.savesnapshot.snapshot.SnapshotStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SnapshotListScreen extends Screen {
    private static final Component TITLE = Component.translatable("savesnapshot.title");
    private static final Component NEW_SNAPSHOT = Component.translatable("savesnapshot.button.new");
    private static final Component RESTORE = Component.translatable("savesnapshot.button.restore");
    private static final Component RENAME = Component.translatable("savesnapshot.button.rename");
    private static final Component DELETE = Component.translatable("savesnapshot.button.delete");
    private static final Component AUTO = Component.translatable("savesnapshot.button.auto");
    private static final Component INTERVAL = Component.translatable("savesnapshot.button.interval");
    private static final Component KEEP = Component.translatable("savesnapshot.button.keep");
    private static final Component NOT_IN_WORLD = Component.translatable("savesnapshot.not_in_world");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Screen parent;
    private @Nullable IntegratedServer server;
    private @Nullable SnapshotStorage storage;
    private List<SnapshotMeta> snapshots = List.of();

    private @Nullable SnapshotList list;
    private @Nullable StringWidget notInWorldLabel;
    private Button newButton;
    private Button restoreButton;
    private Button renameButton;
    private Button deleteButton;
    private Button autoButton;
    private Button intervalButton;
    private Button keepButton;
    private Button doneButton;

    public SnapshotListScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.server = this.minecraft.getSingleplayerServer();
        if (this.server != null) {
            this.storage = new SnapshotStorage(this.server.getWorldPath(LevelResource.ROOT));
        } else {
            this.storage = null;
        }

        loadSnapshots();

        int centerX = this.width / 2;
        int bottomY = this.height - 55;
        int secondRowY = this.height - 30;
        int buttonW = 60;

        if (this.storage != null) {
            this.list = new SnapshotList();
            this.addRenderableWidget(this.list);
            List<SnapshotEntry> entries = new ArrayList<>();
            for (SnapshotMeta meta : this.snapshots) {
                entries.add(new SnapshotEntry(meta));
            }
            this.list.replaceEntries(entries);
        } else {
            this.notInWorldLabel = new StringWidget(centerX - 100, this.height / 2 - 10, 200, 20, NOT_IN_WORLD, this.font);
            this.addRenderableWidget(this.notInWorldLabel);
        }

        boolean inWorld = this.storage != null;
        boolean hasSelection = inWorld && this.list != null && this.list.getSelected() != null;

        this.newButton = this.addRenderableWidget(Button.builder(NEW_SNAPSHOT, b -> onNew())
            .bounds(centerX - 130, bottomY, buttonW, 20)
            .build());
        this.restoreButton = this.addRenderableWidget(Button.builder(RESTORE, b -> onRestore())
            .bounds(centerX - 66, bottomY, buttonW, 20)
            .build());
        this.renameButton = this.addRenderableWidget(Button.builder(RENAME, b -> onRename())
            .bounds(centerX - 2, bottomY, buttonW, 20)
            .build());
        this.deleteButton = this.addRenderableWidget(Button.builder(DELETE, b -> onDelete())
            .bounds(centerX + 62, bottomY, buttonW, 20)
            .build());

        this.autoButton = this.addRenderableWidget(Button.builder(autoLabel(), b -> onToggleAuto())
            .bounds(centerX - 130, secondRowY, buttonW, 20)
            .build());
        this.intervalButton = this.addRenderableWidget(Button.builder(intervalLabel(), b -> onCycleInterval())
            .bounds(centerX - 66, secondRowY, buttonW, 20)
            .build());
        this.keepButton = this.addRenderableWidget(Button.builder(keepLabel(), b -> onCycleKeep())
            .bounds(centerX - 2, secondRowY, buttonW, 20)
            .build());
        this.doneButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onDone())
            .bounds(centerX + 62, secondRowY, buttonW, 20)
            .build());

        updateButtons();
    }

    private void loadSnapshots() {
        if (this.storage == null) {
            this.snapshots = List.of();
            return;
        }
        try {
            this.snapshots = this.storage.list();
        } catch (IOException e) {
            SaveSnapshotMod.LOGGER.error("Failed to list snapshots", e);
            this.snapshots = List.of();
        }
    }

    private Set<String> existingNames() {
        Set<String> names = new LinkedHashSet<>();
        for (SnapshotMeta meta : this.snapshots) {
            names.add(meta.name());
        }
        return names;
    }

    private void updateButtons() {
        boolean inWorld = this.storage != null;
        boolean hasSelection = inWorld && this.list != null && this.list.getSelected() != null;
        this.newButton.active = inWorld;
        this.restoreButton.active = hasSelection;
        this.renameButton.active = hasSelection;
        this.deleteButton.active = hasSelection;
        this.autoButton.active = inWorld;
        this.intervalButton.active = inWorld;
        this.keepButton.active = inWorld;
    }

    private void onNew() {
        if (this.server == null || this.storage == null) {
            return;
        }
        this.minecraft.gui.setScreen(SnapshotNameScreen.forNew(this, this.server, this.storage, existingNames()));
    }

    private void onRestore() {
        if (this.list == null || this.storage == null) {
            return;
        }
        SnapshotEntry entry = this.list.getSelected();
        if (entry == null) {
            return;
        }
        String name = entry.meta.name();
        Component title = Component.translatable("savesnapshot.restore.confirm.title", name);
        Component message = Component.translatable("savesnapshot.restore.confirm.message");
        this.minecraft.gui.setScreen(new ConfirmScreen(result -> {
            if (result) {
                RestoreSession.begin(name);
            } else {
                this.minecraft.gui.setScreen(this);
            }
        }, title, message));
    }

    private void onRename() {
        if (this.list == null || this.storage == null) {
            return;
        }
        SnapshotEntry entry = this.list.getSelected();
        if (entry == null) {
            return;
        }
        this.minecraft.gui.setScreen(SnapshotNameScreen.forRename(this, this.storage, entry.meta.name(), existingNames()));
    }

    private void onDelete() {
        if (this.list == null || this.storage == null) {
            return;
        }
        SnapshotEntry entry = this.list.getSelected();
        if (entry == null) {
            return;
        }
        String name = entry.meta.name();
        Component title = Component.translatable("savesnapshot.delete.confirm.title", name);
        Component message = Component.translatable("savesnapshot.delete.confirm.message");
        this.minecraft.gui.setScreen(new ConfirmScreen(result -> {
            if (result) {
                try {
                    this.storage.delete(name);
                } catch (IOException e) {
                    SaveSnapshotMod.LOGGER.error("Failed to delete snapshot {}", name, e);
                }
            }
            this.minecraft.gui.setScreen(new SnapshotListScreen(this));
        }, title, message));
    }

    private void onToggleAuto() {
        SnapshotConfig config = ConfigHolder.get();
        config.autoEnabled = !config.autoEnabled;
        ConfigHolder.save();
        this.autoButton.setMessage(autoLabel());
    }

    private void onCycleInterval() {
        SnapshotConfig config = ConfigHolder.get();
        int[] values = {5, 10, 15, 20, 30, 60};
        int current = config.autoIntervalMinutes;
        int next = values[0];
        for (int i = 0; i < values.length; i++) {
            if (values[i] > current) {
                next = values[i];
                break;
            }
            if (i == values.length - 1) {
                next = values[0];
            }
        }
        config.autoIntervalMinutes = next;
        ConfigHolder.save();
        this.intervalButton.setMessage(intervalLabel());
    }

    private void onCycleKeep() {
        SnapshotConfig config = ConfigHolder.get();
        int[] values = {5, 10, 20, 50, 100};
        int current = config.autoKeep;
        int next = values[0];
        for (int i = 0; i < values.length; i++) {
            if (values[i] > current) {
                next = values[i];
                break;
            }
            if (i == values.length - 1) {
                next = values[0];
            }
        }
        config.autoKeep = next;
        ConfigHolder.save();
        this.keepButton.setMessage(keepLabel());
    }

    private Component autoLabel() {
        SnapshotConfig config = ConfigHolder.get();
        return Component.translatable("savesnapshot.button.auto.value", config.autoEnabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private Component intervalLabel() {
        SnapshotConfig config = ConfigHolder.get();
        return Component.translatable("savesnapshot.button.interval.value", config.autoIntervalMinutes);
    }

    private Component keepLabel() {
        SnapshotConfig config = ConfigHolder.get();
        return Component.translatable("savesnapshot.button.keep.value", config.autoKeep);
    }

    private void onDone() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    private static String formatSubtitle(SnapshotMeta meta) {
        String date = DATE_FORMAT.format(Instant.ofEpochMilli(meta.createdAtMillis()).atZone(ZoneId.systemDefault()));
        String autoTag = meta.automatic() ? "[auto] " : "";
        return autoTag + date + " · " + meta.gameVersion();
    }

    private class SnapshotList extends ObjectSelectionList<SnapshotEntry> {
        SnapshotList() {
            super(SnapshotListScreen.this.minecraft, 220, SnapshotListScreen.this.height - 100, 40, 24);
        }

        @Override
        public void setSelected(@Nullable SnapshotEntry entry) {
            super.setSelected(entry);
            SnapshotListScreen.this.updateButtons();
        }
    }

    private class SnapshotEntry extends ObjectSelectionList.Entry<SnapshotEntry> {
        private final SnapshotMeta meta;

        SnapshotEntry(SnapshotMeta meta) {
            this.meta = meta;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int x = this.getContentX();
            int y = this.getContentY();
            graphics.text(SnapshotListScreen.this.font, this.meta.name(), x, y, -1);
            graphics.text(SnapshotListScreen.this.font, formatSubtitle(this.meta), x, y + 12, 0xFFAAAAAA);
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.meta.name());
        }
    }
}

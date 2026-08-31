package com.savesnapshot.client.gui;

import com.savesnapshot.SaveSnapshotMod;
import com.savesnapshot.snapshot.SnapshotCapturer;
import com.savesnapshot.snapshot.SnapshotNameValidator;
import com.savesnapshot.snapshot.SnapshotStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SnapshotNameScreen extends Screen {
    private static final Component TITLE_NEW = Component.translatable("savesnapshot.name.title.new");
    private static final Component NAME_LABEL = Component.translatable("savesnapshot.name.label");

    private final Screen parent;
    private final @Nullable IntegratedServer server;
    private final SnapshotStorage storage;
    private final @Nullable String oldName;
    private final Set<String> existingNames;

    private EditBox nameBox;
    private Button okButton;
    private Button cancelButton;
    private @Nullable Component errorMessage;

    public static SnapshotNameScreen forNew(Screen parent, IntegratedServer server, SnapshotStorage storage, Set<String> existingNames) {
        return new SnapshotNameScreen(parent, server, storage, null, existingNames, TITLE_NEW);
    }

    public static SnapshotNameScreen forRename(Screen parent, SnapshotStorage storage, String oldName, Set<String> existingNames) {
        Set<String> others = new HashSet<>(existingNames);
        others.remove(oldName);
        return new SnapshotNameScreen(parent, null, storage, oldName, others, Component.translatable("savesnapshot.name.title.rename", oldName));
    }

    private SnapshotNameScreen(Screen parent, @Nullable IntegratedServer server, SnapshotStorage storage,
                               @Nullable String oldName, Set<String> existingNames, Component title) {
        super(title);
        this.parent = parent;
        this.server = server;
        this.storage = storage;
        this.oldName = oldName;
        this.existingNames = existingNames;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.nameBox = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, NAME_LABEL);
        this.nameBox.setMaxLength(SnapshotNameValidator.MAX_LENGTH);
        if (this.oldName != null) {
            this.nameBox.setValue(this.oldName);
            this.nameBox.setFocused(true);
            this.nameBox.setHighlightPos(0);
            this.nameBox.moveCursorToEnd(false);
        } else {
            this.nameBox.setFocused(true);
        }
        this.addRenderableWidget(this.nameBox);

        this.okButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_OK, b -> onOk())
            .bounds(centerX - 105, centerY + 25, 100, 20)
            .build());
        this.cancelButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onCancel())
            .bounds(centerX + 5, centerY + 25, 100, 20)
            .build());
    }

    @Override
    protected void setInitialFocus() {
        // 26.2 中焦点在 Screen 显示后由框架重置，必须重写此钩子，否则输入框不进入 focus
        this.setInitialFocus(this.nameBox);
    }

    private void onOk() {
        String raw = this.nameBox.getValue();
        String name = SnapshotNameValidator.sanitize(raw);
        if (!name.equals(raw)) {
            this.nameBox.setValue(name);
        }

        String error = SnapshotNameValidator.validate(name, this.existingNames);
        if (error != null) {
            this.errorMessage = Component.literal(error).withStyle(ChatFormatting.RED);
            return;
        }

        if (this.oldName != null) {
            try {
                this.storage.rename(this.oldName, name);
            } catch (IOException e) {
                SaveSnapshotMod.LOGGER.error("Failed to rename snapshot {} to {}", this.oldName, name, e);
                this.errorMessage = Component.translatable("savesnapshot.name.error.rename").withStyle(ChatFormatting.RED);
                return;
            }
        } else if (this.server != null) {
            IntegratedServer s = this.server;
            s.execute(() -> {
                try {
                    SnapshotCapturer.capture(s, name, false);
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        // 捕捉完成后：聊天提示 + 刷新打开中的列表
                        mc.gui.hud.getChat().addClientSystemMessage(
                            Component.translatable("savesnapshot.saved", name));
                        SnapshotListScreen.refreshIfOpen(mc);
                    });
                } catch (Exception e) {
                    SaveSnapshotMod.LOGGER.error("Failed to create snapshot {}", name, e);
                }
            });
        } else {
            this.errorMessage = Component.translatable("savesnapshot.name.error.no_server").withStyle(ChatFormatting.RED);
            return;
        }

        this.minecraft.gui.setScreen(this.parent);
    }

    private void onCancel() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (this.errorMessage != null) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            graphics.text(this.font, this.errorMessage, centerX - this.font.width(this.errorMessage.getVisualOrderText()) / 2, centerY - 35, -1);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}

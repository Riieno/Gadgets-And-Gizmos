package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.content.pose.ArmorStandPoseData;
import com.rieno.gadgetsandgizmos.content.pose.ArmorStandPosePart;
import com.rieno.gadgetsandgizmos.content.pose.ArmorStandPosePreset;
import com.rieno.gadgetsandgizmos.neoforge.network.ArmorStandPoseSyncPayload;
import com.rieno.gadgetsandgizmos.registry.CTEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

// Edit, preview and save complete Armor Stand and mannequin poses
public class ArmorStandPoseScreen extends Screen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int PRESET_PANEL_W = 540;
    private static final int PRESET_PANEL_H = 286;
    private static final int EDITOR_LEFT_W = 230;
    private static final int EDITOR_RIGHT_W = 330;
    private static final int EDITOR_SIDEBAR_H = 326;
    private static final int EDITOR_SIDE_MARGIN = 12;
    private static final int EDITOR_MIN_CENTER_GAP = 110;
    private static final int BUTTON_H = 16;
    private static final int FIELD_H = 16;
    private static final int GRID_COLS = 6;
    private static final int GRID_ROWS = 3;
    private static final int GRID_CELL_W = 82;
    private static final int GRID_CELL_H = 64;
    // Reuse the client-thread preview transforms
    private static final Vector3f PREVIEW_TRANSLATION = new Vector3f();
    private static final Quaternionf PREVIEW_ROTATION =
            new Quaternionf().rotateZ((float) Math.PI);
    private static final Quaternionf PREVIEW_CAMERA_ROTATION = new Quaternionf();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Armor stand
    private final ArmorStand armorStand;
    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();
    // Tracked pose fields
    private final EnumMap<ArmorStandPosePart, EditBox[]> poseFields = new EnumMap<>(ArmorStandPosePart.class);
    // Tracked all fields
    private final List<EditBox> allFields = new ArrayList<>();
    // Tracked toggle controls
    private final List<ToggleControl> toggleControls = new ArrayList<>();
    // Tracked custom presets
    private final List<ArmorStandPosePreset> customPresets = new ArrayList<>();

    // Current armor stand pose data
    private ArmorStandPoseData data;
    // Current close restore tag
    private CompoundTag closeRestoreTag;
    // Current view mode
    private ViewMode viewMode = ViewMode.EDITOR;
    // Current rotation field
    private EditBox rotationField;
    // Current preset name field
    private EditBox presetNameField;
    // Current preview entity
    private ArmorStand previewEntity;
    // Current status message
    private Component statusMessage = Component.empty();
    // Current preset name
    private String presetName = "";
    // Status tick count
    private int statusTicks;
    // Current left pos
    private int leftPos;
    // Current top pos
    private int topPos;
    // Current editor left panel x
    private int editorLeftPanelX;
    // Current editor right panel x
    private int editorRightPanelX;
    // Current editor panel y
    private int editorPanelY;
    // Current field x
    private int fieldX;
    // Current field y
    private int fieldY;
    // Current preset scroll row
    private int presetScrollRow;
    // Tracks whether armor stand pose is updating widgets
    private boolean updatingWidgets;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the armor stand pose
    public ArmorStandPoseScreen(ArmorStand armorStand) {
        super(Component.translatable("createthrusters.pose_screen.title"));
        this.armorStand = armorStand;
        this.data = ArmorStandPoseData.fromEntity(armorStand);
        this.closeRestoreTag = this.data.toTag().copy();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Open the armor stand pose
    public static void open(int entityId) {
        if (!ArmorStandPoseClientState.isLocallyEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof ArmorStand armorStand) {
            minecraft.setScreen(new ArmorStandPoseScreen(armorStand));
        }
    }

    // Check if this is a pause screen
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Initialize the armor stand pose
    @Override
    protected void init() {
        updateLayout();
        reloadCustomPresets(false);
        rebuildPoseWidgets();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the armor stand pose
    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0) {
            statusTicks--;
        }
    }

    // Draw the armor stand pose
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (viewMode == ViewMode.PRESETS) {
            guiGraphics.fillGradient(0, 0, width, height, 0x22000000, 0x55000000);
        }
        int localMouseX = scalableGui.mouseX(mouseX);
        int localMouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(guiGraphics);
        try {
            if (viewMode == ViewMode.EDITOR) {
                renderEditor(guiGraphics, localMouseX, localMouseY);
            } else {
                CTCreateScreenHelper.renderPanel(guiGraphics, leftPos, topPos, PRESET_PANEL_W, PRESET_PANEL_H);
                guiGraphics.drawCenteredString(font, title, leftPos + PRESET_PANEL_W / 2, topPos + 7,
                        CTCreateScreenHelper.BANNER_TITLE_COLOR);
                renderPresetGrid(guiGraphics, localMouseX, localMouseY);
            }
            renderStatus(guiGraphics);
        } finally {
            scalableGui.pop(guiGraphics);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // Draw the background
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        double localMouseX = scalableGui.mouseX(mouseX);
        double localMouseY = scalableGui.mouseY(mouseY);
        if (btn == 0 && viewMode == ViewMode.PRESETS && clickPresetGrid(localMouseX, localMouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    // Handle mouse scrolled
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double localMouseX = scalableGui.mouseX(mouseX);
        double localMouseY = scalableGui.mouseY(mouseY);
        if (viewMode == ViewMode.PRESETS && insidePresetGrid(localMouseX, localMouseY)) {
            presetScrollRow = Mth.clamp(presetScrollRow - (int) Math.signum(scrollY), 0, maxPresetScrollRow());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Handle key pressed
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (viewMode == ViewMode.PRESETS && keyCode == 256) {
            openEditor();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Handle the close event
    @Override
    public void onClose() {
        ArmorStandPoseData.applyAllowedTag(armorStand, closeRestoreTag.copy());
        minecraft.setScreen(null);
    }

    // Rebuild the pose widgets
    private void rebuildPoseWidgets() {
        clearWidgets();
        allFields.clear();
        poseFields.clear();
        toggleControls.clear();
        rotationField = null;
        presetNameField = null;
        updateLayout();
        if (viewMode == ViewMode.EDITOR) {
            initEditorWidgets();
        } else {
            initPresetWidgets();
        }
    }

    // Update the layout
    private void updateLayout() {
        if (viewMode == ViewMode.PRESETS) {
            leftPos = (width - PRESET_PANEL_W) / 2;
            topPos = (height - PRESET_PANEL_H) / 2;
            scalableGui.update(leftPos, topPos, PRESET_PANEL_W, PRESET_PANEL_H, width, height);
            return;
        }

        leftPos = 0;
        topPos = 0;
        scalableGui.updateUnscaled(0, 0, width, height);
        editorPanelY = Math.max(8, (height - EDITOR_SIDEBAR_H) / 2);
        if (editorPanelY + EDITOR_SIDEBAR_H > height - 8) {
            editorPanelY = Math.max(8, height - EDITOR_SIDEBAR_H - 8);
        }

        int sideMargin = Math.max(6, Math.min(EDITOR_SIDE_MARGIN, width / 64));
        int requiredWidth = EDITOR_LEFT_W + EDITOR_RIGHT_W + EDITOR_MIN_CENTER_GAP + sideMargin * 2;
        if (width >= requiredWidth) {
            editorLeftPanelX = sideMargin;
            editorRightPanelX = width - sideMargin - EDITOR_RIGHT_W;
            return;
        }

        int centerGap = Math.max(36, Math.min(EDITOR_MIN_CENTER_GAP,
                width - sideMargin * 2 - EDITOR_LEFT_W - EDITOR_RIGHT_W));
        int totalWidth = EDITOR_LEFT_W + centerGap + EDITOR_RIGHT_W;
        editorLeftPanelX = Math.max(sideMargin, (width - totalWidth) / 2);
        editorRightPanelX = editorLeftPanelX + EDITOR_LEFT_W + centerGap;
    }

    // Initialize the editor widgets
    private void initEditorWidgets() {
        int optionsX = editorLeftPanelX + 14;
        int optionsY = editorPanelY + 56;
        int toggleW = 86;
        int toggleGap = 8;
        addToggle("createthrusters.pose_screen.option.invisible",
                () -> data.invisible, val -> data.invisible = val, optionsX, optionsY);
        addToggle("createthrusters.pose_screen.option.no_base",
                () -> data.noBasePlate, val -> data.noBasePlate = val, optionsX + toggleW + toggleGap, optionsY);
        addToggle("createthrusters.pose_screen.option.no_gravity",
                () -> data.noGravity, val -> data.noGravity = val, optionsX, optionsY + 21);
        addToggle("createthrusters.pose_screen.option.arms",
                () -> data.showArms, val -> data.showArms = val, optionsX + toggleW + toggleGap, optionsY + 21);
        addToggle("createthrusters.pose_screen.option.small",
                () -> data.small, val -> data.small = val, optionsX, optionsY + 42);
        addToggle("createthrusters.pose_screen.option.name",
                () -> data.nameVisible, val -> data.nameVisible = val, optionsX + toggleW + toggleGap, optionsY + 42);
        addToggle("createthrusters.pose_screen.option.locked",
                () -> data.locked, val -> data.locked = val, optionsX, optionsY + 63);
        rotationField = addNumberField(optionsX + 138, editorPanelY + 143, 58,
                format(data.rotation), "createthrusters.pose_screen.rotation");

        presetNameField = new CTScaledEditBox(scalableGui, font, optionsX, editorPanelY + 194, 202, FIELD_H,
                Component.translatable("createthrusters.pose_screen.preset_name"));
        presetNameField.setMaxLength(48);
        presetNameField.setBordered(false);
        presetNameField.setValue(presetName);
        presetNameField.setResponder(val -> presetName = val == null ? "" : val);
        addRenderableWidget(presetNameField);

        int leftButtonX = editorLeftPanelX + 14;
        int leftButtonW = EDITOR_LEFT_W - 28;
        addStyledButton(leftButtonX, editorPanelY + 228, leftButtonW, BUTTON_H,
                Component.translatable("createthrusters.pose_screen.presets"), btn -> openPresets());
        addStyledButton(leftButtonX, editorPanelY + 252, leftButtonW, BUTTON_H,
                Component.translatable("createthrusters.pose_screen.save_pose"), btn -> saveCurrentPreset());
        addStyledButton(leftButtonX, editorPanelY + 276, leftButtonW, BUTTON_H,
                Component.translatable("createthrusters.pose_screen.reload_disk"), btn -> reloadCustomPresets(true));
        addStyledButton(leftButtonX, editorPanelY + 300, leftButtonW, BUTTON_H,
                Component.translatable("createthrusters.pose_screen.hide_poser"), btn -> hidePoser());

        fieldX = editorRightPanelX + 18;
        fieldY = editorPanelY + 52;
        int row = 0;
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            EditBox[] boxes = new EditBox[3];
            float[] pose = data.pose(part);
            for (int axis = 0; axis < 3; axis++) {
                boxes[axis] = addNumberField(fieldX + 112 + axis * 58, fieldY + row * 26, 48,
                        format(pose[axis]), part.label() + " " + "XYZ".charAt(axis));
            }
            poseFields.put(part, boxes);
            row++;
        }

        int actionY = editorPanelY + 282;
        int actionGap = 8;
        int actionW = (EDITOR_RIGHT_W - 36 - actionGap * 2) / 3;
        int actionX = editorRightPanelX + 18;
        addStyledButton(actionX, actionY, actionW, BUTTON_H,
                Component.translatable("createthrusters.pose_screen.reset"), btn -> resetPreview());
        addStyledButton(actionX + actionW + actionGap, actionY, actionW, BUTTON_H,
                Component.translatable("createthrusters.pose_screen.apply"), btn -> sendPose());
        addStyledButton(actionX + (actionW + actionGap) * 2, actionY, actionW, BUTTON_H,
                Component.translatable("gui.done"), btn -> {
            sendPose();
            minecraft.setScreen(null);
        });
    }

    // Initialize the preset widgets
    private void initPresetWidgets() {
        int controlsY = topPos + PRESET_PANEL_H - 24;
        presetNameField = new CTScaledEditBox(scalableGui, font, leftPos + 154, controlsY, 150, FIELD_H,
                Component.translatable("createthrusters.pose_screen.preset_name"));
        presetNameField.setMaxLength(48);
        presetNameField.setBordered(false);
        presetNameField.setValue(presetName);
        presetNameField.setResponder(val -> presetName = val == null ? "" : val);
        addRenderableWidget(presetNameField);

        addStyledButton(leftPos + 18, controlsY, 54, BUTTON_H,
                Component.translatable("createthrusters.pose_screen.back"), btn -> openEditor());
        addStyledButton(leftPos + 76, controlsY, 74, BUTTON_H,
                Component.translatable("createthrusters.pose_screen.reload_disk"), btn -> reloadCustomPresets(true));
        addStyledButton(leftPos + 310, controlsY, 86, BUTTON_H,
                Component.translatable("createthrusters.pose_screen.save_pose"), btn -> saveCurrentPreset());
        addStyledButton(leftPos + 448, controlsY, 74, BUTTON_H,
                Component.translatable("gui.done"), btn -> {
            sendPose();
            minecraft.setScreen(null);
        });
        addStyledButton(leftPos + PRESET_PANEL_W - 28, gridY() + 4, 16, 14,
                Component.literal("^"), btn -> presetScrollRow = Math.max(0, presetScrollRow - 1));
        addStyledButton(leftPos + PRESET_PANEL_W - 28, gridY() + GRID_ROWS * GRID_CELL_H - 18, 16, 14,
                Component.literal("v"), btn -> presetScrollRow = Math.min(maxPresetScrollRow(), presetScrollRow + 1));
    }

    // Draw the editor
    private void renderEditor(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        CTCreateScreenHelper.renderPanel(guiGraphics, editorLeftPanelX, editorPanelY,
                EDITOR_LEFT_W, EDITOR_SIDEBAR_H);
        CTCreateScreenHelper.renderPanel(guiGraphics, editorRightPanelX, editorPanelY,
                EDITOR_RIGHT_W, EDITOR_SIDEBAR_H);
        guiGraphics.drawCenteredString(font, title, editorLeftPanelX + EDITOR_LEFT_W / 2, editorPanelY + 7,
                CTCreateScreenHelper.BANNER_TITLE_COLOR);
        guiGraphics.drawCenteredString(font, Component.translatable("createthrusters.pose_screen.rotations"),
                editorRightPanelX + EDITOR_RIGHT_W / 2, editorPanelY + 7, CTCreateScreenHelper.BANNER_TITLE_COLOR);
        CTCreateScreenHelper.renderSeparator(guiGraphics, editorLeftPanelX + 12, editorPanelY + 28,
                EDITOR_LEFT_W - 24);
        CTCreateScreenHelper.renderSeparator(guiGraphics, editorRightPanelX + 12, editorPanelY + 28,
                EDITOR_RIGHT_W - 24);

        guiGraphics.drawString(font, Component.translatable("createthrusters.pose_screen.options"),
                editorLeftPanelX + 14, editorPanelY + 40, CTCreateScreenHelper.LABEL_COLOR, false);
        guiGraphics.drawString(font, Component.translatable("createthrusters.pose_screen.rotation"),
                editorLeftPanelX + 14, editorPanelY + 147, CTCreateScreenHelper.LABEL_COLOR, false);
        guiGraphics.drawString(font, Component.translatable("createthrusters.pose_screen.preset_name"),
                editorLeftPanelX + 14, editorPanelY + 182, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        guiGraphics.drawString(font, Component.translatable("createthrusters.pose_screen.rotations"),
                editorRightPanelX + 18, editorPanelY + 40, CTCreateScreenHelper.LABEL_COLOR, false);
        guiGraphics.drawString(font, Component.translatable("createthrusters.pose_screen.actions"),
                editorRightPanelX + 18, editorPanelY + 260, CTCreateScreenHelper.LABEL_COLOR, false);

        renderFieldBackdrops(guiGraphics, mouseX, mouseY);
        int row = 0;
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            int y = fieldY + row * 26 + 4;
            guiGraphics.drawString(font, part.label(), fieldX, y, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
            guiGraphics.drawString(font, "X", fieldX + 100, y, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
            guiGraphics.drawString(font, "Y", fieldX + 158, y, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
            guiGraphics.drawString(font, "Z", fieldX + 216, y, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
            row++;
        }
    }

    // Draw the preset grid
    private void renderPresetGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        CTCreateScreenHelper.renderSeparator(guiGraphics, leftPos + 14, topPos + 28, PRESET_PANEL_W - 28);
        guiGraphics.drawString(font, Component.translatable("createthrusters.pose_screen.presets_title"),
                leftPos + 18, topPos + 38, CTCreateScreenHelper.LABEL_COLOR, false);
        guiGraphics.drawString(font, Component.translatable("createthrusters.pose_screen.preset_name"),
                leftPos + 154, topPos + PRESET_PANEL_H - 36, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
        renderPresetNameBackdrop(guiGraphics, mouseX, mouseY);

        List<PresetEntry> entries = presetEntries();
        int startIndex = presetScrollRow * GRID_COLS;
        int endIndex = Math.min(entries.size(), startIndex + GRID_COLS * GRID_ROWS);
        for (int idx = startIndex; idx < endIndex; idx++) {
            PresetEntry entry = entries.get(idx);
            int visibleIndex = idx - startIndex;
            int col = visibleIndex % GRID_COLS;
            int row = visibleIndex / GRID_COLS;
            int cellX = gridX() + col * GRID_CELL_W;
            int cellY = gridY() + row * GRID_CELL_H;
            boolean hovered = inside(mouseX, mouseY, cellX + 2, cellY + 2, GRID_CELL_W - 6, GRID_CELL_H - 5);
            CTCreateScreenHelper.renderInset(guiGraphics, cellX + 2, cellY + 2,
                    GRID_CELL_W - 6, GRID_CELL_H - 5, hovered, false);
            if (entry.custom()) {
                guiGraphics.drawString(font, Component.translatable("createthrusters.pose_screen.custom"),
                        cellX + 6, cellY + 6, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
            }
            renderPresetPreview(guiGraphics, entry.preset(), cellX + GRID_CELL_W / 2, cellY + 47);
            drawFittedCenteredString(guiGraphics, entry.preset().displayName(),
                    cellX + GRID_CELL_W / 2, cellY + 52, GRID_CELL_W - 12,
                    hovered ? CTCreateScreenHelper.VALUE_COLOR : CTCreateScreenHelper.LABEL_COLOR);
        }

        if (entries.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("createthrusters.pose_screen.no_presets"),
                    leftPos + PRESET_PANEL_W / 2, topPos + 132, CTCreateScreenHelper.SUBTLE_TEXT_COLOR);
        }

        if (maxPresetScrollRow() > 0) {
            Component scroll = Component.literal((presetScrollRow + 1) + " / " + (maxPresetScrollRow() + 1));
            guiGraphics.drawCenteredString(font, scroll, leftPos + PRESET_PANEL_W - 20, topPos + 134,
                    CTCreateScreenHelper.SUBTLE_TEXT_COLOR);
        }
    }

    // Draw the field backdrops
    private void renderFieldBackdrops(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (EditBox box : allFields) {
            CTCreateScreenHelper.renderInset(guiGraphics, box.getX() - 1, box.getY() - 1,
                    box.getWidth() + 2, box.getHeight() + 2,
                    inside(mouseX, mouseY, box.getX(), box.getY(), box.getWidth(), box.getHeight()), box.isFocused());
        }
        renderPresetNameBackdrop(guiGraphics, mouseX, mouseY);
    }

    // Draw the preset name backdrop
    private void renderPresetNameBackdrop(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (presetNameField == null) {
            return;
        }
        CTCreateScreenHelper.renderInset(guiGraphics, presetNameField.getX() - 1, presetNameField.getY() - 1,
                presetNameField.getWidth() + 2, presetNameField.getHeight() + 2,
                inside(mouseX, mouseY, presetNameField.getX(), presetNameField.getY(),
                        presetNameField.getWidth(), presetNameField.getHeight()), presetNameField.isFocused());
    }

    // Draw the status
    private void renderStatus(GuiGraphics guiGraphics) {
        if (statusTicks <= 0 || statusMessage == null || statusMessage.getString().isBlank()) {
            return;
        }
        if (viewMode == ViewMode.EDITOR) {
            drawFittedCenteredString(guiGraphics, statusMessage, width / 2, 12,
                    Math.max(120, width - 40), CTCreateScreenHelper.VALUE_COLOR);
            return;
        }
        drawFittedCenteredString(guiGraphics, statusMessage, leftPos + PRESET_PANEL_W / 2, topPos + PRESET_PANEL_H - 43,
                PRESET_PANEL_W - 40, CTCreateScreenHelper.VALUE_COLOR);
    }

    // Draw the preset preview
    private void renderPresetPreview(GuiGraphics guiGraphics, ArmorStandPosePreset preset, int x, int y) {
        ArmorStand preview = previewEntity();
        if (preview == null) {
            return;
        }
        syncPreviewVariant(preview);
        ArmorStandPoseData previewData = new ArmorStandPoseData();
        previewData.noBasePlate = true;
        previewData.showArms = true;
        previewData.applyPreset(preset);
        ArmorStandPoseData.applyAllowedTag(preview, previewData.toTag());
        resetPreviewFacing(preview);
        InventoryScreen.renderEntityInInventory(guiGraphics, x, y, 21.0F,
                PREVIEW_TRANSLATION, PREVIEW_ROTATION, PREVIEW_CAMERA_ROTATION, preview);
    }

    // Get the preview entity
    private ArmorStand previewEntity() {
        if (previewEntity != null) {
            return previewEntity;
        }
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        try {
            PlayerMannequinEntity mannequin = CTEntityTypes.PLAYER_MANNEQUIN.get().create(minecraft.level);
            if (mannequin != null) {
                mannequin.applyMannequinDefaults();
                previewEntity = mannequin;
                return previewEntity;
            }
        } catch (RuntimeException ignored) {
        }
        previewEntity = EntityType.ARMOR_STAND.create(minecraft.level);
        return previewEntity;
    }

    // Sync the preview variant
    private void syncPreviewVariant(ArmorStand preview) {
        if (armorStand instanceof PlayerMannequinEntity src && preview instanceof PlayerMannequinEntity target) {
            target.setVariant(src.getVariant());
        }
    }

    // Reset the preview facing
    private static void resetPreviewFacing(ArmorStand preview) {
        preview.setYRot(180.0F);
        preview.yRotO = 180.0F;
        preview.setXRot(0.0F);
        preview.xRotO = 0.0F;
        preview.yBodyRot = 180.0F;
        preview.yBodyRotO = 180.0F;
        preview.yHeadRot = 180.0F;
        preview.yHeadRotO = 180.0F;
    }

    // Add the toggle
    private void addToggle(String labelKey, BooleanSupplier getter, Consumer<Boolean> setter, int x, int y) {
        CTStyledButton btn = addStyledButton(x, y, 86, BUTTON_H, toggleMessage(labelKey, getter.getAsBoolean()), pressed -> {
            setter.accept(!getter.getAsBoolean());
            pressed.setMessage(toggleMessage(labelKey, getter.getAsBoolean()));
            updatePreviewFromWidgets();
        });
        toggleControls.add(new ToggleControl(labelKey, getter, btn));
    }

    // Add the styled button
    private CTStyledButton addStyledButton(int x, int y, int width, int height, Component msg, CTStyledButton.OnPress onPress) {
        return addRenderableWidget(new CTStyledButton(scalableGui, x, y, width, height, msg, onPress));
    }

    // Add the number field
    private EditBox addNumberField(int x, int y, int width, String val, String label) {
        EditBox box = new CTScaledEditBox(scalableGui, font, x, y, width, FIELD_H, Component.literal(label));
        box.setMaxLength(8);
        box.setBordered(false);
        box.setFilter(text -> text.isEmpty() || text.matches("-?\\d{0,4}(\\.\\d{0,2})?"));
        box.setValue(val);
        box.setResponder(ignored -> {
            if (!updatingWidgets) {
                updatePreviewFromWidgets();
            }
        });
        allFields.add(box);
        addRenderableWidget(box);
        return box;
    }

    // Update the preview from widgets
    private void updatePreviewFromWidgets() {
        readWidgets();
        ArmorStandPoseData.applyAllowedTag(armorStand, data.toTag());
    }

    // Read the widgets
    private void readWidgets() {
        if (rotationField != null) {
            data.rotation = parse(rotationField, data.rotation);
        }
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            EditBox[] boxes = poseFields.get(part);
            if (boxes == null) {
                continue;
            }
            float[] current = data.pose(part);
            data.setPose(part,
                    parse(boxes[0], current[0]),
                    parse(boxes[1], current[1]),
                    parse(boxes[2], current[2]));
        }
    }

    // Write the widgets
    private void writeWidgets() {
        if (rotationField == null) {
            return;
        }
        updatingWidgets = true;
        rotationField.setValue(format(data.rotation));
        for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
            EditBox[] boxes = poseFields.get(part);
            float[] pose = data.pose(part);
            if (boxes != null) {
                boxes[0].setValue(format(pose[0]));
                boxes[1].setValue(format(pose[1]));
                boxes[2].setValue(format(pose[2]));
            }
        }
        refreshToggleMessages();
        updatingWidgets = false;
    }

    // Refresh the toggle messages
    private void refreshToggleMessages() {
        for (ToggleControl control : toggleControls) {
            control.button().setMessage(toggleMessage(control.labelKey(), control.getter().getAsBoolean()));
        }
    }

    // Apply the selected preset
    private void applySelectedPreset(ArmorStandPosePreset preset) {
        if (preset.randomizes()) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (ArmorStandPosePart part : ArmorStandPosePart.values()) {
                data.setPose(part, randomAngle(random), randomAngle(random), randomAngle(random));
            }
            data.showArms = true;
        } else {
            data.applyPreset(preset);
        }
        writeWidgets();
        ArmorStandPoseData.applyAllowedTag(armorStand, data.toTag());
    }

    // Reset the preview
    private void resetPreview() {
        ArmorStandPoseData.applyAllowedTag(armorStand, closeRestoreTag.copy());
        data = ArmorStandPoseData.fromEntity(armorStand);
        writeWidgets();
    }

    // Send the pose
    private void sendPose() {
        readWidgets();
        CompoundTag tag = data.toTag();
        closeRestoreTag = tag.copy();
        PacketDistributor.sendToServer(new ArmorStandPoseSyncPayload(armorStand.getId(), tag));
        setStatus(Component.translatable("createthrusters.pose_screen.status.applied"));
    }

    // Open the presets
    private void openPresets() {
        readWidgets();
        presetScrollRow = Mth.clamp(presetScrollRow, 0, maxPresetScrollRow());
        viewMode = ViewMode.PRESETS;
        rebuildPoseWidgets();
    }

    // Open the editor
    private void openEditor() {
        viewMode = ViewMode.EDITOR;
        rebuildPoseWidgets();
    }

    // Save the current preset
    private void saveCurrentPreset() {
        readWidgets();
        String name = presetNameField == null ? presetName : presetNameField.getValue();
        if (name == null || name.isBlank()) {
            name = Component.translatable("createthrusters.pose_screen.default_preset_name").getString();
        }
        try {
            Path saved = ArmorStandPosePresetStore.saveCustomPreset(name, data);
            presetName = "";
            if (presetNameField != null) {
                presetNameField.setValue("");
            }
            reloadCustomPresets(false);
            setStatus(Component.translatable("createthrusters.pose_screen.status.saved", saved.getFileName().toString()));
        } catch (IOException ignored) {
            setStatus(Component.translatable("createthrusters.pose_screen.status.save_failed"));
        }
    }

    // Hide the poser
    private void hidePoser() {
        ArmorStandPoseData.applyAllowedTag(armorStand, closeRestoreTag.copy());
        ArmorStandPoseClientState.hideForOtherGui(armorStand.getId());
        minecraft.setScreen(null);
    }

    // Reload the custom presets
    private void reloadCustomPresets(boolean showStatus) {
        ArmorStandPosePresetStore.LoadResult res = ArmorStandPosePresetStore.loadCustomPresets();
        customPresets.clear();
        customPresets.addAll(res.presets());
        presetScrollRow = Mth.clamp(presetScrollRow, 0, maxPresetScrollRow());
        if (!showStatus) {
            return;
        }
        if (res.failed() > 0) {
            setStatus(Component.translatable("createthrusters.pose_screen.status.loaded_failed",
                    res.presets().size(), res.failed()));
        } else {
            setStatus(Component.translatable("createthrusters.pose_screen.status.loaded", res.presets().size()));
        }
    }

    // Handle the preset grid click
    private boolean clickPresetGrid(double mouseX, double mouseY) {
        if (!insidePresetGrid(mouseX, mouseY)) {
            return false;
        }
        int col = (int) ((mouseX - gridX()) / GRID_CELL_W);
        int row = (int) ((mouseY - gridY()) / GRID_CELL_H);
        if (col < 0 || col >= GRID_COLS || row < 0 || row >= GRID_ROWS) {
            return false;
        }
        int idx = (presetScrollRow + row) * GRID_COLS + col;
        List<PresetEntry> entries = presetEntries();
        if (idx < 0 || idx >= entries.size()) {
            return false;
        }
        ArmorStandPosePreset preset = entries.get(idx).preset();
        applySelectedPreset(preset);
        setStatus(Component.translatable("createthrusters.pose_screen.status.preset_applied", preset.displayName()));
        openEditor();
        return true;
    }

    // Check if the point is inside the bounds preset grid
    private boolean insidePresetGrid(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, gridX(), gridY(), GRID_COLS * GRID_CELL_W, GRID_ROWS * GRID_CELL_H);
    }

    // Get the preset entries
    private List<PresetEntry> presetEntries() {
        List<PresetEntry> entries = new ArrayList<>(ArmorStandPosePreset.DEFAULTS.size() + customPresets.size());
        for (ArmorStandPosePreset preset : ArmorStandPosePreset.DEFAULTS) {
            entries.add(new PresetEntry(preset, false));
        }
        for (ArmorStandPosePreset preset : customPresets) {
            entries.add(new PresetEntry(preset, true));
        }
        return entries;
    }

    // Get the maximum preset scroll row
    private int maxPresetScrollRow() {
        int rows = (presetEntries().size() + GRID_COLS - 1) / GRID_COLS;
        return Math.max(0, rows - GRID_ROWS);
    }

    // Get the grid x
    private int gridX() {
        return leftPos + 18;
    }

    // Get the grid y
    private int gridY() {
        return topPos + 54;
    }

    // Set the status
    private void setStatus(Component msg) {
        statusMessage = msg;
        statusTicks = 100;
    }

    // Draw the fitted centered string
    private void drawFittedCenteredString(GuiGraphics guiGraphics, Component text, int centerX, int y,
                                          int maxWidth, int col) {
        int width = font.width(text);
        float scale = width <= 0 ? 1.0F : Mth.clamp(maxWidth / (float) width, 0.55F, 1.0F);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawCenteredString(font, text, 0, 0, col);
        guiGraphics.pose().popPose();
    }

    // Build the pose toggle message
    private static Component toggleMessage(String labelKey, boolean val) {
        return Component.translatable("createthrusters.pose_screen.toggle",
                Component.translatable(labelKey),
                Component.translatable(val ? "gui.createthrusters.on" : "gui.createthrusters.off"));
    }

    // Parse the armor stand pose
    private static float parse(EditBox box, float fallback) {
        try {
            return Float.parseFloat(box.getValue());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    // Format the armor stand pose
    private static String format(float val) {
        if (Math.abs(val - Math.round(val)) < 0.001F) {
            return Integer.toString(Math.round(val));
        }
        return String.format(Locale.ROOT, "%.2f", val);
    }

    // Get the random angle
    private static float randomAngle(ThreadLocalRandom random) {
        return (float) (random.nextDouble() * 70.0D - 35.0D);
    }

    // Check if the point is inside the bounds
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    // Define the view mode values
    private enum ViewMode {
        EDITOR,
        PRESETS
    }

    // Store the preset entry
    private record PresetEntry(ArmorStandPosePreset preset, boolean custom) {
    }

    // Store the toggle control
    private record ToggleControl(String labelKey, BooleanSupplier getter, CTStyledButton button) {
    }
}

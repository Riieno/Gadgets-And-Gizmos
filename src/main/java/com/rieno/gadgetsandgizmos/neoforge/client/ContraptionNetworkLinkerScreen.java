package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSyncPayload;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Edit Contraption Network Linker settings
public class ContraptionNetworkLinkerScreen extends AbstractSimiScreen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int BG_W = 296;
    private static final int BG_H = 214;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();

    // Hand
    private final InteractionHand hand;
    // Stack snapshot
    private final ItemStack stackSnapshot;
    // Tracks whether client data is available
    private final boolean clientDataAvailable;

    // Tracked targets
    private final List<ContraptionNetworkLinkerData.LinkedTarget> targets = new ArrayList<>();
    // Current edit mode
    private ContraptionNetworkLinkerData.LinkMode editMode = ContraptionNetworkLinkerData.LinkMode.OUTPUT;
    // Target mode
    private ContraptionNetworkLinkerData.TargetMode targetMode = ContraptionNetworkLinkerData.TargetMode.AUTO;

    // Current block label field
    private EditBox blockLabelField;
    // Current face label field
    private EditBox faceLabelField;
    // Target mode button
    private CTScaledButton targetModeButton;

    // Current left pos
    private int leftPos;
    // Current top pos
    private int topPos;
    // Current scroll
    private int scroll;
    // Selected target
    private int selectedTarget = -1;
    // Selected face
    private int selectedFace = -1;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker
    public ContraptionNetworkLinkerScreen(InteractionHand hand, ItemStack stack) {
        super(Component.translatable("item.createthrusters.contraption_network_linker"));
        this.hand = hand;
        this.stackSnapshot = stack.copy();
        this.clientDataAvailable = ContraptionNetworkLinkerData.hasClientData(stack);
        this.targets.addAll(ContraptionNetworkLinkerData.readClientTargets(stack));
        this.editMode = ContraptionNetworkLinkerData.getClientEditMode(stack);
        this.targetMode = ContraptionNetworkLinkerData.getClientTargetMode(stack);
        setWindowSize(BG_W, BG_H);
    }

    // Initialize the contraption network linker
    @Override
    protected void init() {
        super.init();
        leftPos = guiLeft;
        topPos = guiTop;
        scalableGui.update(leftPos, topPos, BG_W, BG_H, width, height);

        blockLabelField = new CTScaledEditBox(scalableGui, font, leftPos + 14, topPos + 168, 124, 14,
                Component.translatable("item.createthrusters.contraption_network_linker.block_label"));
        faceLabelField = new CTScaledEditBox(scalableGui, font, leftPos + 146, topPos + 168, 124, 14,
                Component.translatable("item.createthrusters.contraption_network_linker.face_label"));
        addRenderableWidget(blockLabelField);
        addRenderableWidget(faceLabelField);

        addRenderableWidget(new CTScaledButton(scalableGui, leftPos + BG_W - 48, topPos + BG_H - 20, 40, 14,
                Component.translatable("gui.done"), btn -> {
            applyFieldEdits();
            onClose();
        }));

        addRenderableWidget(new CTScaledButton(scalableGui, leftPos + 8, topPos + BG_H - 20, 44, 14,
                Component.translatable("item.createthrusters.contraption_network_linker.clear"), btn -> {
            targets.removeIf(target -> target.mode() == editMode);
            selectedTarget = -1;
            selectedFace = -1;
            refreshFields();
        }));

        addRenderableWidget(new CTScaledButton(scalableGui, leftPos + 56, topPos + BG_H - 20, 52, 14,
                Component.translatable("item.createthrusters.contraption_network_linker.remove"), btn -> {
            removeSelectedEntry();
        }));

        addTargetTab(ContraptionNetworkLinkerData.LinkMode.INPUT, 78, "INPUT");
        addTargetTab(ContraptionNetworkLinkerData.LinkMode.OUTPUT, 132, "OUTPUT");
        addTargetTab(ContraptionNetworkLinkerData.LinkMode.SCM, 192, "SCM");

        targetModeButton = addRenderableWidget(new CTScaledButton(scalableGui, leftPos + 196, topPos + BG_H - 20, 48, 14,
                targetModeLabel(), btn -> {
            targetMode = targetMode.next();
            btn.setMessage(targetModeLabel());
        }));

        addRenderableWidget(new CTScaledButton(scalableGui, leftPos + BG_W - 22, topPos + 20, 14, 12,
                Component.literal("^"), btn -> {
            scroll = Math.max(0, scroll - 1);
        }));

        addRenderableWidget(new CTScaledButton(scalableGui, leftPos + BG_W - 22, topPos + 146, 14, 12,
                Component.literal("v"), btn -> {
            scroll = Math.min(maxScroll(), scroll + 1);
        }));

        refreshFields();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        applyFieldEdits();
        if (insideList(mouseX, mouseY)) {
            int row = (int) ((mouseY - (topPos + 34)) / 12);
            int absolute = scroll + row;
            selectByAbsoluteRow(absolute);
            refreshFields();
            return true;
        }
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Handle the close event
    @Override
    public void onClose() {
        applyFieldEdits();
        if (clientDataAvailable) {
            PacketDistributor.sendToServer(new ContraptionNetworkLinkerSyncPayload(hand,
                    ContraptionNetworkLinkerData.writeClientEditRoot(
                            stackSnapshot, targets, editMode, targetMode)));
        }
        super.onClose();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a pause screen
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Draw the contraption network linker
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fillGradient(0, 0, width, height, 0x22000000, 0x44000000);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    // Draw the window
    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(graphics);
        try {
        CTCreateScreenHelper.renderPanel(graphics, leftPos, topPos, BG_W, BG_H);
        CTCreateScreenHelper.renderSeparator(graphics, leftPos + 8, topPos + 30, BG_W - 16);
        CTCreateScreenHelper.renderInset(graphics, leftPos + 10, topPos + 34, BG_W - 36, 124, false, false);
        CTCreateScreenHelper.renderInset(graphics, leftPos + 10, topPos + 164, BG_W - 20, 22, false, false);

        graphics.drawString(font, title, leftPos + 12, topPos + 8, CTCreateScreenHelper.BANNER_TITLE_COLOR, false);
        graphics.drawString(font, Component.literal("Targets"),
            leftPos + 12, topPos + 20, CTCreateScreenHelper.LABEL_COLOR, false);
        int tabLeft = switch (editMode) {
            case INPUT -> leftPos + 78;
            case OUTPUT -> leftPos + 132;
            case SCM -> leftPos + 192;
        };
        int tabWidth = editMode == ContraptionNetworkLinkerData.LinkMode.OUTPUT ? 56 : 50;
        int tabColor = editMode == ContraptionNetworkLinkerData.LinkMode.SCM ? 0xFF42C96B : 0xFF74A6D8;
        graphics.fill(tabLeft + 2, topPos + 29, tabLeft + tabWidth - 2, topPos + 31, tabColor);
        drawRows(graphics);
        } finally {
            scalableGui.pop(graphics);
        }
    }

    // Draw the rows
    private void drawRows(GuiGraphics graphics) {
        List<RowEntry> rows = buildRows();
        int visible = 10;
        int startY = topPos + 34;
        int end = Math.min(rows.size(), scroll + visible);
        for (int idx = scroll; idx < end; idx++) {
            RowEntry row = rows.get(idx);
            int y = startY + (idx - scroll) * 12;
            if (row.isSelected(selectedTarget, selectedFace)) {
                graphics.fill(leftPos + 12, y - 1, leftPos + BG_W - 28, y + 10, 0x334A7A5A);
            }
            int col = row.isSelected(selectedTarget, selectedFace)
                    ? 0xE9F3EA
                    : (row.faceIndex >= 0 ? CTCreateScreenHelper.LABEL_COLOR : CTCreateScreenHelper.BANNER_TITLE_COLOR);
            graphics.drawString(font, row.text, leftPos + 14, y, col, false);
        }
    }

    // Select a linker entry by absolute row
    private void selectByAbsoluteRow(int absoluteRow) {
        List<RowEntry> rows = buildRows();
        if (absoluteRow < 0 || absoluteRow >= rows.size()) {
            return;
        }
        RowEntry row = rows.get(absoluteRow);
        selectedTarget = row.targetIndex;
        selectedFace = row.faceIndex;
        if (selectedTarget >= 0 && selectedTarget < targets.size()) {
            editMode = targets.get(selectedTarget).mode();
        }
    }

    // Remove the selected entry
    private void removeSelectedEntry() {
        if (selectedTarget < 0 || selectedTarget >= targets.size()) {
            return;
        }
        if (selectedFace < 0) {
            targets.remove(selectedTarget);
            selectedTarget = -1;
            selectedFace = -1;
            scroll = Math.min(scroll, maxScroll());
            refreshFields();
            return;
        }

        ContraptionNetworkLinkerData.LinkedTarget target = targets.get(selectedTarget);
        if (selectedFace >= target.faces().size()) {
            return;
        }
        List<ContraptionNetworkLinkerData.LinkedFace> faces = new ArrayList<>(target.faces());
        faces.remove(selectedFace);
        if (faces.isEmpty()) {
            targets.remove(selectedTarget);
            selectedTarget = -1;
            selectedFace = -1;
        } else {
            targets.set(selectedTarget, new ContraptionNetworkLinkerData.LinkedTarget(target.blockPos(), target.subLevelId(),
                    target.blockId(), target.label(), target.mode(), target.scope(), faces));
            selectedFace = Mth.clamp(selectedFace, 0, faces.size() - 1);
        }
        scroll = Math.min(scroll, maxScroll());
        refreshFields();
    }

    // Apply the field edits
    private void applyFieldEdits() {
        if (selectedTarget < 0 || selectedTarget >= targets.size()) {
            return;
        }
        ContraptionNetworkLinkerData.LinkedTarget target = targets.get(selectedTarget);
        List<ContraptionNetworkLinkerData.LinkedFace> faces = new ArrayList<>(target.faces());
        String blockLabel = blockLabelField.getValue();
        if (selectedFace >= 0 && selectedFace < faces.size()) {
            ContraptionNetworkLinkerData.LinkedFace face = faces.get(selectedFace);
            faces.set(selectedFace, new ContraptionNetworkLinkerData.LinkedFace(
                    face.face(),
                    faceLabelField.getValue(),
                    face.signalKey()));
        }
        targets.set(selectedTarget, new ContraptionNetworkLinkerData.LinkedTarget(target.blockPos(), target.subLevelId(),
                target.blockId(), blockLabel, target.mode(), target.scope(), faces));
    }

    // Refresh the fields
    private void refreshFields() {
        if (selectedTarget < 0 || selectedTarget >= targets.size()) {
            blockLabelField.setValue("");
            faceLabelField.setValue("");
            return;
        }
        ContraptionNetworkLinkerData.LinkedTarget target = targets.get(selectedTarget);
        blockLabelField.setValue(target.label());
        if (selectedFace >= 0 && selectedFace < target.faces().size()) {
            faceLabelField.setValue(target.faces().get(selectedFace).label());
        } else {
            faceLabelField.setValue("");
        }
    }

    // Check if this is inside list
    private boolean insideList(double mouseX, double mouseY) {
        return mouseX >= leftPos + 10 && mouseX <= leftPos + BG_W - 26
                && mouseY >= topPos + 34 && mouseY <= topPos + 154;
    }

    // Get the maximum scroll
    private int maxScroll() {
        int max = buildRows().size() - 10;
        return Math.max(0, max);
    }

    // Get the mode label
    private Component modeLabel() {
        return Component.translatable("item.createthrusters.contraption_network_linker.mode_button",
                editMode.id().toUpperCase(Locale.ROOT));
    }

    // Add the target tab
    private void addTargetTab(ContraptionNetworkLinkerData.LinkMode mode,
                              int xOffset, String label) {
        addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + xOffset, topPos + 17, mode == ContraptionNetworkLinkerData.LinkMode.OUTPUT ? 56 : 50, 14,
                Component.literal(label), btn -> selectTab(mode)));
    }

    // Select the tab
    private void selectTab(ContraptionNetworkLinkerData.LinkMode mode) {
        applyFieldEdits();
        editMode = mode;
        if (targetModeButton != null) {
            targetModeButton.setMessage(targetModeLabel());
        }
        selectedTarget = -1;
        selectedFace = -1;
        scroll = 0;
        refreshFields();
    }

    // Get the target mode label
    private Component targetModeLabel() {
        return Component.literal(targetMode.id().toUpperCase(Locale.ROOT));
    }

    // Build the rows
    private List<RowEntry> buildRows() {
        List<RowEntry> rows = new ArrayList<>();
        for (int targetIndex = 0; targetIndex < targets.size(); targetIndex++) {
            ContraptionNetworkLinkerData.LinkedTarget target = targets.get(targetIndex);
            if (target.mode() != editMode) {
                continue;
            }
            String title = (target.label().isBlank() ? target.blockId() : target.label())
                    + " [" + target.mode().id().toUpperCase(Locale.ROOT) + "]";
            rows.add(new RowEntry(targetIndex, -1, title));
            for (int faceIndex = 0; faceIndex < target.faces().size(); faceIndex++) {
                ContraptionNetworkLinkerData.LinkedFace face = target.faces().get(faceIndex);
                String faceLabel = face.label().isBlank()
                        ? face.face().getSerializedName()
                        : face.label() + " (" + face.face().getSerializedName() + ")";
                rows.add(new RowEntry(targetIndex, faceIndex, "  - " + faceLabel));
            }
        }
        return rows;
    }

    // Store the row entry
    private record RowEntry(int targetIndex, int faceIndex, String text) {
        // Check if this is selected
        private boolean isSelected(int selectedTarget, int selectedFace) {
            return targetIndex == selectedTarget && faceIndex == selectedFace;
        }
    }
}

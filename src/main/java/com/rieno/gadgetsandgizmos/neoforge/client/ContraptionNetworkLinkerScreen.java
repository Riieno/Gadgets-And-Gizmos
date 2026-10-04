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

// Edit Contraption Network Linker settings
public class ContraptionNetworkLinkerScreen extends AbstractSimiScreen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int BG_W = 296;
    private static final int BG_H = 258;

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
    private final List<ContraptionNetworkLinkerData.LinkedArea> areas = new ArrayList<>();
    // Current edit mode
    private ContraptionNetworkLinkerData.LinkMode editMode = ContraptionNetworkLinkerData.LinkMode.OUTPUT;
    private ContraptionNetworkLinkerData.TargetMode targetMode = ContraptionNetworkLinkerData.TargetMode.FACE;
    private CTScaledButton faceModeButton;
    private CTScaledButton blockModeButton;
    private CTScaledButton areaModeButton;
    private CTScaledButton machineInputButton;
    private CTScaledButton machineOutputButton;
    private CTScaledButton noEntryButton;
    private CTScaledButton assignRecipesButton;

    // Current block label field
    private EditBox blockLabelField;
    // Current face label field
    private EditBox faceLabelField;
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
    private int selectedArea = -1;

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
        this.areas.addAll(ContraptionNetworkLinkerData.readClientAreas(stack));
        this.editMode = ContraptionNetworkLinkerData.getClientEditMode(stack);
        this.targetMode = ContraptionNetworkLinkerData.getClientTargetMode(stack);
        if (this.targetMode == ContraptionNetworkLinkerData.TargetMode.AUTO) {
            this.targetMode = ContraptionNetworkLinkerData.TargetMode.FACE;
        }
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
        assignRecipesButton = addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + 146, topPos + 168, 124, 14, Component.literal("Assign recipes"),
                btn -> openRecipePicker()));

        addRenderableWidget(new CTScaledButton(scalableGui, leftPos + BG_W - 48, topPos + BG_H - 20, 40, 14,
                Component.translatable("gui.done"), btn -> {
            applyFieldEdits();
            onClose();
        }));

        addRenderableWidget(new CTScaledButton(scalableGui, leftPos + 8, topPos + BG_H - 20, 44, 14,
                Component.translatable("item.createthrusters.contraption_network_linker.clear"), btn -> {
            targets.removeIf(target -> target.mode() == editMode);
            if(editMode == ContraptionNetworkLinkerData.LinkMode.SCM) areas.clear();
            selectedTarget = -1;
            selectedFace = -1;
            selectedArea = -1;
            refreshFields();
        }));

        addRenderableWidget(new CTScaledButton(scalableGui, leftPos + 56, topPos + BG_H - 20, 52, 14,
                Component.translatable("item.createthrusters.contraption_network_linker.remove"), btn -> {
            removeSelectedEntry();
        }));

        addTargetTab(ContraptionNetworkLinkerData.LinkMode.INPUT, 78);
        addTargetTab(ContraptionNetworkLinkerData.LinkMode.OUTPUT, 132);
        addTargetTab(ContraptionNetworkLinkerData.LinkMode.SCM, 192);
        faceModeButton = addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + 8, topPos + BG_H - 64, 90, 16,
                Component.translatable("item.createthrusters.contraption_network_linker.target.face"), btn -> {
            targetMode = ContraptionNetworkLinkerData.TargetMode.FACE;
            refreshModeButtons();
        }));
        blockModeButton = addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + 103, topPos + BG_H - 64, 90, 16,
                Component.translatable("item.createthrusters.contraption_network_linker.target.block"), btn -> {
            targetMode = ContraptionNetworkLinkerData.TargetMode.BLOCK;
            refreshModeButtons();
        }));
        areaModeButton = addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + 198, topPos + BG_H - 64, 90, 16,
                Component.literal("Machine Area"), btn -> {
            targetMode = ContraptionNetworkLinkerData.TargetMode.AREA;
            refreshModeButtons();
        }));
        machineInputButton = addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + 8, topPos + BG_H - 42, 73, 16, Component.literal("Input"), btn -> {
            targetMode = ContraptionNetworkLinkerData.TargetMode.MACHINE_INPUT;
            refreshModeButtons();
        }));
        machineOutputButton = addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + 85, topPos + BG_H - 42, 73, 16, Component.literal("Output"), btn -> {
            targetMode = ContraptionNetworkLinkerData.TargetMode.MACHINE_OUTPUT;
            refreshModeButtons();
        }));
        noEntryButton = addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + 162, topPos + BG_H - 42, 73, 16, Component.literal("No Entry"), btn -> {
            targetMode = ContraptionNetworkLinkerData.TargetMode.NO_ENTRY;
            refreshModeButtons();
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
        refreshModeButtons();
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
                            stackSnapshot, targets, areas, editMode, targetMode)));
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
            if (row.isSelected(selectedTarget, selectedFace, selectedArea)) {
                graphics.fill(leftPos + 12, y - 1, leftPos + BG_W - 28, y + 10, 0x334A7A5A);
            }
            int col = row.isSelected(selectedTarget, selectedFace, selectedArea)
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
        selectedArea = row.areaIndex;
        if (selectedTarget >= 0 && selectedTarget < targets.size()) {
            editMode = targets.get(selectedTarget).mode();
        }
        if(selectedArea >= 0) editMode = ContraptionNetworkLinkerData.LinkMode.SCM;
        refreshModeButtons();
    }

    // Remove the selected entry
    private void removeSelectedEntry() {
        if(selectedArea >= 0 && selectedArea < areas.size()){
            areas.remove(selectedArea);
            selectedArea = -1;
            scroll = Math.min(scroll, maxScroll());
            refreshFields();
            return;
        }
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
        if(selectedArea >= 0 && selectedArea < areas.size()){
            var area = areas.get(selectedArea);
            areas.set(selectedArea, new ContraptionNetworkLinkerData.LinkedArea(area.id(), area.subLevelId(),
                    area.bounds(), blockLabelField.getValue(), area.recipeId(), area.kind(),
                    area.ports(), area.recipeIds()));
            return;
        }
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
        if(selectedArea >= 0 && selectedArea < areas.size()){
            blockLabelField.setValue(areas.get(selectedArea).label());
            faceLabelField.setValue("");
            faceLabelField.visible = false;
            assignRecipesButton.visible = areas.get(selectedArea).kind()
                    == ContraptionNetworkLinkerData.AreaKind.MACHINE;
            return;
        }
        faceLabelField.visible = true;
        assignRecipesButton.visible = false;
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
                Component.translatable(editMode.translationKey()));
    }

    // Add the target tab
    private void addTargetTab(ContraptionNetworkLinkerData.LinkMode mode, int xOffset) {
        addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + xOffset, topPos + 17, mode == ContraptionNetworkLinkerData.LinkMode.OUTPUT ? 56 : 50, 14,
                Component.translatable(mode.translationKey()), btn -> selectTab(mode)));
    }

    // Select the tab
    private void selectTab(ContraptionNetworkLinkerData.LinkMode mode) {
        applyFieldEdits();
        editMode = mode;
        selectedTarget = -1;
        selectedFace = -1;
        selectedArea = -1;
        scroll = 0;
        refreshFields();
        refreshModeButtons();
    }

    // Show the SCM target choices on the linker toolbar
    private void refreshModeButtons(){
        if(faceModeButton == null || blockModeButton == null || areaModeButton == null) return;
        boolean scm = editMode == ContraptionNetworkLinkerData.LinkMode.SCM;
        faceModeButton.visible = scm;
        blockModeButton.visible = scm;
        areaModeButton.visible = scm;
        machineInputButton.visible = scm;
        machineOutputButton.visible = scm;
        noEntryButton.visible = scm;
        faceModeButton.active = scm && targetMode != ContraptionNetworkLinkerData.TargetMode.FACE;
        blockModeButton.active = scm && targetMode != ContraptionNetworkLinkerData.TargetMode.BLOCK;
        areaModeButton.active = scm && targetMode != ContraptionNetworkLinkerData.TargetMode.AREA;
        machineInputButton.active = scm && targetMode != ContraptionNetworkLinkerData.TargetMode.MACHINE_INPUT;
        machineOutputButton.active = scm && targetMode != ContraptionNetworkLinkerData.TargetMode.MACHINE_OUTPUT;
        noEntryButton.active = scm && targetMode != ContraptionNetworkLinkerData.TargetMode.NO_ENTRY;
    }

    private void openRecipePicker(){
        if(selectedArea < 0 || selectedArea >= areas.size()
                || areas.get(selectedArea).kind() != ContraptionNetworkLinkerData.AreaKind.MACHINE) return;
        applyFieldEdits();
        int areaIndex = selectedArea;
        minecraft.setScreen(new ContraptionNetworkLinkerRecipeScreen(this, areas.get(areaIndex).recipeIds(), ids -> {
            var area = areas.get(areaIndex);
            areas.set(areaIndex, new ContraptionNetworkLinkerData.LinkedArea(area.id(), area.subLevelId(),
                    area.bounds(), area.label(), ids.isEmpty() ? "" : ids.getFirst(),
                    area.kind(), area.ports(), ids));
        }));
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
                    + " [" + Component.translatable(target.mode().translationKey()).getString() + "]";
            rows.add(new RowEntry(targetIndex, -1, title));
            for (int faceIndex = 0; faceIndex < target.faces().size(); faceIndex++) {
                ContraptionNetworkLinkerData.LinkedFace face = target.faces().get(faceIndex);
                String faceLabel = face.label().isBlank()
                        ? face.face().getSerializedName()
                        : face.label() + " (" + face.face().getSerializedName() + ")";
                rows.add(new RowEntry(targetIndex, faceIndex, "  - " + faceLabel));
            }
        }
        if(editMode == ContraptionNetworkLinkerData.LinkMode.SCM){
            for(int areaIdx = 0; areaIdx < areas.size(); areaIdx++){
                var area = areas.get(areaIdx);
                String label = area.label().isBlank() ? "Machine Area" : area.label();
                rows.add(new RowEntry(-1, -1, areaIdx, label
                        + (area.kind() == ContraptionNetworkLinkerData.AreaKind.NO_ENTRY ? " [No Entry] " : " [Machine Area] ")
                        + area.bounds().min().toShortString() + " .. " + area.bounds().max().toShortString()));
            }
        }
        return rows;
    }

    // Store the row entry
    private record RowEntry(int targetIndex, int faceIndex, int areaIndex, String text) {
        private RowEntry(int targetIndex, int faceIndex, String text){
            this(targetIndex, faceIndex, -1, text);
        }
        // Check if this is selected
        private boolean isSelected(int selectedTarget, int selectedFace, int selectedArea) {
            return areaIndex >= 0 ? areaIndex == selectedArea
                    : targetIndex == selectedTarget && faceIndex == selectedFace && selectedArea < 0;
        }
    }
}

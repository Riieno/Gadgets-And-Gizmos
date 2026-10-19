package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.LayeredItemRenderer;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphTextLayout;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSyncPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

// Edit Contraption Network Linker settings
public class ContraptionNetworkLinkerScreen extends AbstractSimiScreen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int BG_W = 288;
    private static final int BG_H = 276;
    private static final int LIST_X = 22;
    private static final int LIST_Y = 80;
    private static final int LIST_W = 194;
    private static final int ROW_H = 16;
    private static final int VISIBLE_ROWS = 8;
    private static final int RENAME_X = LIST_X + LIST_W - 14;
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/linker_gui.png");
    private static final GuiTextureRegion PANEL = region(32, 36, 192, 184);
    private static final GuiTextureRegion RENAME = region(16, 240, 13, 13);
    private static final GuiTextureRegion RENAME_HOVER = region(0, 240, 13, 13);
    private static final GuiTextureRegion DONE = region(238, 238, 18, 18);
    private static final GuiTextureRegion ROW_DIVIDER = region(85, 242, 91, 2);
    private static final GuiTextureRegion[] FILTER_HIGHLIGHTS = {
            region(231, 102, 21, 18), region(231, 120, 21, 19), region(231, 139, 21, 18)
    };

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
    private ContraptionNetworkLinkerData.TargetMode targetMode = ContraptionNetworkLinkerData.TargetMode.FACE;
    private CTScaledButton faceModeButton;
    
    
    
    
    
    private final List<CTScaledButton> renameButtons = new ArrayList<>();

    // Label editor for one block or area row
    private EditBox inlineLabelField;
    private RowEntry editingRow;
    private final Map<ResourceLocation, ItemStack> blockIcons = new HashMap<>();
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
        applyFieldEdits();
        super.init();
        leftPos = guiLeft;
        topPos = guiTop;
        scalableGui.update(leftPos, topPos, BG_W, BG_H, width, height);

        inlineLabelField = new CTScaledEditBox(scalableGui, font,
                leftPos + LIST_X + 18, topPos + LIST_Y + 3, RENAME_X - LIST_X - 20, 10,
                Component.translatable("item.createthrusters.contraption_network_linker.block_label"));
        inlineLabelField.setBordered(false);
        inlineLabelField.setTextColor(0xE9F3EA);
        inlineLabelField.visible = false;
        inlineLabelField.active = false;
        addRenderableWidget(inlineLabelField);

        CTScaledButton doneButton = addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + atlasX(200), topPos + atlasY(198), atlasX(218) - atlasX(200),
                atlasY(216) - atlasY(198), Component.translatable("gui.done"), btn -> onClose(),
                (graphics, btn, hovered, partialTick) -> {
            DONE.draw(graphics, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight());
            if(hovered || btn.isFocused()){
                graphics.fill(btn.getX() + 1, btn.getY() + 1,
                        btn.getX() + btn.getWidth() - 1, btn.getY() + btn.getHeight() - 1, 0x22FFFFFF);
            }
        }));
        doneButton.setTooltip(Tooltip.create(doneButton.getMessage()));

        addFooterButton(8, 259, 46,
                Component.translatable("item.createthrusters.contraption_network_linker.clear"), () -> {
            applyFieldEdits();
            targets.removeIf(target -> target.mode() == editMode);
            selectedTarget = -1;
            selectedFace = -1;

            scroll = 0;
            refreshFields();
        });

        addFooterButton(58, 259, 58,
                Component.translatable("item.createthrusters.contraption_network_linker.remove"), this::removeSelectedEntry);

        addTargetTab(ContraptionNetworkLinkerData.LinkMode.INPUT, 0, 101);
        addTargetTab(ContraptionNetworkLinkerData.LinkMode.OUTPUT, 1, 120);
        addTargetTab(ContraptionNetworkLinkerData.LinkMode.SCM, 2, 139);
        faceModeButton = addFooterButton(120, 259, 116, targetModeLabel(), () -> {
            applyFieldEdits();
            targetMode = targetMode.next();
            refreshModeButtons();
        });

        addRenderableWidget(new CTScaledButton(scalableGui, leftPos + 220, topPos + LIST_Y, 10, 14,
                Component.literal("^"), btn -> {
            applyFieldEdits();
            scroll = Math.max(0, scroll - 1);
        }));

        addRenderableWidget(new CTScaledButton(scalableGui, leftPos + 220,
                topPos + LIST_Y + ROW_H * VISIBLE_ROWS - 14, 10, 14, Component.literal("v"), btn -> {
            applyFieldEdits();
            scroll = Math.min(maxScroll(), scroll + 1);
        }));

        renameButtons.clear();
        for(int idx = 0; idx < VISIBLE_ROWS; idx++){
            int row = idx;
            CTScaledButton button = addRenderableWidget(new CTScaledButton(scalableGui,
                    leftPos + RENAME_X, topPos + LIST_Y + idx * ROW_H + 1, 13, 13,
                    Component.translatable("item.createthrusters.contraption_network_linker.rename"),
                    btn -> renameRow(scroll + row), (graphics, btn, hovered, partialTick) -> {
                GuiTextureRegion icon = hovered || btn.isFocused() ? RENAME_HOVER : RENAME;
                icon.draw(graphics, btn.getX(), btn.getY());
            }));
            button.setTooltip(Tooltip.create(button.getMessage()));
            renameButtons.add(button);
        }

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
        if(editingRow != null && inlineLabelField.isMouseOver(mouseX, mouseY)){
            return super.mouseClicked(mouseX, mouseY, btn);
        }
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        applyFieldEdits();
        if(btn == 0 && insideList(mouseX, mouseY)){
            int row = (int)((mouseY - (topPos + LIST_Y)) / ROW_H);
            int absolute = scroll + row;
            List<RowEntry> rows = buildRows();
            if(absolute >= rows.size()) return true;
            if(mouseX >= leftPos + RENAME_X && rows.get(absolute).canRename()){
                renameButtons.get(row).onPress();
                renameButtons.get(row).playDownSound(minecraft.getSoundManager());
                return true;
            }
            setFocused(null);
            selectByAbsoluteRow(absolute);
            refreshFields();
            return true;
        }
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Scroll linked entries inside the list
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY){
        if(insideList(scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY))){
            applyFieldEdits();
            scroll = Mth.clamp(scroll - (int)Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Save an inline rename with Enter or cancel it with Escape
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers){
        if(editingRow != null && inlineLabelField.isFocused()){
            if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER){
                applyFieldEdits();
                return true;
            }
            if(keyCode == GLFW.GLFW_KEY_ESCAPE){
                finishRename();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        if(editingRow != null && !inlineLabelField.isFocused()) applyFieldEdits();
        graphics.fillGradient(0, 0, width, height, 0x22000000, 0x44000000);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    // Draw the window
    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        scalableGui.push(graphics);
        try {
            PANEL.draw(graphics, leftPos, topPos, BG_W, BG_H);
            String titleText = GraphTextLayout.ellipsize(title.getString(), font::width, BG_W - 24);
            graphics.drawString(font, titleText, leftPos + (BG_W - font.width(titleText)) / 2,
                    topPos + 7, 0x3E2D15, false);
            String modeText = GraphTextLayout.ellipsize(modeLabel().getString(), font::width, BG_W - 24);
            graphics.drawString(font, modeText, leftPos + (BG_W - font.width(modeText)) / 2,
                    topPos + 31, 0xF4E6CE, false);
            drawRows(graphics);
        } finally {
            scalableGui.pop(graphics);
        }
    }

    // Draw the rows
    private void drawRows(GuiGraphics graphics) {
        List<RowEntry> rows = buildRows();
        int end = Math.min(rows.size(), scroll + VISIBLE_ROWS);
        for(int idx = 0; idx < renameButtons.size(); idx++){
            int row = scroll + idx;
            renameButtons.get(idx).visible = row < rows.size() && rows.get(row).canRename();
        }
        for (int idx = scroll; idx < end; idx++) {
            RowEntry row = rows.get(idx);
            int y = topPos + LIST_Y + (idx - scroll) * ROW_H;
            if (row.isSelected(selectedTarget, selectedFace)) {
                graphics.fill(leftPos + LIST_X, y, leftPos + LIST_X + LIST_W, y + ROW_H - 2, 0x554A7A5A);
            }
            int col = row.isSelected(selectedTarget, selectedFace)
                    ? 0xE9F3EA
                    : (row.faceIndex >= 0 ? CTCreateScreenHelper.LABEL_COLOR : CTCreateScreenHelper.BANNER_TITLE_COLOR);
            int textX = LIST_X + 2;
            if(row.targetIndex >= 0 && row.faceIndex < 0){
                LayeredItemRenderer.renderVisible(graphics, blockIcon(targets.get(row.targetIndex)),
                        leftPos + LIST_X + 2, y + 1, 12);
                textX = LIST_X + 18;
            }
            if(!row.equals(editingRow)){
                int textRight = row.canRename() ? RENAME_X - 2 : LIST_X + LIST_W - 2;
                String text = GraphTextLayout.ellipsize(row.text, font::width, textRight - textX);
                graphics.drawString(font, text, leftPos + textX, y + 3, col, false);
            }
            if(idx + 1 < rows.size() && rows.get(idx + 1).faceIndex < 0){
                ROW_DIVIDER.draw(graphics, leftPos + LIST_X, y + ROW_H - 2, LIST_W, 2);
            }
        }
    }

    // Edit the block or area name in its list row
    private void renameRow(int absoluteRow){
        applyFieldEdits();
        List<RowEntry> rows = buildRows();
        if(absoluteRow < 0 || absoluteRow >= rows.size() || !rows.get(absoluteRow).canRename()) return;
        selectByAbsoluteRow(absoluteRow);
        refreshFields();
        editingRow = rows.get(absoluteRow);
        var target = targets.get(selectedTarget);
        var face = selectedFace < 0 ? null : target.faces().get(selectedFace);
        String label = face == null ? target.label() : face.label();
        String hint = face == null ? target.blockId() : face.face().getSerializedName();
        int textX = LIST_X + (face == null ? 18 : 2);
        inlineLabelField.setX(leftPos + textX);
        inlineLabelField.setY(topPos + LIST_Y + (absoluteRow - scroll) * ROW_H + 3);
        inlineLabelField.setWidth(RENAME_X - textX - 2);
        inlineLabelField.setHint(Component.literal(hint));
        inlineLabelField.setValue(label);
        inlineLabelField.visible = true;
        inlineLabelField.active = true;
        setFocused(inlineLabelField);
        inlineLabelField.setFocused(true);
        inlineLabelField.setCursorPosition(label.length());
        inlineLabelField.setHighlightPos(0);
    }

    // Hide the editor without changing its row
    private void finishRename(){
        if(getFocused() == inlineLabelField) setFocused(null);
        inlineLabelField.visible = false;
        inlineLabelField.active = false;
        editingRow = null;
    }

    // Face planes display the block they are attached to
    private ItemStack blockIcon(ContraptionNetworkLinkerData.LinkedTarget target){
        ResourceLocation id = ResourceLocation.tryParse(target.blockId());
        if(id == null) return ItemStack.EMPTY;
        if(id.equals(CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.getId()) && !target.faces().isEmpty()){
            var level = SubLevelBlockEntityCollector.resolveTargetLevel(minecraft.level, target.subLevelId());
            if(level == null) return ItemStack.EMPTY;
            BlockPos support = target.blockPos().relative(target.faces().getFirst().face().getOpposite());
            if(!level.isLoaded(support)) return ItemStack.EMPTY;
            id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(support).getBlock());
        }
        return blockIcons.computeIfAbsent(id, key -> BuiltInRegistries.BLOCK.getOptional(key)
                .map(block -> block.asItem().getDefaultInstance()).orElse(ItemStack.EMPTY));
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
        refreshModeButtons();
    }

    // Remove the selected entry
    private void removeSelectedEntry() {
        applyFieldEdits();
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
        if(editingRow == null || inlineLabelField == null) return;
        int targetIdx = editingRow.targetIndex;
        if(targetIdx >= 0 && targetIdx < targets.size()){
            var target = targets.get(targetIdx);
            String label = inlineLabelField.getValue();
            List<ContraptionNetworkLinkerData.LinkedFace> faces = new ArrayList<>(target.faces());
            int faceIdx = editingRow.faceIndex;
            if(faceIdx >= 0 && faceIdx < faces.size()){
                var face = faces.get(faceIdx);
                faces.set(faceIdx, new ContraptionNetworkLinkerData.LinkedFace(face.face(), label, face.signalKey()));
                label = target.label();
            }
            targets.set(targetIdx, new ContraptionNetworkLinkerData.LinkedTarget(target.blockPos(), target.subLevelId(),
                    target.blockId(), label, target.mode(), target.scope(), faces));
        }
        finishRename();
    }

    // Show recipe assignment for the selected machine area
    private void refreshFields() {
        refreshModeButtons();
    }

    // Check if this is inside list
    private boolean insideList(double mouseX, double mouseY) {
        return mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W
                && mouseY >= topPos + LIST_Y && mouseY < topPos + LIST_Y + ROW_H * VISIBLE_ROWS;
    }

    // Get the maximum scroll
    private int maxScroll() {
        int max = buildRows().size() - VISIBLE_ROWS;
        return Math.max(0, max);
    }

    // Get the mode label
    private Component modeLabel() {
        return Component.translatable(editMode.translationKey()).append(" / ").append(targetModeLabel());
    }

    // Add the target tab
    private void addTargetTab(ContraptionNetworkLinkerData.LinkMode mode, int idx, int atlasTop){
        GuiTextureRegion highlight = FILTER_HIGHLIGHTS[idx];
        CTScaledButton button = addRenderableWidget(new CTScaledButton(scalableGui,
                leftPos + atlasX(197), topPos + atlasY(atlasTop), atlasX(223) - atlasX(197),
                atlasY(atlasTop + 19) - atlasY(atlasTop), Component.translatable(mode.translationKey()),
                btn -> selectTab(mode), (graphics, btn, hovered, partialTick) -> {
            if(editMode == mode || hovered || btn.isFocused()){
                highlight.draw(graphics, leftPos + atlasX(199), topPos + atlasY(highlight.v()),
                        atlasX(199 + highlight.width()) - atlasX(199),
                        atlasY(highlight.v() + highlight.height()) - atlasY(highlight.v()));
            }
            if(mode == ContraptionNetworkLinkerData.LinkMode.SCM){
                int glyphX = btn.getX() + 7;
                int glyphY = btn.getY() + 5;
                graphics.fill(glyphX, glyphY, btn.getX() + btn.getWidth() - 5,
                        btn.getY() + btn.getHeight() - 4, editMode == mode ? 0xFFC9984D : 0xFF444648);
                graphics.drawCenteredString(font, "SCM", btn.getX() + btn.getWidth() / 2 + 1,
                        btn.getY() + (btn.getHeight() - font.lineHeight) / 2, 0xFFF4E6CE);
            }
        }));
        button.setTooltip(Tooltip.create(button.getMessage()));
    }

    // Paint the generated controls
    private CTScaledButton addFooterButton(int x, int y, int w, Component text, Runnable onPress){
        return addRenderableWidget(new CTScaledButton(scalableGui, leftPos + x, topPos + y, w, 14,
                text, btn -> onPress.run(), (graphics, btn, hovered, partialTick) -> {
            Component label = Component.literal(GraphTextLayout.ellipsize(btn.getMessage().getString(), font::width, w - 4));
            CTCreateScreenHelper.renderTextButton(graphics, font, btn.getX(), btn.getY(), w, 14,
                    label, hovered || btn.isFocused(), btn.active, false, 0xF3EEE4, 0xE4B664, false);
        }));
    }

    // Crop sprites without drawing the surrounding atlas
    private static GuiTextureRegion region(int u, int v, int w, int h){
        return new GuiTextureRegion(GUI_TEXTURE, 256, 256, u, v, w, h);
    }

    // Align sprites with their positions in the central panel
    private static int atlasX(int x){
        return Math.round((x - PANEL.u()) * (float)BG_W / PANEL.width());
    }

    private static int atlasY(int y){
        return Math.round((y - PANEL.v()) * (float)BG_H / PANEL.height());
    }

    // Select the tab
    private void selectTab(ContraptionNetworkLinkerData.LinkMode mode) {
        applyFieldEdits();
        setFocused(null);
        editMode = mode;
        selectedTarget = -1;
        selectedFace = -1;

        scroll = 0;
        refreshFields();
        refreshModeButtons();
    }

    // Show the SCM target choices on the linker toolbar
    private void refreshModeButtons(){
        if(faceModeButton != null) faceModeButton.setMessage(targetModeLabel());
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
        return rows;
    }

    // Store the row entry
    private record RowEntry(int targetIndex, int faceIndex, String text) {
        private boolean canRename(){ return true; }
        private boolean isSelected(int selectedTarget, int selectedFace) {
            return targetIndex == selectedTarget && faceIndex == selectedFace;
        }
    }

    private Component targetModeLabel() {
        return Component.literal(targetMode.id().toUpperCase(Locale.ROOT));
    }
}

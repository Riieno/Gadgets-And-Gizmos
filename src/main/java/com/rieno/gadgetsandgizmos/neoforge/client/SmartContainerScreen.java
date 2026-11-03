package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.SmartContainerManifest;
import com.rieno.gadgetsandgizmos.content.SmartContainerMenu;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLayout;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientUi;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletMenuInventory;
import com.rieno.gadgetsandgizmos.lib.client.ui.ScaledMenuScreen;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

// Keep the painted tablet and its attached player inventory inside the window at every GUI scale
public final class SmartContainerScreen extends ScaledMenuScreen<SmartContainerMenu>{
    private final SmartContainerManifestRenderer renderer = new SmartContainerManifestRenderer();
    private final TabletAppClientState state = renderer.createScreenState();
    private int ticks;
    private boolean dialogOpen;

    public SmartContainerScreen(SmartContainerMenu menu, Inventory inventory, Component title){
        super(menu, inventory, title, 320, 180);
    }

    @Override protected int canvasHeight(){ return menu.storageTab() ? 270 : 180; }
    @Override protected TabletLayout preferredCanvasSize(){
        return new TabletLayout(DiagnosticTabletScreen.projectionWidth(),
                (int)Math.round(DiagnosticTabletScreen.projectionHeight() * canvasHeight() / 180D));
    }
    @Override protected void init(){ dialogOpen = false; super.init(); refresh(); }
    @Override protected void containerTick(){ super.containerTick(); if(++ticks % 20 == 0) refresh(); }
    private void refresh(){ renderer.refresh(context(null, 0, 0), state); }

    @Override protected void renderBg(GuiGraphics graphics, float tick, int mouseX, int mouseY){
        renderer.render(context(graphics, mouseX, mouseY), state);
        long time = minecraft.level == null ? 0 : minecraft.level.getDayTime() + 6000;
        String clock = String.format(java.util.Locale.ROOT, "%02d:%02d", time / 1000 % 24, time % 1000 * 60 / 1000);
        graphics.pose().pushPose();
        try{
            graphics.pose().scale(.65F, .65F, 1);
            graphics.drawString(font, clock, 14, 6, 0xFFF3F7FC, true);
            graphics.drawString(font, "CONTAINER", 410, 6, 0xFFF3F7FC, true);
        }finally{ graphics.pose().popPose(); }
    }

    private TabletAppClientContext context(GuiGraphics graphics, int mouseX, int mouseY){
        var app = SmartContainerManifest.APP;
        var tab = app.tabs().stream().filter(val -> val.id().equals(menu.tab())).findFirst().orElse(app.tabs().getFirst());
        var data = DiagnosticTabletClientAppData.get(app.id(), true, menu.tabletId, menu.subLevelId, menu.targetPos);
        return new TabletAppClientContext(app, tab, data, graphics, font, 0, 0, 320, 180,
                new TabletAppClientSurface(0, 0, 320, 180), mouseX, mouseY, this::send, this::refresh, new TabletAppClientUi(){
                    @Override public TabletMenuInventory inventory(){ return new TabletMenuInventory(menu, 0, 0); }
                    @Override public void selectTab(String id){
                        if(!SmartContainerMenu.TABS.contains(id)) return;
                        menu.selectClientTab(id);
                        send("select", ""); refresh();
                    }
                    @Override public void editText(String prompt, String initial, int limit, java.util.function.Consumer<String> accept){
                        dialogOpen = true;
                        minecraft.setScreen(new ControllerTextInputScreen(SmartContainerScreen.this, title, Component.literal(prompt), initial, accept, limit));
                    }
                });
    }

    private void send(String action, String val){
        if("storage_control".equals(action)){
            try{ minecraft.gameMode.handleInventoryButtonClick(menu.containerId, Integer.parseInt(val)); }
            catch(NumberFormatException ignored){}
            return;
        }
        PacketDistributor.sendToServer(new DiagnosticTabletActionPayload(true, InteractionHand.MAIN_HAND,
                menu.targetPos, menu.subLevelId, menu.tabletId, SmartContainerManifest.ID, menu.tab(),
                "refresh".equals(action) ? "menu_refresh" : action, val));
    }

    @Override protected boolean canvasClicked(double x, double y, int btn){
        return renderer.mouseClicked(context(null, (int)x, (int)y), state, x, y, btn) || super.canvasClicked(x, y, btn);
    }
    @Override protected boolean canvasScrolled(double x, double y, double sx, double sy){
        return renderer.mouseScrolled(context(null, (int)x, (int)y), state, x, y, sx, sy) || super.canvasScrolled(x, y, sx, sy);
    }
    @Override protected boolean canvasDragged(double x, double y, int btn, double dx, double dy){
        return renderer.mouseDragged(context(null, (int)x, (int)y), state, x, y, btn, dx, dy) || super.canvasDragged(x, y, btn, dx, dy);
    }
    @Override protected boolean canvasReleased(double x, double y, int btn){
        return renderer.mouseReleased(context(null, (int)x, (int)y), state, x, y, btn) || super.canvasReleased(x, y, btn);
    }
    @Override public boolean keyPressed(int key, int scan, int mods){ return renderer.keyPressed(context(null, 0, 0), state, key, scan, mods) || super.keyPressed(key, scan, mods); }
    @Override public boolean charTyped(char ch, int mods){ return renderer.chatTyped(context(null, 0, 0), state, ch, mods) || super.charTyped(ch, mods); }
    @Override public boolean isPauseScreen(){ return false; }
    @Override public void removed(){ if(!dialogOpen) super.removed(); }
}

package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.client.tablet.*;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletTabDefinition;
import com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletClientAppData;
import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BlockmatesDraftTest{
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    // Reopen with the selected item, amount, crafting mode and task unchanged
    @Test void requestFieldsRoundTrip(){
        CompoundTag draft = new CompoundTag();
        draft.putString("Item", "minecraft:iron_block");
        draft.putString("Amount", "2");
        draft.putBoolean("Craft", true);
        draft.putInt("Task", 3);
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Offset", 8);
        inventory.putString("Selected", "minecraft:oak_log");
        draft.put("Inventory", inventory);
        draft.putString("InventoryAmount", "1024");
        var state = new Blockmates().createScreenState();
        state.loadDraft(draft);
        assertEquals(draft, state.saveDraft());
    }

    @Test void fluidContainerRequestFieldsRoundTrip(){
        CompoundTag draft = new CompoundTag();
        draft.putString("Item", "minecraft:bucket");
        draft.putString("Amount", "3");
        draft.putBoolean("FluidRequest", true);
        draft.putString("Fluid", "minecraft:water");
        draft.putString("Millibuckets", "1000");
        var state = new Blockmates().createScreenState();
        state.loadDraft(draft);
        CompoundTag saved = state.saveDraft();
        assertTrue(saved.getBoolean("FluidRequest"));
        assertEquals("minecraft:water", saved.getString("Fluid"));
        assertEquals("minecraft:bucket", saved.getString("Item"));
        assertEquals("3", saved.getString("Amount"));
        assertEquals("1000", saved.getString("Millibuckets"));
    }

    @Test void failedRequestDetailsSurviveReopening(){
        CompoundTag draft = new CompoundTag();
        ListTag missing = new ListTag();
        missing.add(net.minecraft.nbt.StringTag.valueOf("Stage 2 needs create:deploying"));
        draft.put("Missing", missing);
        draft.putInt("MissingScroll", 26);
        var first = new Blockmates().createScreenState();
        first.loadDraft(draft);
        var reopened = new Blockmates().createScreenState();
        reopened.loadDraft(first.saveDraft());
        assertEquals(missing, reopened.saveDraft().getList("Missing", net.minecraft.nbt.Tag.TAG_STRING));
        assertEquals(26, reopened.saveDraft().getInt("MissingScroll"));
    }

    @Test void periodicRefreshKeepsTheLastRequestFailure(){
        DiagnosticTabletClientAppData.clear();
        var app = PaidTabletApps.BLOCKMATES.id();
        CompoundTag failure = new CompoundTag();
        failure.putBoolean("Linked", true);
        failure.putUUID("RequestResponseId", UUID.randomUUID());
        failure.putBoolean("RequestFailed", true);
        ListTag missing = new ListTag();
        missing.add(net.minecraft.nbt.StringTag.valueOf("Stage 2 needs create:deploying"));
        failure.put("Missing", missing);
        DiagnosticTabletClientAppData.apply(app, failure);
        CompoundTag refresh = new CompoundTag();
        refresh.putBoolean("Linked", true);
        refresh.putInt("RequestLimit", 4096);
        DiagnosticTabletClientAppData.apply(app, refresh);
        CompoundTag retained = DiagnosticTabletClientAppData.get(app);
        assertEquals(failure.getUUID("RequestResponseId"), retained.getUUID("RequestResponseId"));
        assertEquals(missing, retained.getList("Missing", net.minecraft.nbt.Tag.TAG_STRING));
        DiagnosticTabletClientAppData.clear();
    }

    @Test void failedRequestRendersMissingSectionInsideRequestViewport(){
        var app = new Blockmates();
        var state = app.createScreenState();
        CompoundTag data = new CompoundTag();
        data.putBoolean("Linked", true);
        data.putInt("RequestLimit", 4096);
        data.putUUID("RequestResponseId", UUID.randomUUID());
        data.putBoolean("RequestFailed", true);
        ListTag missing = new ListTag();
        missing.add(net.minecraft.nbt.StringTag.valueOf("Machine route: create:sequenced_assembly"));
        data.put("Missing", missing);
        var ctx = context("request", mock(TabletAppClientContext.ActionSender.class), data);
        app.render(ctx, state);
        verify(ctx.graphics()).fill(5, 79, 395, 147, 0xFF332B32);
        assertEquals(missing, state.saveDraft().getList("Missing", net.minecraft.nbt.Tag.TAG_STRING));
    }

    // Inventory requests must retain the selected item and full amount without inheriting crafting mode
    @Test void inventoryRequestUsesSelectedItemAndAmount(){
        var app = new Blockmates();
        var state = app.createScreenState();
        CompoundTag draft = new CompoundTag();
        draft.putBoolean("Craft", true);
        draft.putString("InventoryAmount", "1024");
        CompoundTag inventory = new CompoundTag();
        inventory.putString("Selected", "minecraft:iron_ingot");
        draft.put("Inventory", inventory);
        state.loadDraft(draft);
        var sender = mock(TabletAppClientContext.ActionSender.class);
        var ctx = context("inventory", sender);
        app.render(ctx, state);
        assertTrue(app.mouseClicked(ctx, state, 110, 160, 0));
        verify(sender).send("request", "minecraft:iron_ingot|1024|transfer|");
    }

    // Clicking a visible cargo row must select it before the request is sent
    @Test void clickingInventoryRowRequestsItsItem(){
        var app = new Blockmates();
        var state = app.createScreenState();
        CompoundTag draft = new CompoundTag();
        draft.putString("InventoryAmount", "1024");
        state.loadDraft(draft);
        var sender = mock(TabletAppClientContext.ActionSender.class);
        CompoundTag row = new CompoundTag();
        row.putString("Type", "item");
        row.putString("Id", "minecraft:diamond");
        row.putString("Name", "Diamond");
        row.putLong("Amount", 2048);
        ListTag rows = new ListTag();
        rows.add(row);
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Total", 1);
        inventory.put("Rows", rows);
        CompoundTag data = new CompoundTag();
        data.putBoolean("Linked", true);
        data.putInt("RequestLimit", 4096);
        data.put("Inventory", inventory);
        var ctx = context("inventory", sender, data);
        app.render(ctx, state);
        assertTrue(app.mouseClicked(ctx, state, 10, 30, 0));
        app.render(ctx, state);
        assertTrue(app.mouseClicked(ctx, state, 110, 160, 0));
        verify(sender).send("request", "minecraft:diamond|1024|transfer|");
        verify(ctx.graphics(), atLeastOnce()).renderItem(argThat(stack -> stack.is(net.minecraft.world.item.Items.DIAMOND)), eq(9), eq(28));
    }

    // Periodic polling must preserve a scrolled page while the request tab uses its normal refresh
    @Test void refreshKeepsInventoryPosition(){
        var app = new Blockmates();
        var state = app.createScreenState();
        CompoundTag draft = new CompoundTag();
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Offset", 90);
        draft.put("Inventory", inventory);
        state.loadDraft(draft);
        var sender = mock(TabletAppClientContext.ActionSender.class);
        app.refresh(context("inventory", sender), state);
        verify(sender).send("refresh", "90");
        app.refresh(context("request", sender), state);
        verify(sender).send("refresh", "");
    }

    @Test void expandsWorkerTasksAndDrawsTotalProgress(){
        var app = new Blockmates();
        var state = app.createScreenState();
        UUID workerId = UUID.randomUUID();
        CompoundTag task = new CompoundTag();
        task.putLong("Requested", 4L);
        task.putLong("Completed", 2L);
        CompoundTag output = new CompoundTag();
        output.putString("Type", "item");
        output.putString("Id", "minecraft:iron_ingot");
        CompoundTag current = new CompoundTag();
        current.putUUID("Id", UUID.randomUUID());
        current.put("Task", task);
        current.put("Output", output);
        CompoundTag row = new CompoundTag();
        row.putUUID("Worker", workerId);
        row.putString("Name", "Worker");
        row.putString("Job", "any");
        row.put("Current", current);
        ListTag rows = new ListTag();
        rows.add(row);
        CompoundTag data = new CompoundTag();
        data.putBoolean("Linked", true);
        data.put("Workers", rows);
        var ctx = context("workers", mock(TabletAppClientContext.ActionSender.class), data);
        app.render(ctx, state);
        assertTrue(app.mouseClicked(ctx, state, 10, 22, 0));
        app.render(ctx, state);
        assertEquals(workerId, state.saveDraft().getList("ExpandedWorkers", 10).getCompound(0).getUUID("Id"));
        verify(ctx.graphics(), atLeastOnce()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(0xFF75BD77));
    }

    private static TabletAppClientContext context(String tabId, TabletAppClientContext.ActionSender sender){
        CompoundTag data = new CompoundTag();
        data.putBoolean("Linked", true);
        data.putInt("RequestLimit", 4096);
        return context(tabId, sender, data);
    }

    private static TabletAppClientContext context(String tabId, TabletAppClientContext.ActionSender sender, CompoundTag data){
        var tab = mock(TabletTabDefinition.class);
        when(tab.id()).thenReturn(tabId);
        var graphics = mock(GuiGraphics.class);
        when(graphics.pose()).thenReturn(new PoseStack());
        return new TabletAppClientContext(null, tab, data, graphics, mock(Font.class),
                0, 0, 400, 180, -1, -1, sender, null);
    }
}

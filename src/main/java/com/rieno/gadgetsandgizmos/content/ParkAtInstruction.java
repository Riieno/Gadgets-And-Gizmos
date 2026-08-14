package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.TextScheduleInstruction;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Add one landing zone destination to Create shipping schedules
public final class ParkAtInstruction extends TextScheduleInstruction {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "park_at");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the park at instruction
    public ParkAtInstruction() {
        data.putString("Text", "*");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the summary
    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(icon(), Component.translatable(
                "createthrusters.shipping_schedule.park_at.summary", dockFilter()));
    }

    // Check if this supports conditions
    @Override
    public boolean supportsConditions() {
        return true;
    }

    // Get the id
    @Override
    public ResourceLocation getId() {
        return ID;
    }

    // Get the second line icon
    @Override
    public ItemStack getSecondLineIcon() {
        return icon();
    }

    // Get the instruction title
    @Override
    public List<Component> getTitleAs(String type) {
        return List.of(
                Component.translatable(
                                "createthrusters.shipping_schedule.park_at.title")
                        .withStyle(ChatFormatting.GOLD),
                Component.translatable(
                        "createthrusters.shipping_schedule.park_at.dock",
                        dockFilter()));
    }

    // Get the second line tooltip
    @Override
    public List<Component> getSecondLineTooltip(int slot) {
        return List.of(
                Component.translatable(
                        "createthrusters.shipping_schedule.park_at.dock_tooltip"),
                Component.translatable(
                                "createthrusters.shipping_schedule.park_at.wildcard_tooltip")
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable(
                                "createthrusters.shipping_schedule.park_at.airship_tooltip")
                        .withStyle(ChatFormatting.GRAY));
    }

    // Modify the dock input
    @Override
    @OnlyIn(Dist.CLIENT)
    protected void modifyEditBox(EditBox input) {
        input.setMaxLength(32);
        input.setFilter(val -> StringUtils.countMatches(val, '*') <= 3);
    }

    // Get the dock filter
    public String dockFilter() {
        String filter = data.getString("Text").trim();
        return filter.isEmpty() ? "*" : filter;
    }

    // Get the icon
    private static ItemStack icon() {
        return new ItemStack(CTItems.SHIP_DOCK.get());
    }

    // Start the park at instruction
    @Override
    public @Nullable DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        runtime.state = ScheduleRuntime.State.PRE_TRANSIT;
        runtime.currentEntry++;
        return null;
    }
}

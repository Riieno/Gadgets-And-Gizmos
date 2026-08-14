package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Implement the conditional refuel shipping schedule step
public final class RefuelIfInstruction extends ScheduleInstruction {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "refuel_if");
    private static final String FUEL_MODE = "FuelMode";
    private static final String FUEL_FILTER = "Fuel";
    private static final String MODE_ENERGY = "energy";
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current fuel filter
    private FilterItemStack fuelFilter = FilterItemStack.empty();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the refuel if instruction
    public RefuelIfInstruction() {
        data.putString("Threshold", "25");
        data.putString("Dock", "*");
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
        String src = usesEnergy() ? "FE" : fuelFilter.isEmpty()
                ? "fuel" : fuelFilter.item().getHoverName().getString();
        return Pair.of(fuelFilter.isEmpty() ? ItemStack.EMPTY : fuelFilter.item(), Component.literal(
                "<= " + thresholdPercent() + "% " + src + " @ " + dockFilter()));
    }

    // Check if this supports conditions
    @Override
    public boolean supportsConditions() {
        return false;
    }

    // Get the id
    @Override
    public ResourceLocation getId() {
        return ID;
    }

    // Get the second line icon
    @Override
    public ItemStack getSecondLineIcon() {
        return new ItemStack(Items.COMPASS);
    }

    // Get the instruction title
    @Override
    public List<Component> getTitleAs(String type) {
        return List.of(
                Component.translatable(
                                "createthrusters.shipping_schedule.refuel_if.title",
                                "<=", thresholdPercent(), usesEnergy() ? "FE" : "fuel")
                        .withStyle(ChatFormatting.GOLD),
                Component.translatable(
                        "createthrusters.shipping_schedule.refuel_if.dock",
                        dockFilter()));
    }

    // Get the second line tooltip
    @Override
    public List<Component> getSecondLineTooltip(int slot) {
        return List.of(
                Component.translatable(
                        "createthrusters.shipping_schedule.refuel_if.dock_tooltip"),
                Component.translatable(
                        "createthrusters.shipping_schedule.refuel_if.wildcard_tooltip")
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "createthrusters.shipping_schedule.refuel_if.fuel_tooltip")
                        .withStyle(ChatFormatting.GRAY));
    }

    // Initialize the configuration widgets
    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        builder.addIntegerTextInput(0, 58, (input, tooltip) ->
                input.setMaxLength(3), "Threshold");
        builder.addTextInput(63, 58, RefuelIfInstruction::modifyDockInput, "Dock");
        builder.addSelectionScrollInput(126, 58, (input, label) -> input
                .forOptions(List.of(
                        Component.translatable("createthrusters.shipping_schedule.refuel_if.fuel"),
                        Component.translatable("createthrusters.shipping_schedule.refuel_if.energy")))
                .titled(Component.translatable("createthrusters.shipping_schedule.refuel_if.source")),
                FUEL_MODE);
    }

    // Get the slots targeted
    @Override
    public int slotsTargeted() {
        return 1;
    }

    // Set the item
    @Override
    public void setItem(int idx, ItemStack stack) {
        if (idx == 0) {
            fuelFilter = stack == null || stack.isEmpty()
                    ? FilterItemStack.empty() : FilterItemStack.of(stack.copy());
        }
    }

    // Get the item
    @Override
    public ItemStack getItem(int idx) {
        return idx == 0 && !fuelFilter.isEmpty()
                ? fuelFilter.item().copy() : ItemStack.EMPTY;
    }

    // Write the additional
    @Override
    protected void writeAdditional(HolderLookup.Provider provider, CompoundTag tag) {
        super.writeAdditional(provider, tag);
        tag.put(FUEL_FILTER, fuelFilter.serializeNBT(provider));
    }

    // Read the additional
    @Override
    protected void readAdditional(HolderLookup.Provider provider, CompoundTag tag) {
        super.readAdditional(provider, tag);
        if (!data.contains("Dock") && data.contains("Text")) {
            data.putString("Dock", data.getString("Text"));
        }
        fuelFilter = tag.contains(FUEL_FILTER)
                ? FilterItemStack.of(provider, tag.getCompound(FUEL_FILTER))
                : FilterItemStack.empty();
    }

    // Modify the dock input
    @OnlyIn(Dist.CLIENT)
    private static void modifyDockInput(EditBox input) {
        input.setMaxLength(32);
        input.setFilter(val -> StringUtils.countMatches(val, '*') <= 3);
    }

    // Modify the dock input
    @OnlyIn(Dist.CLIENT)
    private static void modifyDockInput(
            EditBox input, com.simibubi.create.foundation.gui.widget.TooltipArea tooltip) {
        modifyDockInput(input);
        tooltip.withTooltip(List.of(
                Component.translatable("createthrusters.shipping_schedule.refuel_if.dock_tooltip"),
                Component.translatable("createthrusters.shipping_schedule.refuel_if.wildcard_tooltip")
                        .withStyle(ChatFormatting.GRAY)));
    }

    // Get the threshold percent
    public int thresholdPercent() {
        try {
            return Math.max(0, Math.min(100,
                    Integer.parseInt(data.getString("Threshold"))));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    // Get the dock filter
    public String dockFilter() {
        String filter = (data.contains("Dock")
                ? data.getString("Dock") : data.getString("Text")).trim();
        return filter.isEmpty() ? "*" : filter;
    }

    // Check if this uses energy
    public boolean usesEnergy() {
        return MODE_ENERGY.equalsIgnoreCase(data.getString(FUEL_MODE));
    }

    // Check if this has fuel filter
    public boolean hasFuelFilter() {
        return !fuelFilter.isEmpty();
    }

    // Get the fuel filter
    public FilterItemStack fuelFilter() {
        return fuelFilter;
    }

    // Check if this should refuel
    public boolean shouldRefuel(double fuelRatio) {
        double percent = Math.max(0.0D, Math.min(100.0D, fuelRatio * 100.0D));
        return percent <= thresholdPercent();
    }

    // Start the refuel if instruction
    @Override
    public @Nullable DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        runtime.state = ScheduleRuntime.State.PRE_TRANSIT;
        runtime.currentEntry++;
        return null;
    }
}

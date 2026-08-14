package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Add one idempotent couple or decouple action to Create shipping schedules
public final class CarriageCouplingInstruction extends ScheduleInstruction {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "carriage_coupling");
    private static final String OPERATION = "Operation";
    private static final String ENDPOINT = "Endpoint";
    private static final int ATTACH = 0;
    private static final int DECOUPLE = 1;
    public static final int ALL_ENDPOINTS = -1;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the carriage coupling instruction
    public CarriageCouplingInstruction() {
        data.putInt(OPERATION, ATTACH);
        data.putInt(ENDPOINT, ALL_ENDPOINTS);
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
                "createthrusters.shipping_schedule.carriage_coupling.summary",
                operationLabel(), endpointLabel()));
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
        return icon();
    }

    // Get the instruction title
    @Override
    public List<Component> getTitleAs(String type) {
        return List.of(
                Component.translatable(
                                "createthrusters.shipping_schedule.carriage_coupling.title",
                                operationLabel())
                        .withStyle(ChatFormatting.GOLD),
                Component.translatable(
                        "createthrusters.shipping_schedule.carriage_coupling.endpoint",
                        endpointLabel()));
    }

    // Get the second line tooltip
    @Override
    public List<Component> getSecondLineTooltip(int slot) {
        return List.of(
                Component.translatable(
                        "createthrusters.shipping_schedule.carriage_coupling.endpoint_tooltip"),
                Component.translatable(
                                "createthrusters.shipping_schedule.carriage_coupling.all_tooltip")
                        .withStyle(ChatFormatting.GRAY));
    }

    // Initialize the configuration widgets
    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        builder.addSelectionScrollInput(0, 90, (input, label) -> input
                        .forOptions(List.of(
                                Component.translatable(
                                        "createthrusters.shipping_schedule.carriage_coupling.attach"),
                                Component.translatable(
                                        "createthrusters.shipping_schedule.carriage_coupling.decouple")))
                        .titled(Component.translatable(
                                "createthrusters.shipping_schedule.carriage_coupling.operation")),
                OPERATION);
        builder.addScrollInput(95, 89, (input, label) -> input
                        .withRange(ALL_ENDPOINTS, 256)
                        .withShiftStep(10)
                        .format(CarriageCouplingInstruction::endpointLabel)
                        .titled(Component.translatable(
                                "createthrusters.shipping_schedule.carriage_coupling.endpoint_title")),
                ENDPOINT);
    }

    // Attach the carriage coupling instruction
    public boolean attach() {
        return data.getInt(OPERATION) != DECOUPLE;
    }

    // Get the endpoint selector
    public int endpointSelector() {
        return Math.max(ALL_ENDPOINTS, data.getInt(ENDPOINT));
    }

    // Get the operation label
    private Component operationLabel() {
        return Component.translatable(attach()
                ? "createthrusters.shipping_schedule.carriage_coupling.attach"
                : "createthrusters.shipping_schedule.carriage_coupling.decouple");
    }

    // Get the endpoint label
    private Component endpointLabel() {
        return endpointLabel(endpointSelector());
    }

    // Get the endpoint label
    private static Component endpointLabel(int endpoint) {
        return endpoint == ALL_ENDPOINTS
                ? Component.translatable(
                        "createthrusters.shipping_schedule.carriage_coupling.all_endpoints")
                : Component.literal("#" + endpoint);
    }

    // Get the icon
    private static ItemStack icon() {
        return new ItemStack(Items.CHAIN);
    }

    // Start the carriage coupling instruction
    @Override
    public @Nullable DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        runtime.state = ScheduleRuntime.State.PRE_TRANSIT;
        runtime.currentEntry++;
        return null;
    }
}

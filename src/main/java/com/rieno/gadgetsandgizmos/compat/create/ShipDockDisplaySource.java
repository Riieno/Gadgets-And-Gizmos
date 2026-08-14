package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShipDockRegistry;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

// Show dock identity and service status on Create display links
public class ShipDockDisplaySource extends DisplaySource {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String DISPLAY_MODE_KEY = "ShipDockDisplayMode";
    private static final int DEPARTURES_MODE = 0;
    private static final int JOURNEY_MODE = 1;
    private static final int POSITION_MODE = 2;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the provide text
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext ctx, DisplayTargetStats stats) {
        if (!(ctx.getSourceBlockEntity() instanceof ShipDockBlockEntity dock)) {
            return EMPTY;
        }
        List<ShipDockRegistry.ShipTelemetry> telemetry = dock.getShippingTelemetry();
        if (telemetry.isEmpty()) {
            return List.of(Component.literal("No scheduled ships"));
        }
        int limit = Math.max(1, stats.maxRows());
        List<MutableComponent> lines = new ArrayList<>(Math.min(limit, telemetry.size() * 3));
        int mode = ctx.sourceConfig().getInt(DISPLAY_MODE_KEY);
        for (ShipDockRegistry.ShipTelemetry update : telemetry) {
            if (lines.size() >= limit) {
                break;
            }
            switch (mode) {
                case JOURNEY_MODE -> appendJourney(lines, limit, update);
                case POSITION_MODE -> appendPosition(lines, limit, update);
                default -> lines.add(Component.literal(formatDeparture(update)));
            }
        }
        return List.copyOf(lines);
    }

    // Populate the data
    @Override
    public void populateData(DisplayLinkContext ctx) {
        CompoundTag config = ctx.sourceConfig();
        if (!config.contains(DISPLAY_MODE_KEY)) {
            config.putInt(DISPLAY_MODE_KEY, DEPARTURES_MODE);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the configuration widgets
    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(
            DisplayLinkContext ctx, ModularGuiLineBuilder builder, boolean isFirstLine
    ) {
        super.initConfigurationWidgets(ctx, builder, isFirstLine);
        if (isFirstLine) {
            return;
        }
        builder.addSelectionScrollInput(0, 95, (input, label) -> input.forOptions(List.of(
                Component.translatable("createthrusters.display_source.ship_dock_shipping.departures"),
                Component.translatable("createthrusters.display_source.ship_dock_shipping.journey"),
                Component.translatable("createthrusters.display_source.ship_dock_shipping.position"))),
                DISPLAY_MODE_KEY);
    }

    // Get the passive refresh ticks
    @Override
    public int getPassiveRefreshTicks() {
        return 10;
    }

    // Check if the display should reset passively
    @Override
    public boolean shouldPassiveReset() {
        return true;
    }

    // Add the journey
    private static void appendJourney(
            List<MutableComponent> lines, int limit, ShipDockRegistry.ShipTelemetry update
    ) {
        addLine(lines, limit, update.shipName() + " | " + update.journeyPhase());
        addLine(lines, limit, "From " + update.currentStop() + " to " + update.targetName());
        String next = update.nextStop().isBlank() ? "" : " | Next " + update.nextStop();
        addLine(lines, limit, formatEta(update.etaSeconds()) + " | "
                + formatDistance(update.distanceToTarget()) + " | Fuel "
                + Math.round(update.fuelRatio() * 100.0D) + "%" + next);
    }

    // Add the position
    private static void appendPosition(
            List<MutableComponent> lines, int limit, ShipDockRegistry.ShipTelemetry update
    ) {
        addLine(lines, limit, update.shipName() + " | " + update.targetName());
        addLine(lines, limit, "Position " + formatPosition(update.currentPosition()));
        addLine(lines, limit, formatEta(update.etaSeconds()) + " | "
                + formatDistance(update.distanceToTarget()) + " | "
                + Math.round(update.progressPercent()) + "% complete");
    }

    // Add the line
    private static void addLine(List<MutableComponent> lines, int limit, String text) {
        if (lines.size() < limit) {
            lines.add(Component.literal(text));
        }
    }

    // Format the departure
    private static String formatDeparture(ShipDockRegistry.ShipTelemetry update) {
        return formatEta(update.etaSeconds()) + " | " + update.shipName() + " | "
                + update.targetName() + " | " + update.journeyStatus();
    }

    // Format the distance
    private static String formatDistance(double distance) {
        return Math.round(Math.max(0.0D, distance)) + "m";
    }

    // Format the position
    private static String formatPosition(net.minecraft.world.phys.Vec3 pos) {
        return Math.round(pos.x) + ", " + Math.round(pos.y)
                + ", " + Math.round(pos.z);
    }

    // Format the eta
    private static String formatEta(long etaSeconds) {
        if (etaSeconds < 0L) {
            return "ETA --:--";
        }
        if (etaSeconds == 0L) {
            return "Arrived";
        }
        long hours = etaSeconds / 3_600L;
        long minutes = etaSeconds % 3_600L / 60L;
        long seconds = etaSeconds % 60L;
        return hours > 0L ? "ETA %d:%02d:%02d".formatted(hours, minutes, seconds)
                : "ETA %02d:%02d".formatted(minutes, seconds);
    }
}

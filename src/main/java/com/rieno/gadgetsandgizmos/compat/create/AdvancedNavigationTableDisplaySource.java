package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedNavigationTableBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.Duration;
import java.util.List;

// Show navigation targets and route state on Create displays
public class AdvancedNavigationTableDisplaySource extends SingleLineDisplaySource {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String SELECTION_KEY = "NavTableSelection";
    private static final int DISTANCE_MODE = 0;
    private static final int ETA_MODE = 1;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the provide line
    @Override
    protected MutableComponent provideLine(DisplayLinkContext ctx, DisplayTargetStats stats) {
        if (!(ctx.getSourceBlockEntity() instanceof AdvancedNavigationTableBlockEntity table)) {
            return EMPTY_LINE.copy();
        }

        return switch (ctx.sourceConfig().getInt(SELECTION_KEY)) {
            case DISTANCE_MODE -> formatDistance(table.ct$getDisplayDistanceToTarget());
            case ETA_MODE -> formatEta(table.ct$getDisplayDistanceToTarget(), table.ct$getLastDisplayDistanceToTarget());
            default -> EMPTY_LINE.copy();
        };
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the configuration widgets
    @Override
    public void initConfigurationWidgets(DisplayLinkContext ctx, ModularGuiLineBuilder builder, boolean isFirstLine) {
        super.initConfigurationWidgets(ctx, builder, isFirstLine);
        if (isFirstLine) {
            return;
        }
        builder.addSelectionScrollInput(0, 95, (input, label) -> input.forOptions(List.of(
                Component.translatable("createthrusters.display_source.advanced_navigation_table.distance"),
                Component.translatable("createthrusters.display_source.advanced_navigation_table.eta_real"))), SELECTION_KEY);
    }

    // Check if this allows labeling
    @Override
    protected boolean allowsLabeling(DisplayLinkContext ctx) {
        return true;
    }

    // Get the passive refresh ticks
    @Override
    public int getPassiveRefreshTicks() {
        return 10;
    }

    // Format the distance
    private static MutableComponent formatDistance(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0D) {
            return EMPTY_LINE.copy();
        }
        return Component.literal(String.valueOf((int) distance));
    }

    // Format the eta
    private static MutableComponent formatEta(double distance, double lastDistance) {
        if (!Double.isFinite(distance) || !Double.isFinite(lastDistance) || distance < 0.0D || lastDistance < 0.0D) {
            return Component.literal("N/A");
        }

        double change = lastDistance - distance;
        double speed = change / 0.5D;
        if (change < 0.001D || speed <= 0.0D) {
            return Component.literal("N/A");
        }

        int totalSeconds = (int) (distance / speed);
        if (totalSeconds < 0) {
            return Component.literal("N/A");
        }

        Duration duration = Duration.ofSeconds(totalSeconds);
        String eta = "%2s:%2s".formatted(duration.toMinutesPart(), duration.toSecondsPart());
        if (duration.toHoursPart() > 0) {
            eta = "%2s:".formatted(duration.toHoursPart()) + eta;
        }
        return Component.literal(eta.replace(' ', '0'));
    }
}

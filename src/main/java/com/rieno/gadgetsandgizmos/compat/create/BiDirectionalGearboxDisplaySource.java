package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Show gearbox lane mode, speed and servo angles on Create displays
public class BiDirectionalGearboxDisplaySource extends DisplaySource {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the provide text
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext ctx, DisplayTargetStats stats) {
        if (!(ctx.getSourceBlockEntity() instanceof BiDirectionalGearboxBlockEntity gearbox)) {
            return EMPTY;
        }

        int maxRows = Math.max(1, stats.maxRows());
        if (maxRows == 1) {
            if (gearbox.isServoModeActive()) {
                return List.of(Component.literal(String.format(Locale.ROOT, "N %.1f S %.1f E %.1f W %.1f",
                        gearbox.getFaceAngle(Direction.NORTH),
                        gearbox.getFaceAngle(Direction.SOUTH),
                        gearbox.getFaceAngle(Direction.EAST),
                        gearbox.getFaceAngle(Direction.WEST))));
            }

            return List.of(Component.literal(String.format(Locale.ROOT, "%s | NS %.1f | EW %.1f",
                    gearbox.getOperationModeName(),
                    Mth.abs(gearbox.getNorthSouthSpeed()),
                    Mth.abs(gearbox.getEastWestSpeed()))));
        }

        List<MutableComponent> lines = new ArrayList<>();
        lines.add(Component.translatable("createthrusters.text.mode_1_s", gearbox.getOperationModeName()));
    if (gearbox.isServoModeActive()) {
            lines.add(Component.translatable("createthrusters.text.north_south_1_s_2_s_deg", String.format(Locale.ROOT, "%.1f", gearbox.getFaceAngle(Direction.NORTH)), String.format(Locale.ROOT, "%.1f", gearbox.getFaceAngle(Direction.SOUTH))));
            lines.add(Component.translatable("createthrusters.text.east_west_1_s_2_s_deg", String.format(Locale.ROOT, "%.1f", gearbox.getFaceAngle(Direction.EAST)), String.format(Locale.ROOT, "%.1f", gearbox.getFaceAngle(Direction.WEST))));
        lines.add(Component.translatable("createthrusters.text.limits_1_s_2_s_3_s_4_s", String.format(Locale.ROOT, "%.1f", gearbox.getFaceMaxAngle(Direction.NORTH)), String.format(Locale.ROOT, "%.1f", gearbox.getFaceMaxAngle(Direction.SOUTH)), String.format(Locale.ROOT, "%.1f", gearbox.getFaceMaxAngle(Direction.EAST)), String.format(Locale.ROOT, "%.1f", gearbox.getFaceMaxAngle(Direction.WEST))));
            lines.add(Component.translatable("createthrusters.text.signals_1_s_2_s_3_s_4_s", gearbox.getOutputSignal(Direction.NORTH), gearbox.getOutputSignal(Direction.SOUTH), gearbox.getOutputSignal(Direction.EAST), gearbox.getOutputSignal(Direction.WEST)));
        } else {
            lines.add(Component.translatable("createthrusters.text.north_south_speed_1_s_rpm", String.format(Locale.ROOT, "%.1f", Mth.abs(gearbox.getNorthSouthSpeed()))));
            lines.add(Component.translatable("createthrusters.text.east_west_speed_1_s_rpm", String.format(Locale.ROOT, "%.1f", Mth.abs(gearbox.getEastWestSpeed()))));
        }

        return lines.size() > maxRows ? lines.subList(0, maxRows) : lines;
    }

    // Get the passive refresh ticks
    @Override
    public int getPassiveRefreshTicks() {
        return 5;
    }

    // Check if the display should reset passively
    @Override
    public boolean shouldPassiveReset() {
        return false;
    }
}

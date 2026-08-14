package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Show linked orientation values and source status on Create displays
public class GyroscopeLinkDisplaySource extends DisplaySource {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the provide text
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext ctx, DisplayTargetStats stats) {
        if (!(ctx.getSourceBlockEntity() instanceof GyroscopeLinkBlockEntity link)) {
            return EMPTY;
        }

        double[] deg = link.getLinkedAnglesDegrees();
        Vec3 dir = link.getLinkedDirection();
        int maxRows = Math.max(1, stats.maxRows());

        if (maxRows == 1) {
            if (!link.isLinked()) {
                return List.of(Component.literal("Unlinked"));
            }
            if (deg == null) {
                return List.of(Component.literal("Linked | No live data"));
            }
            return List.of(Component.literal(String.format(Locale.ROOT, "X %.1f | Z %.1f", deg[0], deg[1])));
        }

        List<MutableComponent> lines = new ArrayList<>();
        if (!link.isLinked()) {
            lines.add(Component.literal("Unlinked"));
        } else if (deg == null || dir == null) {
            lines.add(Component.literal("Linked"));
            lines.add(Component.literal("Waiting for live data"));
        } else {
            lines.add(Component.literal(String.format(Locale.ROOT, "X Angle: %.1f deg", deg[0])));
            lines.add(Component.literal(String.format(Locale.ROOT, "Z Angle: %.1f deg", deg[1])));
            lines.add(Component.literal(String.format(Locale.ROOT, "Dir: %.2f %.2f %.2f", dir.x, dir.y, dir.z)));
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

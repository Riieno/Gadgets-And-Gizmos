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
                return List.of(Component.translatable("createthrusters.text.unlinked"));
            }
            if (deg == null) {
                return List.of(Component.translatable("createthrusters.text.linked_no_live_data"));
            }
            return List.of(Component.literal(String.format(Locale.ROOT, "X %.1f | Z %.1f", deg[0], deg[1])));
        }

        List<MutableComponent> lines = new ArrayList<>();
        if (!link.isLinked()) {
            lines.add(Component.translatable("createthrusters.text.unlinked"));
        } else if (deg == null || dir == null) {
            lines.add(Component.translatable("createthrusters.text.linked"));
            lines.add(Component.translatable("createthrusters.text.waiting_for_live_data"));
        } else {
            lines.add(Component.translatable("createthrusters.text.x_angle_1_s_deg", String.format(Locale.ROOT, "%.1f", deg[0])));
            lines.add(Component.translatable("createthrusters.text.z_angle_1_s_deg", String.format(Locale.ROOT, "%.1f", deg[1])));
            lines.add(Component.translatable("createthrusters.text.dir_1_s_2_s_3_s", String.format(Locale.ROOT, "%.2f", dir.x), String.format(Locale.ROOT, "%.2f", dir.y), String.format(Locale.ROOT, "%.2f", dir.z)));
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

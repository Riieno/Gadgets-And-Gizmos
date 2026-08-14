package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

// Resolve an ACC display surface and its controller-side terminal target
public final class AccDisplayTarget extends DisplayTarget {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Accept the text
    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext ctx) {
        BlockEntity target = ctx.getTargetBlockEntity();
        if (target instanceof AccDisplayBlockEntity display) {
            display.acceptDisplayLinkText(line, text);
        }
    }

    // Get the provide stats
    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext ctx) {
        BlockEntity target = ctx.getTargetBlockEntity();
        if (!(target instanceof AccDisplayBlockEntity display)) {
            return new DisplayTargetStats(1, 16, this);
        }
        AccDisplayBlockEntity root = display.networkRoot();
        if (root == null) {
            root = display;
        }
        return new DisplayTargetStats(
                Math.max(1, root.networkHeight() * 6),
                Math.max(16, root.networkWidth() * 18), this);
    }

    // Get the multiblock bounds
    @Override
    public AABB getMultiblockBounds(net.minecraft.world.level.LevelAccessor level,
                                    net.minecraft.core.BlockPos pos) {
        BlockEntity target = level.getBlockEntity(pos);
        if (!(target instanceof AccDisplayBlockEntity display)) {
            return super.getMultiblockBounds(level, pos);
        }
        AccDisplayBlockEntity root = display.networkRoot();
        if (root == null) {
            return super.getMultiblockBounds(level, pos);
        }
        AABB bounds = new AABB(root.getBlockPos());
        net.minecraft.core.Direction right = root.screenRight();
        for (int y = 0; y < root.networkHeight(); y++) {
            for (int x = 0; x < root.networkWidth(); x++) {
                bounds = bounds.minmax(new AABB(root.getBlockPos().below(y).relative(right, x)));
            }
        }
        return bounds;
    }
}

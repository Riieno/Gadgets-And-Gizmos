package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.mixin.SwivelBearingPlateParentAccessor;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

// Keep the instanced Thruster Swivel Bearing Plate model aligned with its live block state
public class ThrusterSwivelBearingPlateVisual extends OrientedRotatingVisual<SwivelBearingPlateBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster swivel bearing plate visual
    private ThrusterSwivelBearingPlateVisual(VisualizationContext ctx,
                                             SwivelBearingPlateBlockEntity blockEntity,
                                             float partialTick,
                                             Direction facing,
                                             Model model) {
        super(ctx, blockEntity, partialTick, Direction.UP, facing, model);

        CTFlywheelVisuals.forceWhite(this.rotatingModel).setChanged();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the thruster swivel bearing plate visual
    public static BlockEntityVisual<? super SwivelBearingPlateBlockEntity> create(VisualizationContext ctx,
                                                                                   SwivelBearingPlateBlockEntity blockEntity,
                                                                                   float partialTick) {
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.getValue(SwivelBearingBlock.FACING);
        PartialModel model = SimPartialModels.SHAFT_SIXTEENTH;

        SwivelBearingPlateParentAccessor accessor = (SwivelBearingPlateParentAccessor) blockEntity;
        BlockPos parentPos = accessor.createthrusters$getParent();
        if (parentPos != null && blockEntity.getLevel() != null) {
            UUID parentSubLevelId = accessor.createthrusters$getParentSubLevelId();
            ThrusterBearingBlockEntity parent = parentSubLevelId != null
                    ? SimulatedHelper.findBlockEntity(blockEntity.getLevel(), parentSubLevelId, parentPos, ThrusterBearingBlockEntity.class)
                    : SimulatedHelper.findBlockEntityIncludingSubLevels(blockEntity.getLevel(), parentPos, ThrusterBearingBlockEntity.class);
            if (parent != null) {
                model = CTPartialModels.THRUSTER_BEARING_SWIVEL_LINK;
            }
        }

        return new ThrusterSwivelBearingPlateVisual(ctx, blockEntity, partialTick, facing, Models.partial(model));
    }
}

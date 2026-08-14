package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlockEntity;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

// Draw the Physics Gantry Shaft visual
public class PhysicsGantryShaftVisual extends OrientedRotatingVisual<PhysicsGantryShaftBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry shaft visual
    private PhysicsGantryShaftVisual(VisualizationContext ctx, PhysicsGantryShaftBlockEntity blockEntity,
                                     float partialTick, Direction facing, Model model) {
        super(ctx, blockEntity, partialTick, Direction.UP, facing, model);

        CTFlywheelVisuals.forceWhite(this.rotatingModel).setChanged();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the physics gantry shaft visual
    public static BlockEntityVisual<? super PhysicsGantryShaftBlockEntity> create(VisualizationContext ctx,
                                                                                    PhysicsGantryShaftBlockEntity blockEntity,
                                                                                    float partialTick) {
        BlockState state = blockEntity.getBlockState();
        PhysicsGantryShaftBlock.Part part = state.getValue(PhysicsGantryShaftBlock.PART);
        boolean powered = state.getValue(PhysicsGantryShaftBlock.POWERED);
        Direction facing = state.getValue(PhysicsGantryShaftBlock.FACING);
        boolean flipped = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE;

        Model model = Models.partial(getPartial(part, powered, flipped));
        return new PhysicsGantryShaftVisual(ctx, blockEntity, partialTick, facing, model);
    }

    // Get the partial
    private static PartialModel getPartial(PhysicsGantryShaftBlock.Part part, boolean powered, boolean flipped) {
        return switch (part) {
            case START  -> powered ? (flipped ? CTPartialModels.GANTRY_SHAFT_START_POWERED_FLIPPED  : CTPartialModels.GANTRY_SHAFT_START_POWERED)
                                   : (flipped ? CTPartialModels.GANTRY_SHAFT_START_FLIPPED          : CTPartialModels.GANTRY_SHAFT_START);
            case MIDDLE -> powered ? (flipped ? CTPartialModels.GANTRY_SHAFT_MIDDLE_POWERED_FLIPPED : CTPartialModels.GANTRY_SHAFT_MIDDLE_POWERED)
                                   : (flipped ? CTPartialModels.GANTRY_SHAFT_MIDDLE_FLIPPED         : CTPartialModels.GANTRY_SHAFT_MIDDLE);
            case END    -> powered ? (flipped ? CTPartialModels.GANTRY_SHAFT_END_POWERED_FLIPPED    : CTPartialModels.GANTRY_SHAFT_END_POWERED)
                                   : (flipped ? CTPartialModels.GANTRY_SHAFT_END_FLIPPED            : CTPartialModels.GANTRY_SHAFT_END);
            case SINGLE -> powered ? (flipped ? CTPartialModels.GANTRY_SHAFT_SINGLE_POWERED_FLIPPED : CTPartialModels.GANTRY_SHAFT_SINGLE_POWERED)
                                   : (flipped ? CTPartialModels.GANTRY_SHAFT_SINGLE_FLIPPED         : CTPartialModels.GANTRY_SHAFT_SINGLE);
        };
    }
}

package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.RatchetCogwheelBlock;
import com.rieno.gadgetsandgizmos.content.RatchetCogwheelBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

// Render the independently rotating ratchet cog and shaft through Flywheel
public class RatchetCogwheelVisual extends KineticBlockEntityVisual<RatchetCogwheelBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Cog instance
    private final RotatingInstance gear;
    // Shaft instance
    private final RotatingInstance shaft;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ratchet cogwheel visual
    public RatchetCogwheelVisual(VisualizationContext ctx, RatchetCogwheelBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        BlockState state = blockEntity.getBlockState();
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        Direction facing = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        boolean large = state.getBlock() instanceof RatchetCogwheelBlock cogwheel && cogwheel.isLargeCog();

        gear = this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(large
                        ? CTPartialModels.LARGE_RATCHET_COGWHEEL_GEAR
                        : CTPartialModels.RATCHET_COGWHEEL_GEAR))
                .createInstance();
        gear.rotateToFace(Direction.UP, facing)
                .setup(blockEntity)
                .setPosition((Vec3i) getVisualPosition());
        CTFlywheelVisuals.forceWhite(gear).setChanged();

        shaft = this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(large
                        ? CTPartialModels.LARGE_RATCHET_COGWHEEL_SHAFT
                        : CTPartialModels.RATCHET_COGWHEEL_SHAFT))
                .createInstance();
        shaft.rotateToFace(Direction.UP, facing)
                .setup(blockEntity.getShaftKineticBlockEntity())
                .setPosition((Vec3i) getVisualPosition());
        CTFlywheelVisuals.forceWhite(shaft).setChanged();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update both ratchet partials
    @Override
    public void update(float partialTick) {
        KineticBlockEntity shaftKinetic = blockEntity.getShaftKineticBlockEntity();
        gear.setup(blockEntity);
        CTFlywheelVisuals.forceWhite(gear).setChanged();
        shaft.setup(shaftKinetic);
        CTFlywheelVisuals.forceWhite(shaft).setChanged();
    }

    // Update the light
    @Override
    public void updateLight(float partialTick) {
        relight(new FlatLit[] { gear, shaft });
    }

    // Delete both ratchet partials
    @Override
    protected void _delete() {
        gear.delete();
        shaft.delete();
    }

    // Collect the crumbling instances
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(gear);
        consumer.accept(shaft);
    }
}

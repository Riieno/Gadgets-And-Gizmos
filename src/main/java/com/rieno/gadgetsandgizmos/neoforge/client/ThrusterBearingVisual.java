package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

// Keep the instanced Thruster Bearing model aligned with its live block state
public class ThrusterBearingVisual extends KineticBlockEntityVisual<ThrusterBearingBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Cog instance
    private final RotatingInstance cogInstance;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster bearing visual
    public ThrusterBearingVisual(VisualizationContext ctx, ThrusterBearingBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        Direction connectorFacing = blockEntity.getBlockState().getValue(BlockStateProperties.FACING).getOpposite();

        this.cogInstance = (RotatingInstance) this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(SimPartialModels.SWIVEL_BEARING_COG))
                .createInstance();
        this.cogInstance.rotateToFace(Direction.UP, connectorFacing)
                .setup(blockEntity.getExtraKinetics())
                .setPosition((Vec3i) this.getVisualPosition())
                .colorRgb(0xFFFFFF)
                .setChanged();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the thruster bearing visual
    @Override
    public void update(float pt) {

        this.cogInstance.setup(this.blockEntity.getExtraKinetics()).colorRgb(0xFFFFFF).setChanged();
    }

    // Update the light
    @Override
    public void updateLight(float partialTick) {
        this.relight(new FlatLit[]{this.cogInstance});
    }

    // Delete the thruster bearing visual
    @Override
    protected void _delete() {
        this.cogInstance.delete();
    }

    // Collect the crumbling instances
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(this.cogInstance);
    }
}

package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.render.DynamicKineticPartsVisual;
import com.simibubi.create.AllPartialModels;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;

// Keep bearing pistons and the unassembled plate in reusable GPU instances
public final class VectorBearingVisual extends DynamicKineticPartsVisual<VectorBearingBlockEntity>{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain one plate and four pairs of piston parts
    private final TransformedInstance plate;
    private final TransformedInstance[] heads = new TransformedInstance[4];
    private final TransformedInstance[] poles = new TransformedInstance[4];
    private final PoseStack poses = new PoseStack();
    // Skip instance uploads while the physical head remains still
    private final Quaternionf prevTilt = new Quaternionf();
    private boolean prevMounted;
    private boolean initialized;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Allocate each bearing part once for this visual's lifetime
    public VectorBearingVisual(VisualizationContext ctx, VectorBearingBlockEntity be, float partialTick){
        super(ctx, be, partialTick, Direction.SOUTH, be.getBearingFacing().getOpposite(),
                Models.partial(AllPartialModels.SHAFT_HALF), 0xFFFFFF);
        plate = addPart(Models.partial(CTPartialModels.VECTOR_BEARING_PLATE));
        for(int idx = 0; idx < 4; idx++){
            heads[idx] = addPart(Models.partial(CTPartialModels.VECTOR_BEARING_PISTON_HEAD));
            poles[idx] = addPart(Models.partial(CTPartialModels.VECTOR_BEARING_PISTON_POLE));
        }
        animate(partialTick);
    }

    // Upload changed attachment transforms without transforming model vertices on the CPU
    @Override protected void animate(float partialTick){
        boolean mounted = blockEntity.isMountedAssemblyPresent();
        Quaternionf tilt = VectorBearingRenderer.headTilt(blockEntity, partialTick);
        if(initialized && mounted == prevMounted && prevTilt.equals(tilt)) return;
        initialized = true;
        prevMounted = mounted;
        prevTilt.set(tilt);
        Direction facing = blockEntity.getBearingFacing();
        poses.pushPose();
        poses.translate(getVisualPosition().getX(), getVisualPosition().getY(), getVisualPosition().getZ());
        poses.pushPose();
        if(mounted) plate.setZeroTransform().setChanged();
        else{
            poses.translate(0.5D, 0.5D, 0.5D);
            poses.mulPose(new Quaternionf().rotationTo(0, 1, 0, facing.getStepX(), facing.getStepY(), facing.getStepZ()));
            poses.translate(-0.5D, -0.5D, -0.5D);
            plate.setTransform(poses).setChanged();
        }
        poses.popPose();
        for(int idx = 0; idx < 4; idx++){
            positionPart(heads[idx], facing, tilt, idx, false);
            positionPart(poles[idx], facing, tilt, idx, true);
        }
        poses.popPose();
    }

    // Refresh the shaft orientation and part transforms after a wrench rotation
    @Override public void update(float partialTick){
        setModelFacing(Direction.SOUTH, blockEntity.getBearingFacing().getOpposite());
        super.update(partialTick);
        initialized = false;
        animate(partialTick);
    }

    // Apply the fallback renderer's exact piston transform to one instance
    private void positionPart(TransformedInstance part, Direction facing, Quaternionf tilt, int idx, boolean pole){
        poses.pushPose();
        if(VectorBearingRenderer.posePiston(poses, facing, tilt, idx, pole)) part.setTransform(poses).setChanged();
        else part.setZeroTransform().setChanged();
        poses.popPose();
    }
}

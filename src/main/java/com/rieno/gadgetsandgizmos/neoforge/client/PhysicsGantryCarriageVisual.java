package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import java.util.function.Consumer;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;

// Keep the instanced Physics Gantry Carriage model aligned with its live block state
public class PhysicsGantryCarriageVisual extends ShaftVisual<PhysicsGantryCarriageBlockEntity>
        implements SimpleDynamicVisual {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float OUTPUT_SHAFT_PROTRUSION = 0.0625f;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Gantry cogs
    private final TransformedInstance gantryCogs;
    // Output shaft
    private final RotatingInstance outputShaft;
    // Facing
    final Direction facing;
    // Tracks whether along first is set
    final boolean alongFirst;
    // Rotation axis
    final Direction.Axis rotationAxis;
    // Rotation mult
    final float rotationMult;
    // Visual pos
    final BlockPos visualPos;
    // Mounted dir
    final Direction mountedDir;
    // Last angle
    private float lastAngle = Float.NaN;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry carriage visual
    public PhysicsGantryCarriageVisual(VisualizationContext ctx, PhysicsGantryCarriageBlockEntity blockEntity,
                                       float partialTick) {
        super(ctx, blockEntity, partialTick);

        CTFlywheelVisuals.forceWhite(this.rotatingModel);
        this.gantryCogs = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(CTPartialModels.PHYSICS_GANTRY_COGS))
                .createInstance();

        this.facing = blockState.getValue(PhysicsGantryCarriageBlock.FACING);
        this.alongFirst = blockState.getValue(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE);
        this.rotationAxis = KineticBlockEntityRenderer.getRotationAxisOf(blockEntity);
        this.rotationMult = getRotationMultiplier(getGantryAxis(), facing);
        this.visualPos = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? blockEntity.getBlockPos()
                : blockEntity.getBlockPos().relative(facing.getOpposite());

        this.mountedDir = PhysicsGantryCarriageBlockEntity.getMountedPayloadDirection(blockState);

        animateCogs(getCogAngle());

        this.outputShaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                .createInstance();
        outputShaft

            .rotateToFace(Direction.SOUTH, mountedDir)
                .setup(blockEntity, mountedDir.getAxis())

            .setPosition(
                getVisualPosition().getX() + mountedDir.getStepX() * OUTPUT_SHAFT_PROTRUSION,
                getVisualPosition().getY() + mountedDir.getStepY() * OUTPUT_SHAFT_PROTRUSION,
                getVisualPosition().getZ() + mountedDir.getStepZ() * OUTPUT_SHAFT_PROTRUSION)
                .colorRgb(0xFFFFFF)
                .setChanged();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Begin the frame
    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        float cogAngle = getCogAngle();
        if (Mth.equal(cogAngle, lastAngle)) {
            return;
        }
        animateCogs(cogAngle);
        lastAngle = cogAngle;
        outputShaft.setup(blockEntity, mountedDir.getAxis()).colorRgb(0xFFFFFF).setChanged();
    }

    // Get the cog angle
    private float getCogAngle() {
        return PhysicsGantryCarriageRenderer.getAngleForBE(blockEntity, visualPos, rotationAxis) * rotationMult;
    }

    // Animate the cogs
    private void animateCogs(float cogAngle) {
        gantryCogs.setIdentityTransform()
                .translate((Vec3i) getVisualPosition())
                .center()
                .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXDegrees(facing == Direction.UP ? 0.0f : (facing == Direction.DOWN ? 180.0f : 90.0f))
                .rotateYDegrees(alongFirst ^ facing.getAxis() == Direction.Axis.X ? 0.0f : 90.0f)
                .translate(0.0f, -0.5625f, 0.0f)
                .rotateXDegrees(-cogAngle)
                .translate(0.0f, 0.5625f, 0.0f)
                .uncenter()
                .setChanged();
    }

    // Get the rotation multiplier
    static float getRotationMultiplier(Direction.Axis gantryAxis, Direction facing) {
        float multiplier = 1.0f;
        if (gantryAxis == Direction.Axis.X && facing == Direction.UP) {
            multiplier *= -1.0f;
        }
        if (gantryAxis == Direction.Axis.Y && (facing == Direction.NORTH || facing == Direction.EAST)) {
            multiplier *= -1.0f;
        }
        return multiplier;
    }

    // Get the gantry axis
    private Direction.Axis getGantryAxis() {
        Direction.Axis gantryAxis = Direction.Axis.X;
        for (Direction.Axis axis : Iterate.axes) {
            if (axis == rotationAxis || axis == facing.getAxis()) {
                continue;
            }
            gantryAxis = axis;
        }
        return gantryAxis;
    }

    // Update the light
    @Override
    public void updateLight(float partialTick) {
        relight(new FlatLit[]{gantryCogs, rotatingModel, outputShaft});
    }

    // Delete the physics gantry carriage visual
    @Override
    protected void _delete() {
        super._delete();
        gantryCogs.delete();
        outputShaft.delete();
    }

    // Collect the crumbling instances
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(gantryCogs);
        consumer.accept(outputShaft);
    }
}

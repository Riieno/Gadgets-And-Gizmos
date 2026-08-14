package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlock;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlock;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerPlaneBlock;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlock;
import com.rieno.gadgetsandgizmos.content.EntityLauncherAnchorBlockEntity;
import com.rieno.gadgetsandgizmos.content.IndustrialMotorBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlockEntity;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.rieno.gadgetsandgizmos.content.ScissorPistonBlock;
import com.rieno.gadgetsandgizmos.content.ScissorPistonBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlock;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlock;
import com.rieno.gadgetsandgizmos.content.ThrusterBlock;
import com.rieno.gadgetsandgizmos.content.VariableTransmissionBlock;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import com.simibubi.create.api.contraption.transformable.MovedBlockTransformerRegistries;
import com.simibubi.create.api.schematic.state.SchematicStateFilterRegistry;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockRotation;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.index.SimBlockMovementChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Keep Create contraption movement and storage compatible with Sable-mounted addon blocks
public final class CTCreateContraptionCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT create contraption compat
    private CTCreateContraptionCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the common setup event
    public static void onCommonSetup(FMLCommonSetupEvent evt) {
        evt.enqueueWork(CTCreateContraptionCompat::register);
    }

    // Register the CT Create contraption compat
    private static void register() {
        BlockMovementChecks.registerMovementNecessaryCheck(CTCreateContraptionCompat::isMovementNecessary);
        BlockMovementChecks.registerMovementAllowedCheck(CTCreateContraptionCompat::isMovementAllowed);
        BlockMovementChecks.registerBrittleCheck(CTCreateContraptionCompat::isBrittle);
        BlockMovementChecks.registerAttachedCheck(CTCreateContraptionCompat::isAttachedTowards);
        SimBlockMovementChecks.registerAdditionalBlocks(CTCreateContraptionCompat::additionalLinkerPlanes);

        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.THRUSTER_BEARING.get(),
                (blockEntity, state) -> state
                        .setValue(SwivelBearingBlock.ASSEMBLED, false)
                        .setValue(BlockStateProperties.POWERED, false));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.THRUSTER.get(),
                (blockEntity, state) -> state
                        .setValue(ThrusterBlock.FOCUSED, false)
                        .setValue(ThrusterBlock.POWERED, false));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.BIDIRECTIONAL_GEARBOX.get(),
                (blockEntity, state) -> state
                        .setValue(BiDirectionalGearboxBlock.GYRO_MODE, false)
                        .setValue(BiDirectionalGearboxBlock.PASSTHROUGH_SPLIT, false));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.BI_DIRECTIONAL_GEARSHIFT.get(),
                (blockEntity, state) -> state
                        .setValue(BiDirectionalGearboxBlock.GYRO_MODE, false)
                        .setValue(BiDirectionalGearboxBlock.PASSTHROUGH_SPLIT, false));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.SCISSOR_PISTON.get(),
                (blockEntity, state) -> state.setValue(ScissorPistonBlock.ASSEMBLED, false));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.DOUBLE_BUTTON.get(),
                (blockEntity, state) -> resetDoubleButtonState(state));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.COPYCAT_DOUBLE_BUTTON.get(),
                (blockEntity, state) -> resetDoubleButtonState(state));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get(),
                (blockEntity, state) -> state.setValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB, false));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get(),
                (blockEntity, state) -> state.setValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB, false));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.SHIPPING_MANIFEST.get(),
                (blockEntity, state) -> state
                        .setValue(ShippingManifestBlock.SHOW_BLANK, false)
                        .setValue(ShippingManifestBlock.MANIFEST_COLOR, DyeColor.GREEN));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.VARIABLE_TRANSMISSION.get(),
                (blockEntity, state) -> state.setValue(VariableTransmissionBlock.POWER, 0));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.PHYSICS_GANTRY_SHAFT.get(),
                (blockEntity, state) -> state.setValue(PhysicsGantryShaftBlock.POWERED, false));
        SchematicStateFilterRegistry.REGISTRY.register(CTBlocks.INDUSTRIAL_MOTOR.get(),
                (blockEntity, state) -> state.setValue(IndustrialMotorBlock.POWERED, true));

        MovedBlockTransformerRegistries.BLOCK_TRANSFORMERS.register(CTBlocks.DOUBLE_BUTTON.get(),
                (state, transform) -> resetDoubleButtonState(transformState(state, transform)));
        MovedBlockTransformerRegistries.BLOCK_TRANSFORMERS.register(CTBlocks.COPYCAT_DOUBLE_BUTTON.get(),
                (state, transform) -> resetDoubleButtonState(transformState(state, transform)));
        MovedBlockTransformerRegistries.BLOCK_TRANSFORMERS.register(CTBlocks.THRUSTER_BEARING.get(),
                CTCreateContraptionCompat::transformThrusterBearingState);
        MovedBlockTransformerRegistries.BLOCK_TRANSFORMERS.register(CTBlocks.ANDESITE_CABLE.get(),
                FluidPipeBlockRotation::transform);
        MovedBlockTransformerRegistries.BLOCK_TRANSFORMERS.register(CTBlocks.BIDIRECTIONAL_GEARBOX.get(),
                CTCreateContraptionCompat::transformAxisFacingState);
        MovedBlockTransformerRegistries.BLOCK_TRANSFORMERS.register(CTBlocks.BI_DIRECTIONAL_GEARSHIFT.get(),
                CTCreateContraptionCompat::transformAxisFacingState);
        MovedBlockTransformerRegistries.BLOCK_TRANSFORMERS.register(CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get(),
                CTCreateContraptionCompat::transformCtrlState);
        MovedBlockTransformerRegistries.BLOCK_TRANSFORMERS.register(CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get(),
                CTCreateContraptionCompat::transformCtrlState);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check whether movement is necessary
    private static CheckResult isMovementNecessary(BlockState state, Level level, BlockPos pos) {
        Block block = state.getBlock();
        return block == CTBlocks.ROPE_KNOT.get() || block == CTBlocks.LAUNCHER_ENDPOINT.get()
                || block == CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get()
                ? CheckResult.SUCCESS
                : CheckResult.PASS;
    }

    // Check whether movement is allowed
    private static CheckResult isMovementAllowed(BlockState state, Level level, BlockPos pos) {
        Block block = state.getBlock();
        if (block == CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get()) {
            return CheckResult.SUCCESS;
        }
        if (isGeneratedAssemblyPart(block)) {
            return CheckResult.FAIL;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AileronBearingBlockEntity bearing
                && (bearing.isMountedAssemblyPresent(BearingHead.PRIMARY)
                || bearing.isMountedAssemblyPresent(BearingHead.SECONDARY))) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof VectorBearingBlockEntity bearing && bearing.isMountedAssemblyPresent()) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof ScissorPistonBlockEntity piston && piston.isMountedAssemblyPresent()) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof ThrusterBearingBlockEntity bearing && bearing.isAssembled()) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof PhysicsGantryCarriageBlockEntity carriage
                && (carriage.isSubLevelAssembled()
                || carriage.hasActiveAttachmentAnchor()
                || carriage.getAttachedShaftPos() != null)) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof PhysicsGantryShaftBlockEntity shaft && shaft.hasActiveAttachedCarriage()) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof PhysicsGantryBeltWheelBlockEntity wheel && wheel.hasLinkedTarget()) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof AdvancedContraptionControllerBlockEntity) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof AnalogueContraptionControllerBlockEntity controller
                && (!controller.canMoveWithCreateContraption() || controller.getEmbeddedSlabState() != null)) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof EntityLauncherAnchorBlockEntity anchor
                && (anchor.hasEntityTarget() || anchor.hasAnchorTarget() || anchor.hasMountedClaw())) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof PoweredZiplineBlockEntity zipline
                && zipline.hasCreateMovementSensitiveState()) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof ClawBlockEntity claw
                && (claw.isHolding() || claw.getPendingConnectorPos() != null)) {
            return CheckResult.FAIL;
        }
        if (blockEntity instanceof RopeStrandHolderBlockEntity ropeHolder) {
            RopeStrandHolderBehavior behavior = ropeHolder.getBehavior();
            if (behavior != null && behavior.isAttached()) {
                return CheckResult.FAIL;
            }
        }
        return CheckResult.PASS;
    }

    // Check if this is a generated assembly part
    private static boolean isGeneratedAssemblyPart(Block block) {
        return block == CTBlocks.THRUSTER_BEARING_LINK.get()
                || block == CTBlocks.AILERON_BEARING_LINK.get()
                || block == CTBlocks.VECTOR_BEARING_LINK.get()
                || block == CTBlocks.SCISSOR_PISTON_LINK.get()
                || block == CTBlocks.SCISSOR_PISTON_ARM.get()
                || block == CTBlocks.ENTITY_LAUNCHER_ANCHOR.get()
                || block == CTBlocks.LAUNCHER_ENDPOINT.get();
    }

    // Check whether the block is brittle
    private static CheckResult isBrittle(BlockState state) {
        Block block = state.getBlock();
        if (block == CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get()) {
            return CheckResult.FAIL;
        }
        return block == CTBlocks.DOUBLE_BUTTON.get() || block == CTBlocks.COPYCAT_DOUBLE_BUTTON.get()
                || block == CTBlocks.PHYSICS_GANTRY_CARRIAGE.get()
                ? CheckResult.SUCCESS
                : CheckResult.PASS;
    }

    // Check the attachment on the side
    private static CheckResult isAttachedTowards(BlockState state, Level level, BlockPos pos,
                                                  Direction dir) {
        Block block = state.getBlock();
        if (block == CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get()) {
            return CheckResult.of(ContraptionNetworkLinkerPlaneBlock.hasPlane(state, dir.getOpposite()));
        }
        if (block != CTBlocks.DOUBLE_BUTTON.get() && block != CTBlocks.COPYCAT_DOUBLE_BUTTON.get()
                && block != CTBlocks.PHYSICS_GANTRY_CARRIAGE.get()) {
            return CheckResult.PASS;
        }
        return CheckResult.of(dir == state.getValue(BlockStateProperties.FACING).getOpposite());
    }

    // Get the additional linker planes
    private static Iterable<BlockPos> additionalLinkerPlanes(BlockState state, Level level, BlockPos pos,
                                                              Set<BlockPos> gatheredBlocks) {
        List<BlockPos> planes = new ArrayList<>();
        if (state.getBlock() == CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get()) {
            for (Direction face : Direction.values()) {
                BlockPos ownerPos = pos.relative(face.getOpposite());
                if (ContraptionNetworkLinkerPlaneBlock.hasPlane(state, face)
                        && !gatheredBlocks.contains(ownerPos)) {
                    planes.add(ownerPos);
                }
            }
            return planes;
        }
        for (Direction face : Direction.values()) {
            BlockPos planePos = pos.relative(face);
            if (gatheredBlocks.contains(planePos)) {
                continue;
            }
            BlockState planeState = level.getBlockState(planePos);
            if (ContraptionNetworkLinkerPlaneBlock.hasPlane(planeState, face)) {
                planes.add(planePos);
            }
        }
        return planes;
    }

    // Reset the double button state
    private static BlockState resetDoubleButtonState(BlockState state) {
        return state
                .setValue(DoubleButtonBlock.TOP_POWERED, false)
                .setValue(DoubleButtonBlock.BOTTOM_POWERED, false);
    }

    // Transform the state
    private static BlockState transformState(BlockState state, StructureTransform transform) {
        if (transform.mirror != null) {
            state = state.mirror(transform.mirror);
        }
        if (transform.rotationAxis == Direction.Axis.Y) {
            state = state.rotate(transform.rotation);
        } else if (transform.rotationAxis != null) {
            state = state.setValue(BlockStateProperties.FACING,
                    transform.rotateFacing(state.getValue(BlockStateProperties.FACING)));
        }
        return state;
    }

    // Transform the axis facing state
    private static BlockState transformAxisFacingState(BlockState state, StructureTransform transform) {
        Direction facing = transform.rotateFacing(transform.mirrorFacing(state.getValue(BiDirectionalGearboxBlock.FACING)));
        return state
                .setValue(BlockStateProperties.AXIS, transform.rotateAxis(state.getValue(BlockStateProperties.AXIS)))
                .setValue(BiDirectionalGearboxBlock.FACING, facing);
    }

    // Transform the thruster bearing state
    private static BlockState transformThrusterBearingState(BlockState state, StructureTransform transform) {
        if (transform.mirror != null) {
            state = state.mirror(transform.mirror);
        }
        if (transform.rotationAxis == Direction.Axis.Y) {
            if (transform.rotation.ordinal() % 2 == 1) {
                state = state.cycle(ThrusterBearingBlock.AXIS_ALONG_FIRST_COORDINATE);
            }
            return state.rotate(transform.rotation);
        }

        Direction newFacing = transform.rotateFacing(state.getValue(BlockStateProperties.FACING));
        if (transform.rotationAxis == newFacing.getAxis() && transform.rotation.ordinal() % 2 == 1) {
            state = state.cycle(ThrusterBearingBlock.AXIS_ALONG_FIRST_COORDINATE);
        }
        return state.setValue(BlockStateProperties.FACING, newFacing);
    }

    // Transform the ctrl state
    private static BlockState transformCtrlState(BlockState state, StructureTransform transform) {
        Direction normal = state.getValue(AnalogueContraptionControllerBlock.FACING);
        Direction localZ = normal.getAxis().isVertical()
                ? state.getValue(AnalogueContraptionControllerBlock.HORIZONTAL_FACING)
                : Direction.UP;
        normal = transformDirection(normal, transform);
        localZ = transformDirection(localZ, transform);

        Direction horizontal = normal.getAxis().isVertical() ? localZ : normal;
        if (!horizontal.getAxis().isHorizontal()) {
            horizontal = Direction.NORTH;
        }
        return state
                .setValue(AnalogueContraptionControllerBlock.FACING, normal)
                .setValue(AnalogueContraptionControllerBlock.HORIZONTAL_FACING, horizontal);
    }

    // Transform the direction
    private static Direction transformDirection(Direction dir, StructureTransform transform) {
        return transform.rotateFacing(transform.mirrorFacing(dir));
    }

}

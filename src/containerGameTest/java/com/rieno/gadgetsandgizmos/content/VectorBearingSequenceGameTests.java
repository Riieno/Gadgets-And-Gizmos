package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.kinetics.KineticAngleHelper;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.Instruction;
import com.simibubi.create.content.kinetics.transmission.sequencer.InstructionSpeedModifiers;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Exercise Create's real sequencing and precise-angle mixins through the bearing shaft
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class VectorBearingSequenceGameTests{
    private VectorBearingSequenceGameTests(){}

    @GameTest(template = "empty_container", timeoutTicks = 110)
    public static void repeatedTurnsStopAtTheRequestedAngle(GameTestHelper ctx){
        var rig = rig(ctx, Direction.EAST, 128);
        ctx.runAtTickTime(15, () -> turn(rig, InstructionSpeedModifiers.FORWARD, 90));
        ctx.runAtTickTime(35, () -> {
            check(ctx, rig, 90);
            turn(rig, InstructionSpeedModifiers.FORWARD, 90);
        });
        ctx.runAtTickTime(55, () -> {
            check(ctx, rig, 180);
            turn(rig, InstructionSpeedModifiers.BACK, 45);
        });
        ctx.runAtTickTime(80, () -> {
            check(ctx, rig, 135);
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", timeoutTicks = 85)
    public static void doubleSpeedAndNegativeInputKeepTheTurnAngle(GameTestHelper ctx){
        var rig = rig(ctx, Direction.UP, -128);
        ctx.runAtTickTime(15, () -> turn(rig, InstructionSpeedModifiers.FORWARD_FAST, 35));
        ctx.runAtTickTime(35, () -> {
            check(ctx, rig, -35);
            turn(rig, InstructionSpeedModifiers.BACK_FAST, 35);
        });
        ctx.runAtTickTime(60, () -> {
            check(ctx, rig, 0);
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", timeoutTicks = 85)
    public static void consecutiveInstructionsKeepTheirAccumulatedAngle(GameTestHelper ctx){
        var rig = rig(ctx, Direction.NORTH, 32);
        ctx.runAtTickTime(15, () -> {
            var instructions = rig.gearshift().getInstructions();
            instructions.clear();
            instructions.add(new Instruction(SequencerInstructions.TURN_ANGLE, InstructionSpeedModifiers.FORWARD, 90));
            instructions.add(new Instruction(SequencerInstructions.DELAY, 3));
            instructions.add(new Instruction(SequencerInstructions.TURN_ANGLE, InstructionSpeedModifiers.BACK_FAST, 30));
            instructions.add(new Instruction(SequencerInstructions.END));
            rig.gearshift().run(0);
        });
        ctx.runAtTickTime(65, () -> {
            check(ctx, rig, -60);
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", timeoutTicks = 85)
    public static void savedTurnsFinishAfterReload(GameTestHelper ctx){
        var rig = rig(ctx, Direction.EAST, 16);
        ctx.runAtTickTime(15, () -> turn(rig, InstructionSpeedModifiers.FORWARD, 90));
        ctx.runAtTickTime(20, () -> {
            var saved = rig.gearshift().saveWithoutMetadata(ctx.getLevel().registryAccess());
            var pos = rig.gearshift().getBlockPos();
            var state = ctx.getLevel().getBlockState(pos);
            ctx.getLevel().removeBlockEntity(pos);
            var restored = AllBlockEntityTypes.SEQUENCED_GEARSHIFT.create(pos, state);
            restored.loadWithComponents(saved, ctx.getLevel().registryAccess());
            ctx.getLevel().setBlockEntity(restored);
        });
        ctx.runAtTickTime(65, () -> {
            check(ctx, rig, 90);
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", timeoutTicks = 115)
    public static void reductionGearsKeepTheirPhaseAcrossFullTurns(GameTestHelper ctx){
        var level = ctx.getLevel();
        var rig = rig(ctx, Direction.NORTH, 64);
        BlockPos smallPos = rig.gearshiftPos().north();
        BlockPos largePos = smallPos.offset(1, 1, 0);
        level.removeBlock(rig.bearing().getBlockPos(), false);
        level.setBlock(smallPos, AllBlocks.COGWHEEL.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
        level.setBlock(largePos, AllBlocks.LARGE_COGWHEEL.getDefaultState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
        BlockPos bearingPos = largePos.north();
        level.setBlock(bearingPos, CTBlocks.VECTOR_BEARING.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH), 3);
        var geared = new Rig(level, rig.gearshiftPos(), (VectorBearingBlockEntity)level.getBlockEntity(bearingPos));
        ctx.runAtTickTime(15, () -> turn(geared, InstructionSpeedModifiers.FORWARD, 90));
        for(int turn = 1; turn <= 4; turn++){
            int completed = turn;
            ctx.runAtTickTime(15 + turn * 20, () -> {
                check(ctx, geared, completed * 45.0D);
                if(completed < 4) turn(geared, InstructionSpeedModifiers.FORWARD, 90);
                else ctx.succeed();
            });
        }
    }

    private static Rig rig(GameTestHelper ctx, Direction facing, int rpm){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(5, 5, 5));
        BlockPos motorPos = pos.relative(facing.getOpposite());
        BlockPos shaftPos = pos.relative(facing);
        BlockPos bearingPos = shaftPos.relative(facing);
        level.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, facing), 3);
        ((CreativeMotorBlockEntity)level.getBlockEntity(motorPos)).generatedSpeed.setValue(rpm);
        level.setBlock(pos, AllBlocks.SEQUENCED_GEARSHIFT.getDefaultState()
                .setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, facing.getAxis().isVertical() ? Direction.Axis.X : facing.getAxis())
                .setValue(SequencedGearshiftBlock.VERTICAL, facing.getAxis().isVertical()), 3);
        level.setBlock(shaftPos, AllBlocks.SHAFT.getDefaultState()
                .setValue(BlockStateProperties.AXIS, facing.getAxis()), 3);
        level.setBlock(bearingPos, CTBlocks.VECTOR_BEARING.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, facing), 3);
        return new Rig(level, pos, (VectorBearingBlockEntity)level.getBlockEntity(bearingPos));
    }

    private static void turn(Rig rig, InstructionSpeedModifiers modifier, int degrees){
        var instructions = rig.gearshift().getInstructions();
        instructions.clear();
        instructions.add(new Instruction(SequencerInstructions.TURN_ANGLE, modifier, degrees));
        instructions.add(new Instruction(SequencerInstructions.END));
        rig.gearshift().run(0);
    }

    private static void check(GameTestHelper ctx, Rig rig, double expected){
        var saved = rig.gearshift().saveWithoutMetadata(ctx.getLevel().registryAccess());
        ctx.assertTrue(rig.gearshift().isIdle(), "Gearshift did not finish its turn: rpm=" + rig.gearshift().getSpeed()
                + ", progress=" + saved.getFloat("InstructionProgress") + ", timer=" + saved.getInt("Timer")
                + ", duration=" + saved.getInt("InstructionDuration"));
        double actual = rig.bearing().getShaftAngleDegrees();
        ctx.assertTrue(Math.abs(KineticAngleHelper.normalizeDegrees(actual - expected)) < 0.001D,
                "Bearing drifted: expected " + expected + " degrees, got " + actual);
    }

    private record Rig(ServerLevel level, BlockPos gearshiftPos, VectorBearingBlockEntity bearing){
        SequencedGearshiftBlockEntity gearshift(){
            return (SequencedGearshiftBlockEntity)level.getBlockEntity(gearshiftPos);
        }
    }
}

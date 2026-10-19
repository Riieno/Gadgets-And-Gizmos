package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.List;
import java.util.UUID;

// Exercise real assembly and disassembly on both signs of every shaft axis
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class PhysicsGantryAssemblyGameTests{
    private PhysicsGantryAssemblyGameTests(){}

    @GameTest(template = "empty_container", timeoutTicks = 60)
    public static void westShaftPreservesOrientation(GameTestHelper ctx) throws Exception{
        checkCycles(ctx, Direction.WEST);
    }

    @GameTest(template = "empty_container", timeoutTicks = 60)
    public static void eastShaftPreservesOrientation(GameTestHelper ctx) throws Exception{
        checkCycles(ctx, Direction.EAST);
    }

    @GameTest(template = "empty_container", timeoutTicks = 60)
    public static void downShaftPreservesOrientation(GameTestHelper ctx) throws Exception{
        checkCycles(ctx, Direction.DOWN);
    }

    @GameTest(template = "empty_container", timeoutTicks = 60)
    public static void upShaftPreservesOrientation(GameTestHelper ctx) throws Exception{
        checkCycles(ctx, Direction.UP);
    }

    @GameTest(template = "empty_container", timeoutTicks = 60)
    public static void northShaftPreservesOrientation(GameTestHelper ctx) throws Exception{
        checkCycles(ctx, Direction.NORTH);
    }

    @GameTest(template = "empty_container", timeoutTicks = 60)
    public static void southShaftPreservesOrientation(GameTestHelper ctx) throws Exception{
        checkCycles(ctx, Direction.SOUTH);
    }

    private static void checkCycles(GameTestHelper ctx, Direction shaftDirection) throws Exception{
        var level = ctx.getLevel();
        BlockPos shaftPos = ctx.absolutePos(new BlockPos(5, 10, 5));
        Direction facing = shaftDirection.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        BlockPos carriagePos = shaftPos.relative(facing);
        var state = CTBlocks.PHYSICS_GANTRY_CARRIAGE.get().defaultBlockState()
                .setValue(PhysicsGantryCarriageBlock.FACING, facing);
        if(PhysicsGantryCarriageBlock.getValidGantryShaftAxis(state) != shaftDirection.getAxis()){
            state = state.cycle(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE);
        }
        level.setBlock(shaftPos, CTBlocks.PHYSICS_GANTRY_SHAFT.get().defaultBlockState()
                .setValue(PhysicsGantryShaftBlock.FACING, shaftDirection), 3);
        level.setBlock(carriagePos, state, 3);
        BlockPos payloadPos = carriagePos.relative(PhysicsGantryCarriageBlockEntity.getMountedPayloadDirection(state));
        level.setBlock(payloadPos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);

        for(int cycle = 0; cycle < 3; cycle++){
            var carriage = (PhysicsGantryCarriageBlockEntity)level.getBlockEntity(carriagePos);
            ctx.assertTrue(carriage != null, "Carriage missing before assembly " + cycle);
            var alignment = PhysicsGantryCarriageBlockEntity.class.getDeclaredMethod("computeCarriageToShaftAlignment",
                    BlockState.class, BlockState.class, Direction.class, PhysicsGantryShaftBlockEntity.class);
            alignment.setAccessible(true);
            var relink = (Quaterniondc)alignment.invoke(carriage, state, level.getBlockState(shaftPos), facing,
                    level.getBlockEntity(shaftPos));
            assertIdentity(ctx, relink, "Manual relinking rotated its attachment on " + shaftDirection);
            call(carriage, "doSubLevelAssemble");
            UUID id = (UUID)field(carriage, "attachedSubLevelId");
            var body = (ServerSubLevel)SableLevelApi.subLevel(level, id);
            ctx.assertTrue(body != null, "Assembly failed on " + shaftDirection);
            try{
                var active = SimulatedHelper.findBlockEntityInSubLevel(body, body.getPlot().getCenterBlock(),
                        PhysicsGantryCarriageBlockEntity.class);
                ctx.assertTrue(active != null, "Assembled carriage missing");
                var target = (Quaterniondc)field(active, "lockedSubLevelOrientation");
                assertIdentity(ctx, target, "Assembly rotated its attachment on " + shaftDirection);
                assertIdentity(ctx, body.logicalPose().orientation(), "Assembly rotated its body on " + shaftDirection);
                call(active, "doSubLevelDisassemble");
                ctx.assertTrue(level.getBlockState(carriagePos).equals(state), "Disassembly changed the carriage state");
                ctx.assertTrue(level.getBlockState(payloadPos).is(Blocks.GOLD_BLOCK), "Disassembly moved the payload");
            }finally{
                if(SableLevelApi.subLevel(level, id) != null) SableSubLevelLifecycleApi.remove(level, List.of(body));
            }
        }
        level.removeBlock(payloadPos, false);
        level.removeBlock(carriagePos, false);
        level.removeBlock(shaftPos, false);
        ctx.runAtTickTime(5, ctx::succeed);
    }

    private static void assertIdentity(GameTestHelper ctx, Quaterniondc orientation, String message){
        ctx.assertTrue(Math.abs(new Quaterniond().dot(orientation)) > 1 - 1E-8, message + ": " + orientation);
    }

    private static Object field(Object receiver, String name) throws Exception{
        var field = receiver.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }

    private static void call(Object receiver, String name) throws Exception{
        var method = receiver.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(receiver);
    }
}

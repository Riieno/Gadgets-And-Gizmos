package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapterRegistry;
import com.rieno.gadgetsandgizmos.lib.probe.BlockStateDataAccess;
import com.rieno.gadgetsandgizmos.lib.probe.ScrollValueDataAdapter;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock;
import dev.simulated_team.simulated.index.SimBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

// Exercise real mixins, graph schemas, face routing and signal cleanup in the world and on a sub-level
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class GearshiftGraphControlGameTests{
    private GearshiftGraphControlGameTests(){}

    @GameTest(template = "empty_container", timeoutTicks = 100)
    public static void worldGearshiftControls(GameTestHelper ctx){
        exercise(ctx, ctx.getLevel(), ctx.absolutePos(new BlockPos(4, 3, 4)), null);
        ctx.succeed();
    }

    @GameTest(template = "empty_container", timeoutTicks = 100)
    public static void supplementalSettingsKeepDirectControl(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(pos, AllBlocks.FLUID_TANK.getDefaultState(), 2);
        var target = (FluidTankBlockEntity)level.getBlockEntity(pos);
        AtomicInteger applied = new AtomicInteger();
        var behaviour = new ScrollValueBehaviour(Component.literal("Setting"), target, null)
                .between(0, 10).withCallback(applied::set);
        BlockEntityDataAdapterRegistry.register(-500, new ScrollValueDataAdapter<>(FluidTankBlockEntity.class,
                candidate -> candidate == target ? Map.of("test_setting", behaviour) : Map.of()));
        var ports = AdvancedContraptionControllerBlockEntity.graphDataPorts(level, pos, true);
        ctx.assertTrue("number".equals(ports.getString("test_setting")), "Supplemental setting is missing");
        ctx.assertTrue("number".equals(ports.getString("direct_signal")), "Supplemental setting hid direct control");
        ctx.assertTrue(BlockEntityDataAdapterRegistry.write(target, "test_setting", GraphValue.number(100)),
                "Supplemental setting did not accept a graph write");
        ctx.assertTrue(applied.get() == 10, "Graph setting bypassed its clamp or callback");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", timeoutTicks = 100)
    public static void subLevelGearshiftControls(GameTestHelper ctx){
        var level = ctx.getLevel();
        var container = SubLevelContainer.getContainer(level);
        var body = (ServerSubLevel)container.allocateNewSubLevel(new Pose3d());
        try{
            var plot = body.getPlot();
            plot.newEmptyChunk(plot.getCenterChunk());
            BlockPos pos = plot.getCenterBlock().offset(4, 3, 4);
            var worldPos = ctx.absolutePos(new BlockPos(4, 3, 4)).getCenter();
            body.logicalPose().position().set(worldPos.x, worldPos.y, worldPos.z);
            body.logicalPose().rotationPoint().set(pos.getX(), pos.getY(), pos.getZ());
            body.updateLastPose();
            exercise(ctx, level, pos, body.getUniqueId());
            ctx.succeed();
        }finally{
            SableSubLevelLifecycleApi.remove(level, List.of(body));
        }
    }

    private static void exercise(GameTestHelper ctx, Level level, BlockPos pos, UUID subLevel){
        var block = SimBlocks.DIRECTIONAL_GEARSHIFT.get();
        BlockStateDataAccess.registerWritableProperty(block, "left_powered");
        BlockStateDataAccess.registerWritableProperty(block, "right_powered");
        for(Direction facing : Direction.values()){
            level.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.FACING, facing), 2);
            var target = level.getBlockEntity(pos);
            var ports = AdvancedContraptionControllerBlockEntity.graphDataPorts(level, pos, true);
            ctx.assertTrue("boolean".equals(ports.getString("left_powered"))
                    && "boolean".equals(ports.getString("right_powered")), "Missing graph gearshift inputs");
            BlockEntityDataAdapterRegistry.writeAll(target, Map.of("left_powered", GraphValue.bool(true),
                    "right_powered", GraphValue.bool(true)));
            check(ctx, level, pos, true, true);
            BlockEntityDataAdapterRegistry.writeAll(target, Map.of("left_powered", GraphValue.bool(false),
                    "right_powered", GraphValue.bool(false)));
            check(ctx, level, pos, false, false);
            BlockStateDataAccess.write(level, pos, "state_left_powered", GraphValue.bool(true));
            check(ctx, level, pos, true, false);
            BlockStateDataAccess.write(level, pos, "state_left_powered", GraphValue.bool(false));

            BlockPos left = pos.relative(facing);
            BlockPos right = pos.relative(facing.getOpposite());
            level.setBlock(left, CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get().defaultBlockState(), 2);
            level.setBlock(right, CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get().defaultBlockState(), 2);
            ((ContraptionNetworkLinkerPlaneBlockEntity)level.getBlockEntity(left))
                    .setPlane(facing, ContraptionNetworkLinkerData.LinkMode.OUTPUT);
            ((ContraptionNetworkLinkerPlaneBlockEntity)level.getBlockEntity(right))
                    .setPlane(facing.getOpposite(), ContraptionNetworkLinkerData.LinkMode.OUTPUT);
            String leftSource = "gearshift_test_left_" + facing;
            String rightSource = "gearshift_test_right_" + facing;
            try{
                ContraptionNetworkLinkerSignalBus.setPlaneSignal(level, subLevel, left, facing, leftSource, 1);
                check(ctx, level, pos, true, false);
                ContraptionNetworkLinkerSignalBus.setPlaneSignal(level, subLevel, right, facing.getOpposite(), rightSource, 15);
                check(ctx, level, pos, true, true);
                ContraptionNetworkLinkerSignalBus.clearSource(level, leftSource);
                check(ctx, level, pos, false, true);
                ContraptionNetworkLinkerSignalBus.clearSource(level, rightSource);
                check(ctx, level, pos, false, false);
            }finally{
                ContraptionNetworkLinkerSignalBus.clearSource(level, leftSource);
                ContraptionNetworkLinkerSignalBus.clearSource(level, rightSource);
                level.removeBlock(left, false);
                level.removeBlock(right, false);
            }
        }
    }

    private static void check(GameTestHelper ctx, Level level, BlockPos pos, boolean left, boolean right){
        var state = level.getBlockState(pos);
        ctx.assertTrue(state.getValue(DirectionalGearshiftBlock.LEFT_POWERED) == left
                && state.getValue(DirectionalGearshiftBlock.RIGHT_POWERED) == right,
                "Incorrect gearshift inputs: expected " + left + "/" + right + ", got " + state);
    }
}

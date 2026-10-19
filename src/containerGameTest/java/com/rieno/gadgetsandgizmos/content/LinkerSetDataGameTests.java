package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphTargetSignals;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.GraphSignalRange;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class LinkerSetDataGameTests{
    private LinkerSetDataGameTests(){}

    @GameTest(template = "empty_container", batch = "linker_overlap", timeoutTicks = 100)
    public static void replacingAndBreakingAnOverlappingBlockPreservesItsFaceBindings(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(4, 3, 4));
        BlockPos proxy = support.east();
        level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(proxy, CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get().defaultBlockState(), 3);
        ((ContraptionNetworkLinkerPlaneBlockEntity)level.getBlockEntity(proxy))
                .setPlane(Direction.EAST, ContraptionNetworkLinkerData.LinkMode.OUTPUT);
        var target = new ContraptionNetworkLinkerData.LinkedTarget(proxy, null,
                "createthrusters:contraption_network_linker_plane", "Test Plate",
                ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.FACE,
                List.of(new ContraptionNetworkLinkerData.LinkedFace(Direction.EAST, "East", "")));
        ItemStack linker = new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get());
        ContraptionNetworkLinkerData.writeTargets(linker, List.of(target), ContraptionNetworkLinkerData.LinkMode.OUTPUT);
        var tracker = ContraptionNetworkLinkerTracker.get(level.getServer());
        tracker.observeLinker(level, linker);
        var discovery = ContraptionNetworkLinkerData.toDiscoveryNodes(List.of(target)).getFirst();
        var reference = ContraptionNetworkLinkerData.directTargetForFace(discovery, Direction.EAST);
        var player = FakePlayerFactory.getMinecraft(level);
        ItemStack block = new ItemStack(CTItems.RCS_THRUSTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, block);
        var hit = new BlockHitResult(proxy.getCenter(), Direction.UP, proxy, false);
        var placement = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        ctx.assertTrue(level.getBlockState(proxy).canBeReplaced(placement), "Face plate blocks placement");
        ctx.assertTrue(CTItems.RCS_THRUSTER.get().place(placement).consumesAction(), "Overlapping block could not be placed");
        ctx.assertTrue(level.getBlockState(proxy).is(CTBlocks.RCS_THRUSTER.get()), "Placement did not occupy the face cell");
        ControllerSqliteStore.removeLinkedBlockAssignments(level, proxy, null, "createthrusters:contraption_network_linker_plane");
        tracker.clearLinks(level, (candidate, center) -> ContraptionNetworkLinkerTracker.matchesBrokenTarget(candidate, proxy, null));
        tracker.observeLinker(level, linker);
        ctx.assertTrue(ContraptionNetworkLinkerData.readTargets(linker).size() == 1, "Replacement removed the saved plate binding");
        ControllerRedstoneCompat.writeTarget(level, reference, level.getBlockEntity(proxy), "overlap_test", 1);
        ctx.assertTrue(level.getBestNeighborSignal(support) == 15, "Occupied plate did not signal its original support");
        ctx.assertTrue(level.getBestNeighborSignal(proxy) == 0, "Plate signaled the overlapping block");
        level.setBlock(support.above(), Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,
                        net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, true), 3);
        ctx.assertTrue(level.getBestNeighborSignal(proxy) == 15, "Ordinary strong redstone stopped propagating");
        level.removeBlock(support.above(), false);
        ctx.assertTrue(level.getBestNeighborSignal(proxy) == 0, "Removing real redstone left virtual plate feedback");
        ControllerSqliteStore.removeLinkedBlockAssignments(level, proxy, null, "createthrusters:rcs_thruster");
        level.removeBlock(proxy, false);
        tracker.observeLinker(level, linker);
        ctx.assertTrue(ContraptionNetworkLinkerData.readTargets(linker).size() == 1, "Breaking the occupant removed the plate");
        ctx.assertTrue(level.getBlockState(proxy).is(CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get()), "Plate placeholder was not restored");
        ControllerRedstoneCompat.clearSource(level, "overlap_test");
        BlockPos rcsPos = ctx.absolutePos(new BlockPos(4, 3, 7));
        level.setBlock(rcsPos, CTBlocks.RCS_THRUSTER.get().defaultBlockState(), 3);
        var blockTarget = new ContraptionNetworkLinkerData.LinkedTarget(rcsPos, null,
                "createthrusters:rcs_thruster", "Block RCS", ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        ItemStack blockLinker = new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get());
        ContraptionNetworkLinkerData.writeTargets(blockLinker, List.of(blockTarget), ContraptionNetworkLinkerData.LinkMode.OUTPUT);
        tracker.observeLinker(level, blockLinker);
        var blockNode = ContraptionNetworkLinkerData.toDiscoveryNodes(List.of(blockTarget)).getFirst();
        var nozzle = ContraptionNetworkLinkerData.directTargetForFace(blockNode, Direction.EAST);
        ControllerRedstoneCompat.writeTarget(level, nozzle, level.getBlockEntity(rcsPos), "block_rcs_test", 0.75f);
        ctx.assertTrue(Math.abs(((RcsThrusterBlockEntity)level.getBlockEntity(rcsPos)).getThrottle(Direction.EAST) - 0.75) < 0.001,
                "Block mode lost direct RCS nozzle control");
        ControllerRedstoneCompat.clearSource(level, "block_rcs_test");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "set_data_chain", timeoutTicks = 100)
    public static void differentChainedSettersCanRepeatedlyClearEveryTarget(GameTestHelper ctx) throws Exception{
        var controller = controller(ctx);
        var runtime = runtime(controller);
        var graph = new AdvancedGraphDocument();
        var on = event("on", "power_on");var off = event("off", "power_off");
        graph.nodes().add(on);graph.nodes().add(off);
        var first = target(ctx, "first", new BlockPos(4, 3, 4));
        var second = target(ctx, "second", new BlockPos(7, 3, 4));
        var on1 = setter("on1", first, 15);var on2 = setter("on2", second, 15);
        var off1 = setter("off1", first, 0);var off2 = setter("off2", second, 0);
        graph.nodes().addAll(List.of(on1, on2, off1, off2));
        edge(graph, on, on1);edge(graph, on1, on2);edge(graph, off, off1);edge(graph, off1, off2);
        var zero = new AdvancedGraphDocument.Node("zero", "constant_number", "", 0, 0, new CompoundTag());
        zero.data().putDouble("Value", 0);graph.nodes().add(zero);
        graph.edges().add(new AdvancedGraphDocument.Edge("wired_zero", zero.id(), "value", off2.id(), GraphSignalRange.REDSTONE_SIGNAL_PORT));
        runtime.compile(graph);
        for(int cycle = 0; cycle < 3; cycle++){
            runtime.enqueue("power_on");runtime.tick(graph, false);
            ctx.assertTrue(ctx.getLevel().getBestNeighborSignal(first.blockPos()) == 15, "First chained ON was lost");
            ctx.assertTrue(ctx.getLevel().getBestNeighborSignal(second.blockPos()) == 15, "Second chained ON was lost");
            runtime.enqueue("power_off");runtime.tick(graph, false);
            ctx.assertTrue(ctx.getLevel().getBestNeighborSignal(first.blockPos()) == 0, "First chained zero did not clear the earlier setter");
            ctx.assertTrue(ctx.getLevel().getBestNeighborSignal(second.blockPos()) == 0, "Second chained zero did not clear the earlier setter");
        }
        ContraptionNetworkLinkerSignalBus.setBlockSignal(ctx.getLevel(), null, first.blockPos(), "other_controller", 6);
        runtime.enqueue("power_on");runtime.tick(graph, false);
        runtime.enqueue("power_off");runtime.tick(graph, false);
        ctx.assertTrue(ctx.getLevel().getBestNeighborSignal(first.blockPos()) == 6, "A setter cleared another signal source");
        ContraptionNetworkLinkerSignalBus.clearSource(ctx.getLevel(), "other_controller");
        ctx.assertTrue(ctx.getLevel().getBestNeighborSignal(first.blockPos()) == 0, "Own assignment remained after clearing the other source");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "set_data_independent", timeoutTicks = 100)
    public static void mergedTargetsRetainIndependentRedstoneValues(GameTestHelper ctx) throws Exception{
        var controller = controller(ctx);var runtime = runtime(controller);
        var first = target(ctx, "first", new BlockPos(4, 3, 4));
        var second = target(ctx, "second", new BlockPos(7, 3, 4));
        var graph = new AdvancedGraphDocument();var event = event("event", "independent");
        var node = setter("multi", first, 9);
        ListTag targets = new ListTag();targets.add(first.toTag());targets.add(second.toTag());
        node.data().put(AdvancedGraphCatalog.DATA_TARGETS_TAG, targets);
        node.data().putBoolean(AdvancedGraphCatalog.MERGE_LIKE_PORTS_TAG, true);
        AdvancedContraptionControllerBlockEntity.applyDataTargetPortLayout(node, List.of(
                new GraphTargetPortLayout.Target(first.nodeId(), first.label(), List.of()),
                new GraphTargetPortLayout.Target(second.nodeId(), second.label(), List.of())), true);
        AdvancedGraphTargetSignals.upgrade(node, graph.edges());
        String left = signalPort(node, first.nodeId());String right = signalPort(node, second.nodeId());
        graph.nodes().addAll(List.of(event, node));edge(graph, event, node);
        for(var values : List.of(Map.of(left, 15, right, 7), Map.of(left, 0, right, 11), Map.of(left, 4, right, 0))){
            values.forEach((port, value) -> node.data().getCompound("Defaults").put(port, AdvancedGraphDocument.Value.number(value).toTag()));
            runtime.compile(graph);runtime.enqueue("independent");runtime.tick(graph, false);
            ctx.assertTrue(ctx.getLevel().getBestNeighborSignal(first.blockPos()) == values.get(left), "First per-target signal was not independent");
            ctx.assertTrue(ctx.getLevel().getBestNeighborSignal(second.blockPos()) == values.get(right), "Second per-target signal was not independent");
        }
        controller.setGraphTargetData(node, Set.of(left, right), port -> AdvancedGraphDocument.Value.number(0));
        ctx.succeed();
    }

    private static String signalPort(AdvancedGraphDocument.Node node, String target){
        return AdvancedGraphTargetSignals.ports(node).stream().filter(port -> target.equals(node.data()
                .getCompound(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG).getList(port, 10).getCompound(0).getString("Target")))
                .findFirst().orElseThrow();
    }

    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper ctx){
        BlockPos pos = ctx.absolutePos(new BlockPos(2, 3, 2));
        ctx.getLevel().setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        return (AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(pos);
    }

    private static GraphRuntime runtime(AdvancedContraptionControllerBlockEntity controller) throws Exception{
        var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");field.setAccessible(true);
        return (GraphRuntime)field.get(controller);
    }

    private static ControllerDiscoveryNode target(GameTestHelper ctx, String id, BlockPos relative){
        BlockPos pos = ctx.absolutePos(relative);ctx.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        return new ControllerDiscoveryNode(id, ControllerDiscoveryKind.UNKNOWN, "", "minecraft:stone", id, null, pos);
    }

    private static AdvancedGraphDocument.Node setter(String id, ControllerDiscoveryNode target, int value){
        CompoundTag data = AdvancedGraphNodeFactory.createDefaultData("set_block_data", null);
        data.put("TargetData", target.toTag());data.putString(GraphSignalRange.VALUE_TYPE_TAG, "redstone");
        CompoundTag defaults = data.getCompound("Defaults");
        defaults.put(GraphSignalRange.REDSTONE_SIGNAL_PORT, AdvancedGraphDocument.Value.number(value).toTag());
        data.put("Defaults", defaults);
        return new AdvancedGraphDocument.Node(id, "set_block_data", "", 0, 0, data);
    }

    private static AdvancedGraphDocument.Node event(String id, String event){
        CompoundTag data = AdvancedGraphNodeFactory.createDefaultData("event_trigger", null);data.putString("Event", event);
        return new AdvancedGraphDocument.Node(id, "event_trigger", "", 0, 0, data);
    }

    private static void edge(AdvancedGraphDocument graph, AdvancedGraphDocument.Node from, AdvancedGraphDocument.Node to){
        graph.edges().add(new AdvancedGraphDocument.Edge(from.id() + "_" + to.id(), from.id(), "exec", to.id(), "exec"));
    }
}

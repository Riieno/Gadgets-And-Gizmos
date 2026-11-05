package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticProvider;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.TorquePropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// Recreate saved servo networks with transmission blocks between each output and alternator
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class SmartGearboxReloadGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Restore intermediate shafts before the gearbox first ticks
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void shaftsSurviveServoReload(GameTestHelper helper){ exercise(helper, "shaft", false); }

    // Restore the generated sources before their saved consumers first tick
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void speedControllersSurviveServoReload(GameTestHelper helper){ exercise(helper, "controller", true); }

    // Keep encased transmission blocks intact across the same source reconstruction
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void chainDrivesSurviveServoReload(GameTestHelper helper){ exercise(helper, "chain", false); }

    // Restore complete belts and their pulley outputs through the same virtual source contract
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void beltPulleysSurviveServoReload(GameTestHelper helper){ exercise(helper, "belt", false); }

    // Rebuild an independent source after all saved network objects have been discarded
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void freshNetworksSurviveServoReload(GameTestHelper helper){ exercise(helper, "fresh", false); }

    // Resume controller writes before the saved consumers have reattached
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void earlyReversalSurvivesServoReload(GameTestHelper helper){ exercise(helper, "reverse", true); }

    // Reattach consumers whose saved speed predates a reversal in a neighbouring chunk
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void consumersSurviveChangedVirtualSource(GameTestHelper helper){ exercise(helper, "consumers", false); }

    // A source chunk may propagate rotation before the restored consumer gets its first tick
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void sourcePropagationPreparesUntickedConsumers(GameTestHelper helper){ exercise(helper, "consumers_sourcefirst", false); }

    // Keep speed controllers intact when they restore a previous direction
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void speedControllersSurviveChangedVirtualSource(GameTestHelper helper){ exercise(helper, "consumers_controller", true); }

    // Keep encased transmission blocks intact when they restore a previous direction
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void chainDrivesSurviveChangedVirtualSource(GameTestHelper helper){ exercise(helper, "consumers_chain", false); }

    // Saved pulley speeds must follow the current source rather than drive it backwards
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void beltPulleysSurviveChangedVirtualSource(GameTestHelper helper){ exercise(helper, "consumers_belt", true); }

    // Resolve through a live intermediate shaft before reconnecting a saved alternator
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void alternatorsSurviveChangedVirtualSource(GameTestHelper helper){ exercise(helper, "tails", false); }

    // A stopped virtual source must not be powered again by its saved consumers
    @GameTest(template = "smart_gearbox_reload_empty", timeoutTicks = 180)
    public static void stoppedVirtualSourcesStayStoppedOnReload(GameTestHelper helper){ exercise(helper, "consumers_stopped", false); }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Save while every output is moving and reverse it again after reconstruction
    private static void exercise(GameTestHelper helper, String kind, boolean gearboxFirst){
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        var level = helper.getLevel();
        List<BlockPos> positions = new ArrayList<>(); positions.add(center);
        level.setBlock(center, CTBlocks.BIDIRECTIONAL_GEARBOX.get().defaultBlockState(), 3);
        for(Direction dir : Direction.Plane.HORIZONTAL){
            BlockPos transmission = center.relative(dir);
            BlockState state = switch(kind){
                case "controller", "consumers_controller" -> AllBlocks.ROTATION_SPEED_CONTROLLER.getDefaultState().setValue(SpeedControllerBlock.HORIZONTAL_AXIS, dir.getAxis());
                case "chain", "consumers_chain" -> AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState().setValue(BlockStateProperties.AXIS, dir.getAxis());
                default -> AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, dir.getAxis());
            };
            level.setBlock(transmission, state, 3); positions.add(transmission);
            BlockPos alternator = center.relative(dir, 2);
            level.setBlock(alternator, CTBlocks.ALTERNATOR.get().defaultBlockState().setValue(AlternatorBlock.FACING, dir.getOpposite()), 3);
            positions.add(alternator);
            if(kind.endsWith("belt")){
                BlockPos end = transmission.above(2);
                level.setBlock(end, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, dir.getAxis()), 3);
                BeltConnectorItem.createBelts(level, transmission, end);
                BeltBlock.initBelt(level, transmission);
                positions.add(transmission.above()); positions.add(end);
            }
        }
        var gearbox = (BiDirectionalGearboxBlockEntity) level.getBlockEntity(center);
        for(Direction dir : Direction.Plane.HORIZONTAL){ gearbox.setFaceMaxAngle(dir, 360); gearbox.setManualFaceAngle(dir, 360); }
        var controller = new AdvancedContraptionControllerBlockEntity(helper.absolutePos(new BlockPos(1, 2, 1)),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(level);
        var setter = new AdvancedGraphDocument.Node("setter", "set_block_data", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("set_block_data", AdvancedGraphNodeFactory.Context.EMPTY));
        var target = new ControllerDiscoveryNode("gearbox", ControllerDiscoveryKind.MACHINE, "", "createthrusters:bidirectional_gearbox",
                "Smart Gearbox", null, center);
        setter.data().put("TargetData", target.toTag());
        CompoundTag inputs = new CompoundTag();
        for(Direction dir : Direction.Plane.HORIZONTAL) inputs.putString(dir.getName() + "_angle", "number");
        setter.data().put("DynamicInputs", inputs);
        writeAngles(helper, controller, setter, 360);
        helper.runAtTickTime(15, () -> {
            assertIntact(helper, positions, "before reload");
            var saved = positions.stream().map(pos -> level.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess())).toList();
            if(kind.equals("fresh")) discardNetworks(helper, positions);
            boolean partial = kind.startsWith("consumers") || kind.equals("tails");
            List<BlockPos> reloaded = partial ? positions.subList(1, positions.size()) : positions;
            if(kind.equals("tails")) reloaded = positions.stream().filter(pos -> pos.distManhattan(center) == 2).toList();
            for(BlockPos pos : reloaded) level.removeBlockEntity(pos);
            if(partial){
                for(Direction dir : Direction.Plane.HORIZONTAL){
                    double angle = kind.equals("consumers_stopped") ? saved.getFirst().getFloat("ServoAngle" + dir.name().substring(0, 1)) : -360;
                    gearbox.setManualFaceAngle(dir, angle);
                }
                gearbox.tick();
            }
            List<Integer> order = new ArrayList<>(); for(int idx = 0; idx < positions.size(); idx++) order.add(idx);
            List<BlockPos> restoredPositions = reloaded;
            order.removeIf(idx -> !restoredPositions.contains(positions.get(idx)));
            if(!gearboxFirst) Collections.reverse(order);
            for(int idx : order){
                BlockPos pos = positions.get(idx);
                BlockEntity restored = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved.get(idx), level.registryAccess());
                helper.assertTrue(restored != null, "Cannot restore " + pos + " from " + saved.get(idx));
                level.setBlockEntity(restored);
            }
            if(kind.equals("reverse")){
                var restored = (BiDirectionalGearboxBlockEntity) level.getBlockEntity(center);
                for(Direction dir : Direction.Plane.HORIZONTAL) restored.setManualFaceAngle(dir, -360);
            }
            if(kind.equals("consumers_sourcefirst")){
                var host = (VirtualKineticProvider) level.getBlockEntity(center);
                for(KineticBlockEntity source : host.ct$getVirtualKinetics()) source.attachKinetics();
                assertIntact(helper, positions, "before the restored consumers ticked");
            }
            // Tick in the requested order before ordinary world ticking resumes
            for(int idx : order) if(level.getBlockEntity(positions.get(idx)) instanceof KineticBlockEntity kinetic) kinetic.tick();
            assertIntact(helper, positions, "during first restored tick");
        });
        helper.runAtTickTime(20, () -> {
            if(!kind.equals("consumers_stopped")) return;
            for(Direction dir : Direction.Plane.HORIZONTAL){
                var alternator = (AlternatorBlockEntity) level.getBlockEntity(center.relative(dir, 2));
                helper.assertTrue(alternator != null && alternator.getTheoreticalSpeed() == 0, "Saved consumers restarted a stopped virtual output");
            }
        });
        helper.runAtTickTime(25, () -> {
            assertIntact(helper, positions, "after reload");
            writeAngles(helper, controller, setter, 360);
        });
        helper.runAtTickTime(40, () -> writeAngles(helper, controller, setter, -360));
        helper.runAtTickTime(60, () -> {
            assertIntact(helper, positions, "after reversing restored outputs");
            var restored = (BiDirectionalGearboxBlockEntity) level.getBlockEntity(center);
            for(Direction dir : Direction.Plane.HORIZONTAL){
                helper.assertTrue(restored.getManualAngle(dir) == -360, "Restored face lost its control value");
                var alternator = (AlternatorBlockEntity) level.getBlockEntity(center.relative(dir, 2));
                helper.assertTrue(Math.abs(alternator.getTheoreticalSpeed()) > 0, "Restored alternator is disconnected on " + dir);
            }
            helper.succeed();
        });
    }

    // Exercise the same saved target binding before and after replacing its block entity
    private static void writeAngles(GameTestHelper helper, AdvancedContraptionControllerBlockEntity controller,
                                    AdvancedGraphDocument.Node setter, double angle){
        Map<String, AdvancedGraphDocument.Value> vals = new java.util.HashMap<>();
        for(Direction dir : Direction.Plane.HORIZONTAL) vals.put(dir.getName() + "_angle", AdvancedGraphDocument.Value.number(angle));
        helper.assertTrue(controller.setGraphTargetData(setter, vals.keySet(), vals::get), "ACC could not control the restored gearbox");
    }

    // Fail at the first destroyed member instead of reporting a later missing control target
    private static void assertIntact(GameTestHelper helper, List<BlockPos> positions, String phase){
        for(BlockPos pos : positions) helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof KineticBlockEntity,
                "Kinetic block broke " + phase + " at " + pos + ": " + helper.getLevel().getBlockState(pos));
    }

    // Discard only this fixture's network objects to reproduce a server restart
    @SuppressWarnings("unchecked")
    private static void discardNetworks(GameTestHelper helper, List<BlockPos> positions){
        try{
            var field = TorquePropagator.class.getDeclaredField("networks");
            field.setAccessible(true);
            var networks = (Map<Object, Map<Long, ?>>) field.get(null);
            for(BlockPos pos : positions){
                var kinetic = (KineticBlockEntity) helper.getLevel().getBlockEntity(pos);
                if(kinetic.hasNetwork()) networks.get(helper.getLevel()).remove(kinetic.network);
            }
        }catch(ReflectiveOperationException ex){ throw new IllegalStateException(ex); }
    }
}

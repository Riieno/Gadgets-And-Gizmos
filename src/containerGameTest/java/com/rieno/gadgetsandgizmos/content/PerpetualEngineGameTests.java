package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.Map;
import java.util.UUID;

// Exercise advancement loading, real controller writes, layout validation and repeated graph execution
@GameTestHolder("createthrusters_perpetual_engine")
@PrefixGameTestTemplate(false)
public final class PerpetualEngineGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // The reference behaviour qualifies without comparing node ids, positions or graph serialization
    @GameTest(template = "empty", timeoutTicks = 220)
    public static void repeatingGraphAwardsTheEditor(GameTestHelper helper){
        var fixture = fixture(helper);
        var graph = new AdvancedGraphDocument();
        var interval = node("clock", "event_periodic");
        number(interval, "period", 32);
        var flip = node("toggle", "flip_flop");
        var select = node("choose", "data_branch");
        number(select, "true", 360);
        number(select, "false", -360);
        var setter = setter(fixture, "set_block_data");
        graph.nodes().addAll(java.util.List.of(interval, flip, select, setter));
        edge(graph, "clock", "exec", "toggle", "exec");
        edge(graph, "toggle", "exec", "setter", "exec");
        edge(graph, "toggle", "value", "choose", "condition");
        for(Direction dir : Direction.Plane.HORIZONTAL) edge(graph, "choose", "value", "setter", dir.getName() + "_angle");
        var runtime = new GraphRuntime(fixture.controller());
        for(int tick = 1; tick <= 180; tick++) helper.runAtTickTime(tick, () -> runtime.tick(graph));
        helper.runAtTickTime(100, () -> helper.assertTrue(!awarded(fixture), "Engine completed before two full cycles"));
        helper.runAtTickTime(190, () -> {
            helper.assertTrue(awarded(fixture), "The repeating graph did not award its editor: " + state(fixture));
            runtime.clear();
            finish(helper, fixture);
        });
    }

    // Separate writes for each face still count toward the same controlled gearbox
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void independentFaceWritesQualify(GameTestHelper helper){
        var fixture = fixture(helper);
        schedule(helper, fixture, 32, 5, true, "set_block_data");
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(awarded(fixture), "Independent face writes did not complete the sequence: " + state(fixture));
            finish(helper, fixture);
        });
    }

    // Generated target-local bindings must credit the matching gearbox in a multi-target node
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void multipleTargetsAndAliasesQualify(GameTestHelper helper){
        var fixture = fixture(helper);
        var setter = setter(fixture, "set_block_data");
        helper.setBlock(new BlockPos(8, 1, 4), CTBlocks.BIDIRECTIONAL_GEARBOX.get());
        var extra = new ControllerDiscoveryNode("extra", ControllerDiscoveryKind.MACHINE, "", "createthrusters:bidirectional_gearbox",
                "Other Gearbox", null, helper.absolutePos(new BlockPos(8, 1, 4)));
        ListTag targets = new ListTag();
        targets.add(setter.data().getCompound("TargetData").copy());
        targets.add(extra.toTag());
        setter.data().put("DataTargets", targets);
        CompoundTag bindings = new CompoundTag();
        for(Direction dir : Direction.Plane.HORIZONTAL){
            ListTag entries = new ListTag();
            for(String target : new String[]{"engine", "extra"}){
                CompoundTag binding = new CompoundTag();
                binding.putString("Target", target);
                binding.putString("Port", dir.getName() + "_angle");
                entries.add(binding);
            }
            bindings.put("alias_" + dir.getName(), entries);
        }
        setter.data().put("DataTargetBindings", bindings);
        for(int idx = 0; idx < 5; idx++){
            double angle = idx % 2 == 0 ? -360 : 360;
            helper.runAtTickTime(1 + idx * 32, () -> {
                Map<String, AdvancedGraphDocument.Value> values = new java.util.LinkedHashMap<>();
                for(Direction dir : Direction.Plane.HORIZONTAL) values.put("alias_" + dir.getName(), AdvancedGraphDocument.Value.number(angle));
                helper.assertTrue(fixture.controller().setGraphTargetData(setter, values.keySet(), values::get), "Multi-target writes failed");
            });
        }
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(awarded(fixture), "A multi-target node did not credit the matching gearbox");
            finish(helper, fixture);
        });
    }

    // A grouped MAP input must report its applied face fields to the same sequence matcher
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void groupedAngleFieldsQualify(GameTestHelper helper){
        var fixture = fixture(helper);
        var setter = setter(fixture, "set_block_data");
        CompoundTag fields = new CompoundTag();
        for(Direction dir : Direction.Plane.HORIZONTAL) fields.putString(dir.getName() + "_angle", "number");
        CompoundTag groups = new CompoundTag();
        groups.put("angles", fields);
        setter.data().put("DataPortGroups", groups);
        for(int idx = 0; idx < 5; idx++){
            double angle = idx % 2 == 0 ? -360 : 360;
            helper.runAtTickTime(1 + idx * 32, () -> {
                CompoundTag values = new CompoundTag();
                for(Direction dir : Direction.Plane.HORIZONTAL) values.put(dir.getName() + "_angle", AdvancedGraphDocument.Value.number(angle).toTag());
                helper.assertTrue(fixture.controller().setGraphTargetData(setter, java.util.Set.of("angles"),
                        port -> AdvancedGraphDocument.Value.map(values)), "Grouped angle writes failed");
            });
        }
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(awarded(fixture), "Grouped angles did not complete the sequence");
            finish(helper, fixture);
        });
    }

    // A nearby alternator whose shaft faces away from the gearbox is not connected
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void wrongAlternatorFacingDoesNotQualify(GameTestHelper helper){
        var fixture = fixture(helper);
        helper.setBlock(new BlockPos(5, 1, 4), CTBlocks.ALTERNATOR.get().defaultBlockState().setValue(AlternatorBlock.FACING, Direction.EAST));
        schedule(helper, fixture, 32, 5, true, "set_block_data");
        reject(helper, fixture, 140, "A disconnected alternator completed the advancement");
    }

    // Three alternators cannot satisfy the four-face layout
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void missingAlternatorDoesNotQualify(GameTestHelper helper){
        var fixture = fixture(helper);
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.AIR);
        schedule(helper, fixture, 32, 5, true, "set_block_data");
        reject(helper, fixture, 140, "An incomplete layout completed the advancement");
    }

    // The timing is measured between applied angle changes rather than graph execution pulses
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void sixteenTickReversalsDoNotQualify(GameTestHelper helper){
        var fixture = fixture(helper);
        schedule(helper, fixture, 16, 9, true, "set_block_data");
        reject(helper, fixture, 140, "Sixteen-tick reversals completed the advancement");
    }

    // One controlled face cannot stand in for the other three output faces
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void oneFaceDoesNotQualify(GameTestHelper helper){
        var fixture = fixture(helper);
        schedule(helper, fixture, 32, 5, false, "set_block_data");
        reject(helper, fixture, 140, "One oscillating face completed the advancement");
    }

    // A different graph output path must not masquerade as a Set Data node
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void otherOutputNodesDoNotQualify(GameTestHelper helper){
        var fixture = fixture(helper);
        schedule(helper, fixture, 32, 5, true, "direct_target_output");
        reject(helper, fixture, 140, "A different output node completed the advancement");
    }

    // Restoring a broken layout starts a fresh two-cycle observation window
    @GameTest(template = "empty", timeoutTicks = 270)
    public static void brokenLayoutsDiscardEarlierReversals(GameTestHelper helper){
        var fixture = fixture(helper);
        schedule(helper, fixture, 32, 8, true, "set_block_data");
        helper.runAtTickTime(70, () -> helper.setBlock(new BlockPos(4, 1, 3), Blocks.AIR));
        helper.runAtTickTime(80, () -> helper.setBlock(new BlockPos(4, 1, 3), CTBlocks.ALTERNATOR.get().defaultBlockState()
                .setValue(AlternatorBlock.FACING, Direction.SOUTH)));
        helper.runAtTickTime(140, () -> helper.assertTrue(!awarded(fixture), "The broken layout retained previous reversals"));
        helper.runAtTickTime(235, () -> {
            helper.assertTrue(awarded(fixture), "The repaired engine did not qualify after fresh cycles: " + state(fixture));
            finish(helper, fixture);
        });
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Fixture fixture(GameTestHelper helper){
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, CTBlocks.BIDIRECTIONAL_GEARBOX.get());
        var gearbox = (BiDirectionalGearboxBlockEntity)helper.getBlockEntity(pos);
        for(Direction dir : Direction.Plane.HORIZONTAL){
            helper.setBlock(pos.relative(dir), CTBlocks.ALTERNATOR.get().defaultBlockState()
                    .setValue(AlternatorBlock.FACING, dir.getOpposite()));
            gearbox.setFaceMaxAngle(dir, 360);
            gearbox.setManualFaceAngle(dir, -360);
        }
        var controller = new AdvancedContraptionControllerBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(helper.getLevel());
        var profile = new GameProfile(UUID.randomUUID(), "EngineEditor");
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
        player.connection = new FakePlayer(helper.getLevel(), profile).connection;
        players(helper).put(player.getUUID(), player);
        player.getAdvancements();
        PerpetualEngineAdvancement.recordEditor(controller, player);
        return new Fixture(controller, gearbox, player);
    }

    private static AdvancedGraphDocument.Node setter(Fixture fixture, String type){
        var setter = node("setter", type);
        var target = new ControllerDiscoveryNode("engine", ControllerDiscoveryKind.MACHINE, "", "createthrusters:bidirectional_gearbox",
                "Smart Gearbox", null, fixture.gearbox().getBlockPos());
        setter.data().put("TargetData", target.toTag());
        CompoundTag inputs = new CompoundTag();
        for(Direction dir : Direction.Plane.HORIZONTAL) inputs.putString(dir.getName() + "_angle", "number");
        setter.data().put("DynamicInputs", inputs);
        return setter;
    }

    private static void schedule(GameTestHelper helper, Fixture fixture, int period, int pulses, boolean allFaces, String type){
        var setter = setter(fixture, type);
        for(int idx = 0; idx < pulses; idx++){
            double angle = idx % 2 == 0 ? -360 : 360;
            helper.runAtTickTime(1 + idx * period, () -> {
                for(Direction dir : Direction.Plane.HORIZONTAL){
                    if(!allFaces && dir != Direction.NORTH) continue;
                    Map<String, AdvancedGraphDocument.Value> values = Map.of(dir.getName() + "_angle", AdvancedGraphDocument.Value.number(angle));
                    helper.assertTrue(fixture.controller().setGraphTargetData(setter, values.keySet(), values::get), "The fixture could not write a gearbox angle");
                }
            });
        }
    }

    private static boolean awarded(Fixture fixture){
        var advancement = fixture.player().getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath("createthrusters", "perpetual_engine"));
        return advancement != null && fixture.player().getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void reject(GameTestHelper helper, Fixture fixture, int tick, String msg){
        helper.runAtTickTime(tick, () -> {
            helper.assertTrue(!awarded(fixture), msg);
            finish(helper, fixture);
        });
    }

    private static void finish(GameTestHelper helper, Fixture fixture){
        players(helper).remove(fixture.player().getUUID());
        fixture.controller().setRemoved();
        helper.succeed();
    }

    // Include retained sequence and loaded layout state when an end-to-end assertion fails
    private static String state(Fixture fixture){
        try{
            var field = PerpetualEngineAdvancement.class.getDeclaredField("PROGRESS");
            field.setAccessible(true);
            Object targets = ((Map<?, ?>)field.get(null)).get(fixture.controller());
            StringBuilder msg = new StringBuilder("targets=" + targets + ", servo=" + fixture.gearbox().isServoModeActive());
            if(targets instanceof Map<?, ?> map){
                for(Object progress : map.values()){
                    var sequenceField = progress.getClass().getDeclaredField("sequence");
                    sequenceField.setAccessible(true);
                    Object sequence = sequenceField.get(progress);
                    var samples = sequence.getClass().getDeclaredField("samples");
                    samples.setAccessible(true);
                    msg.append(", samples=").append(samples.get(sequence));
                }
            }
            for(Direction dir : Direction.Plane.HORIZONTAL){
                msg.append(", ").append(dir).append('=').append(fixture.gearbox().getLevel().getBlockState(fixture.gearbox().getBlockPos().relative(dir)))
                        .append('/').append(fixture.gearbox().getManualAngle(dir)).append('/').append(fixture.gearbox().getFaceMaxAngle(dir));
            }
            return msg.toString();
        }catch(ReflectiveOperationException ex){ throw new IllegalStateException(ex); }
    }

    // Supply authoritative UUID lookup without invoking optional mods' client login protocols
    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> players(GameTestHelper helper){
        try{
            var field = PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);
            return (Map<UUID, ServerPlayer>)field.get(helper.getLevel().getServer().getPlayerList());
        }catch(ReflectiveOperationException ex){ throw new IllegalStateException(ex); }
    }

    private static AdvancedGraphDocument.Node node(String id, String type){
        return new AdvancedGraphDocument.Node(id, type, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
    }

    private static void number(AdvancedGraphDocument.Node node, String port, double val){
        CompoundTag defaults = node.data().getCompound("Defaults");
        defaults.put(port, AdvancedGraphDocument.Value.number(val).toTag());
        node.data().put("Defaults", defaults);
    }

    private static void edge(AdvancedGraphDocument graph, String from, String output, String to, String input){
        graph.edges().add(new AdvancedGraphDocument.Edge("edge_" + graph.edges().size(), from, output, to, input));
    }

    private record Fixture(AdvancedContraptionControllerBlockEntity controller, BiDirectionalGearboxBlockEntity gearbox, ServerPlayer player){}
}

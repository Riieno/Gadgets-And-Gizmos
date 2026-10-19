package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewTrackingTargets;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import org.joml.Quaterniond;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class AccRequestedFixesGameTests{
    private AccRequestedFixesGameTests(){}

    @GameTest(template = "empty_container", batch = "acc_requested_fixes")
    public static void hudObserversDoNotPartitionTheUpstreamFlipFlop(GameTestHelper ctx){
        var controller = controller(ctx);
        var graph = new AdvancedGraphDocument();
        var event = add(graph, "pulse", "event_trigger");
        event.data().putString("Event", "pulse");
        add(graph, "flip", "flip_flop");
        add(graph, "invert", "not");
        add(graph, "hud", "hud_element");
        edge(graph, "pulse", "exec", "flip", "exec");
        edge(graph, "flip", "value", "invert", "value");
        edge(graph, "invert", "value", "hud", "visible");
        var runtime = new GraphRuntime(controller);
        runtime.compile(graph);
        var players = List.of(UUID.randomUUID(), UUID.randomUUID());
        for(int pulse = 1; pulse <= 6; pulse++){
            runtime.enqueue("pulse", players.get(pulse % 2));
            runtime.tick(graph, false);
            boolean expected = pulse % 2 == 1;
            ctx.assertTrue(runtime.liveOutput("flip", "value").asBoolean() == expected,
                    "HUD observation changed flip-flop pulse " + pulse);
            var before = runtime.createShutdownSnapshot();
            for(UUID player : players){
                var values = runtime.playerHudValues(player);
                ctx.assertTrue(values.inputs().get("hud:visible").asBoolean() != expected,
                        "HUD did not receive the upstream value");
            }
            ctx.assertTrue(before.equals(runtime.createShutdownSnapshot()), "HUD sampling mutated runtime state");
        }
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "acc_requested_fixes")
    public static void removedGlassesPairsStayRevokedWhenTheControllerIsSaved(GameTestHelper ctx){
        var controller = controller(ctx);
        UUID removed = UUID.randomUUID(), kept = UUID.randomUUID();
        controller.registerGogglesTrackerPair(removed, "Glasses");
        controller.registerGogglesTrackerPair(kept, "Pilot hat");
        ctx.assertTrue(controller.unregisterGogglesTrackerPair(removed), "Bound glasses were not removed");
        ctx.assertTrue(!controller.unregisterGogglesTrackerPair(removed), "Missing pair was removed twice");
        var metadata = controller.saveWithFullMetadata(ctx.getLevel().registryAccess());
        var manifest = ControllerManifestStore.loadControllerFromMetadata(metadata, ctx.getLevel());
        ctx.assertTrue(manifest != null, "Controller manifest was not saved");
        var saved = manifest.controllerData();
        ctx.assertTrue(!AdvancedContraptionControllerBlockEntity.controllerDataHasGogglesTrackerPair(saved, removed),
                "Removed pair remained saved");
        ctx.assertTrue(AdvancedContraptionControllerBlockEntity.controllerDataHasGogglesTrackerPair(saved, kept),
                "Unbinding removed a different pair");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "acc_requested_fixes")
    public static void cameraFiltersAcquirePassiveAndHostileMobs(GameTestHelper ctx){
        var level = ctx.getLevel();
        var cow = EntityType.COW.create(level);
        var zombie = EntityType.ZOMBIE.create(level);
        var first = ctx.absolutePos(new BlockPos(3, 4, 2));
        var second = ctx.absolutePos(new BlockPos(5, 4, 2));
        cow.setPos(first.getCenter());
        zombie.setPos(second.getCenter());
        cow.setNoAi(true);
        zombie.setNoAi(true);
        level.addFreshEntity(cow);
        level.addFreshEntity(zombie);
        try{
            var origin = ctx.absolutePos(new BlockPos(4, 5, 7)).getCenter();
            var pose = new ViewPose(origin, new Quaterniond(), 70);
            for(var mob : List.of(cow, zombie)){
                var category = mob == cow ? "passive_mobs" : "hostile_mobs";
                var targets = ViewTrackingTargets.acquire(level, pose, 16,
                        new ViewRaycast.Filter(Set.of(category), true), null, List.of());
                ctx.assertTrue(targets.stream().anyMatch(target -> mob.getUUID().equals(target.key().playerId())),
                        "Camera did not acquire " + category);
            }
        }finally{
            cow.discard();
            zombie.discard();
        }
        ctx.succeed();
    }

    private static AdvancedContraptionControllerBlockEntity controller(GameTestHelper ctx){
        var pos = ctx.absolutePos(new BlockPos(2, 3, 2));
        ctx.getLevel().setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        return (AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(pos);
    }

    private static AdvancedGraphDocument.Node add(AdvancedGraphDocument graph, String id, String type){
        var node = new AdvancedGraphDocument.Node(id, type, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY));
        graph.nodes().add(node);
        return node;
    }

    private static void edge(AdvancedGraphDocument graph, String from, String output, String to, String input){
        graph.edges().add(new AdvancedGraphDocument.Edge(from + output + to + input, from, output, to, input));
    }
}

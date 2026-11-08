package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.PhysicsBodyAvailability;
import com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi;
import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicsBodyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

// Exercise the native unload window while Java's removed flag is still false
@GameTestHolder("createthrusters_scm_unload")
@PrefixGameTestTemplate(false)
public final class ScmUnloadGameTests{
    // A late cleanup wake must never reach a body which Rapier has already removed
    @GameTest(template = "empty", batch = "unload", timeoutTicks = 100)
    public static void lateWakesSkipUnloadedNativeBodies(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            helper.assertTrue(fixture.loaded(), "The loaded ship was unavailable before unload");
            fixture.pipeline.wakeUp(fixture.body);
            fixture.unload();
            helper.assertTrue(!fixture.body.isRemoved(), "The fixture did not preserve the real unload window");
            helper.assertTrue(!fixture.loaded(), "The unloaded native body still appeared loaded");
            fixture.pipeline.wakeUp(fixture.body);
            SableConstraintApi.wakeUp(fixture.pipeline, fixture.body);
            helper.assertTrue(SableConstraintApi.addConstraint(fixture.pipeline, null, fixture.body,
                    SableConstraintApi.fixedConfiguration(new Vector3d(), new Vector3d(), new Quaterniond())) == null,
                    "The controller created a constraint on an unloaded body");
            fixture.reload();
            helper.assertTrue(fixture.loaded(), "The returning ship remained unavailable");
            fixture.pipeline.wakeUp(fixture.body);
        }
        helper.succeed();
    }

    // A valid request queued before unload must be discarded and rechecked at dispatch
    @GameTest(template = "empty", batch = "unload", timeoutTicks = 100)
    public static void queuedWakesCannotOutliveTheirBody(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            boolean prev = SubLevelPhysicsSystem.IN_PHYSICS_STEP;
            try{
                SubLevelPhysicsSystem.IN_PHYSICS_STEP = true;
                fixture.pipeline.wakeUp(fixture.body);
            }finally{ SubLevelPhysicsSystem.IN_PHYSICS_STEP = prev; }
            List<PhysicsPipelineBody> queued = queued(fixture.pipeline);
            helper.assertTrue(queued.stream().anyMatch(body -> body == fixture.body), "The fixture did not queue its wake");
            fixture.unload();
            helper.assertTrue(queued.stream().noneMatch(body -> body == fixture.body), "Unload retained a queued wake");
            queued.add(fixture.body);
            fixture.pipeline.physicsTick(.001);
            helper.assertTrue(queued.isEmpty(), "The dispatch guard failed to retire a stale wake");
            fixture.reload();
        }
        helper.succeed();
    }

    // Hovering controls without a schedule must stop before cleanup marks the controller removed
    @GameTest(template = "empty", batch = "unload", timeoutTicks = 100)
    public static void accAndScmStopWhileTheirNativeShipIsUnloaded(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            var ctrl = fixture.controller;
            helper.assertTrue(ctrl.isControllerRuntimeLoaded() && !ctrl.hasShippingSchedule(),
                    "The unscheduled controller fixture was not ready");
            fixture.runtime.tick();
            fixture.unload();
            helper.assertTrue(!ctrl.isRemoved() && !ctrl.isControllerRuntimeLoaded(),
                    "ACC trusted a Java controller whose native ship was gone");
            set(fixture.runtime, "goggleControlTelemetry", null);
            fixture.runtime.tick();
            helper.assertTrue(field(fixture.runtime, "goggleControlTelemetry") == null,
                    "The SCM continued running controls on an unloaded ship");
            ctrl.sable$physicsTick(fixture.body, RigidBodyHandle.of(fixture.body), .05);
            ctrl.onChunkUnloaded();
            fixture.reload();
            helper.assertTrue(ctrl.isControllerRuntimeLoaded(), "Reload did not restore the ACC loaded check");
            fixture.runtime.tick();
            helper.assertTrue(field(fixture.runtime, "goggleControlTelemetry") != null,
                    "SCM control did not resume after its body returned");
        }
        helper.succeed();
    }

    // A missing controller chunk must stop control even while the ship itself is still loaded
    @GameTest(template = "empty", batch = "unload", timeoutTicks = 100)
    public static void missingControllerChunkCannotKeepControlsAlive(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            var plot = fixture.body.getPlot();
            var pos = fixture.controller.getBlockPos();
            var holder = plot.getChunkHolder(plot.toLocal(new ChunkPos(pos)));
            var field = dev.ryanhcode.sable.sublevel.plot.LevelPlot.class.getDeclaredField("chunks");
            field.setAccessible(true);
            var chunks = (Object[])field.get(plot);
            int idx = -1;
            for(int val = 0; val < chunks.length; val++) if(chunks[val] == holder){ idx = val; break; }
            helper.assertTrue(idx >= 0, "The fixture controller chunk was not registered");
            chunks[idx] = null;
            try{
                helper.assertTrue(fixture.loaded(), "The ship fixture lost its loaded native body");
                helper.assertTrue(!fixture.controller.isControllerRuntimeLoaded(),
                        "ACC accepted a stale block entity outside its loaded chunk contents");
                set(fixture.runtime, "goggleControlTelemetry", null);
                fixture.runtime.tick();
                helper.assertTrue(field(fixture.runtime, "goggleControlTelemetry") == null,
                        "SCM ticked after its controller left the loaded chunk");
            }finally{ chunks[idx] = holder; }
            helper.assertTrue(fixture.controller.isControllerRuntimeLoaded(), "The restored controller remained unavailable");
        }
        helper.succeed();
    }

    // Repeated unload and return must not retain requests from the previous native registration
    @GameTest(template = "empty", batch = "unload", timeoutTicks = 100)
    public static void repeatedHoverUnloadAndReloadRemainsSafe(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            for(int idx = 0; idx < 12; idx++){
                fixture.pipeline.wakeUp(fixture.body);
                fixture.unload();
                fixture.controller.tick();
                fixture.runtime.tick();
                fixture.pipeline.wakeUp(fixture.body);
                fixture.reload();
                helper.assertTrue(fixture.loaded() && fixture.controller.isControllerRuntimeLoaded(),
                        "A repeated native registration failed at cycle " + idx);
            }
        }
        helper.succeed();
    }

    // Exercise the real container unload callback on an initialized ship with no shipping schedule
    @GameTest(template = "empty", batch = "unload", timeoutTicks = 100)
    public static void initializedUnscheduledShipCanUnloadNormally(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            var level = helper.getLevel();
            var map = new ShipControlMap(UUID.randomUUID(), level.dimension().location().toString(),
                    fixture.body.getUniqueId(), fixture.controller.getBlockPos(), Vec3.ZERO,
                    List.of(), List.of(), List.of(), List.of(), List.of(), 0);
            ShipControlMapStore.save(level, map);
            fixture.runtime.setMapId(map.id());
            fixture.runtime.tick();
            helper.assertTrue(!fixture.controller.hasShippingSchedule(), "The fixture unexpectedly had a schedule");
            SubLevelContainer.getContainer(level).removeSubLevel(fixture.body, SubLevelRemovalReason.UNLOADED);
            int chunks = level.getChunkSource().getLoadedChunksCount();
            fixture.controller.tick();
            fixture.runtime.tick();
            fixture.pipeline.wakeUp(fixture.body);
            SableConstraintApi.wakeUp(fixture.pipeline, fixture.body);
            helper.assertTrue(!SablePhysicsBodyApi.isLoaded(fixture.body)
                            && !fixture.controller.isControllerRuntimeLoaded(),
                    "A fully unloaded SCM ship remained available for controls");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == chunks,
                    "Checking the unloaded ship requested a chunk");
        }
        helper.succeed();
    }

    // Remove native residency without deleting the Java sublevel or its loaded controller chunk
    private static final class Fixture implements AutoCloseable{
        private final GameTestHelper helper;
        private final ServerSubLevel body;
        private final PhysicsPipeline pipeline;
        private final AdvancedContraptionControllerBlockEntity controller;
        private final ShipControlModuleRuntime runtime;
        private final Pose3d pose;
        private boolean removed;

        private Fixture(GameTestHelper helper) throws Exception{
            this.helper = helper;
            body = ShipDoorGameTests.newBody(helper);
            var view = body.getPlot().getEmbeddedLevelAccessor();
            view.setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3);
            view.setBlock(new BlockPos(0, 1, 0), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
            view.setBlock(new BlockPos(0, 2, 0), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
            controller = (AdvancedContraptionControllerBlockEntity)body.getLevel()
                    .getBlockEntity(body.getPlot().getCenterBlock().offset(0, 2, 0));
            runtime = (ShipControlModuleRuntime)field(controller, "shipControlRuntime");
            pipeline = SubLevelContainer.getContainer(helper.getLevel()).physicsSystem().getPipeline();
            pose = new Pose3d(body.logicalPose());
            helper.assertTrue(pipeline instanceof PhysicsBodyAvailability, "The native availability mixin did not apply");
        }

        private boolean loaded(){ return SablePhysicsBodyApi.isLoaded(body); }
        private void unload(){ pipeline.remove(body); removed = true; }
        private void reload(){ pipeline.add(body, pose); removed = false; }

        @Override public void close(){
            if(SubLevelContainer.getContainer(helper.getLevel()).getSubLevel(body.getUniqueId()) != body) return;
            if(removed) reload();
            SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<PhysicsPipelineBody> queued(PhysicsPipeline pipeline) throws Exception{
        return (List<PhysicsPipelineBody>)field(pipeline, "queuedWakeUps");
    }

    private static Object field(Object owner, String name) throws Exception{
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void set(Object owner, String name, Object value) throws Exception{
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}

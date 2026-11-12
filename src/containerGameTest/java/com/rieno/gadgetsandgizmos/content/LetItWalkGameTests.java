package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.compat.letitwalk.LetItWalkSupport;
import com.rieno.gadgetsandgizmos.compat.letitwalk.LetItWalkCompatibility;
import com.rieno.gadgetsandgizmos.lib.graph.GraphApi;
import com.rieno.gadgetsandgizmos.lib.graph.GraphExecutionContext;
import com.rieno.gadgetsandgizmos.lib.graph.GraphHostServices;
import com.rieno.gadgetsandgizmos.lib.graph.GraphServiceKey;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.navigation.TrafficCoordinator;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelArchiveApi;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelArchiveStore;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPilotControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmTarget;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.letitwalk.ModContent;
import dev.letitwalk.content.LegJointBlockEntity;
import dev.letitwalk.content.PilotControlsBlockEntity;
import dev.letitwalk.content.WalkerCoreBlock;
import dev.letitwalk.content.WalkerCoreBlockEntity;
import dev.letitwalk.walker.BuiltLeg;
import dev.letitwalk.walker.LegRuntime;
import dev.letitwalk.walker.WalkerBrain;
import dev.letitwalk.walker.WalkerFlight;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.joml.Vector3d;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Exercise native walker topology, storage, pilot ownership and graph execution
@GameTestHolder("createthrusters_let_it_walk")
@PrefixGameTestTemplate(false)
public final class LetItWalkGameTests{
    // Discover the complete walker from every limb without collecting nearby unrelated bodies
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void eachLimbFindsTheWholeWalker(GameTestHelper helper){
        try(var fixture = new Fixture(helper)){
            for(var body : fixture.bodies){
                helper.assertTrue(SableAssemblyTopologyApi.discover(body).loadedBodyIds().equals(fixture.ids()),
                        "Selecting a limb failed to find the whole walker");
            }
        }
        helper.succeed();
    }

    // Store native joints and original plot addresses with the whole assembly
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 200)
    public static void archiveRoundTripRetainsNativeJoints(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            var expected = fixture.core.saveLegs();
            var player = fixture.player();
            UUID archive = SubLevelArchiveApi.store(player, helper.getLevel(), fixture.foot.getUniqueId(), fixture.limits());
            var stored = SubLevelArchiveStore.forServer(player.server).entry(archive);
            helper.assertTrue(stored.getInt("BodyCount") == 3, "Digisable stored only the selected limb");
            var container = SubLevelContainer.getContainer(helper.getLevel());
            helper.assertTrue(fixture.ids().stream().allMatch(id -> container.getSubLevel(id) == null), "Storage left walker bodies behind");
            SubLevelArchiveApi.extract(player, archive, player.position());
            helper.assertTrue(fixture.ids().stream().allMatch(id -> container.getSubLevel(id) != null), "Extraction lost a walker body");
            var restored = (WalkerCoreBlockEntity)helper.getLevel().getBlockEntity(fixture.core.getBlockPos());
            helper.assertTrue(restored != null && expected.equals(restored.saveLegs()), "Extraction changed native joint definitions");
            var joint = (LegJointBlockEntity)helper.getLevel().getBlockEntity(fixture.joint.getBlockPos());
            helper.assertTrue(joint != null && fixture.foot.getUniqueId().equals(joint.child()), "Extraction lost the custom joint child");
        }
        helper.succeed();
    }

    // Reject a partial archive before removing any loaded walker body
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void missingLimbBlocksStorage(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(fixture.foot));
            boolean rejected = false;
            try{ SubLevelArchiveApi.store(fixture.player(), helper.getLevel(), fixture.hull.getUniqueId(), fixture.limits()); }
            catch(IllegalArgumentException err){ rejected = err.getMessage().contains("unavailable connected"); }
            helper.assertTrue(rejected, "Storage accepted an incomplete walker");
            helper.assertTrue(!fixture.hull.isRemoved() && !fixture.limb.isRemoved(), "Failed storage removed part of the walker");
        }
        helper.succeed();
    }

    // Keep movement and turning active together and release only SCM-owned input
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void pilotDrivesAndReleasesNativeInputs(GameTestHelper helper){
        try(var fixture = new Fixture(helper)){
            var pilot = fixture.pilot();
            helper.assertTrue(pilot.isAvailable(), "The assembled native pilot was unavailable");
            pilot.drive(new Vec3(0, 0, -1), new Vec3(0, -1, 0));
            helper.assertTrue((fixture.core.input & 9) == 9, "SCM dropped movement or right steering");
            pilot.drive(new Vec3(0, 0, 1), Vec3.ZERO);
            helper.assertTrue((fixture.core.input & 3) == 2, "SCM reverse did not reach the walker");
            pilot.restore();
            helper.assertTrue(fixture.core.input == 0 && fixture.core.latchedInput == 0, "Release left the walker moving");
            pilot.drive(Vec3.ZERO, Vec3.ZERO, true, false);
            helper.assertTrue((fixture.core.input & WalkerBrain.KEY_JUMP) != 0 && (fixture.core.input & WalkerBrain.KEY_UP) == 0,
                    "SCM Jump became a height adjustment");
            pilot.drive(Vec3.ZERO, Vec3.ZERO, false, true);
            helper.assertTrue((fixture.core.input & WalkerBrain.KEY_DOWN) != 0, "SCM Crouch did not lower the native walker");
            pilot.drive(new Vec3(0, 0, -1), Vec3.ZERO);
            fixture.core.receiveInput(WalkerBrain.KEY_LEFT, helper.getLevel().getGameTime());
            pilot.restore();
            helper.assertTrue(fixture.core.input == WalkerBrain.KEY_LEFT, "SCM release overwrote a newer player input");
        }
        helper.succeed();
    }

    // Offer Walker only when its required native hardware is on this sublevel
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void walkerSelectionRequiresCoreAndPilot(GameTestHelper helper){
        try(var fixture = new Fixture(helper)){
            helper.assertTrue(LetItWalkSupport.isWalker(fixture.controller), "The complete walker was not detected");
            helper.assertTrue("walker".equals(fixture.controller.getScmDetectedVehicleType()), "Auto detection did not suggest Walker");
            helper.assertTrue(ScmControlModeRegistry.resolve("walker").usesGroundNavigation(), "Walker used flight navigation");
            var profile = new ScmConfigurationProfile();
            profile.setVehicleType("walker");
            helper.assertTrue(fixture.controller.canApplyScmConfigurationProfile(profile), "Walker profile was rejected with both blocks present");
            fixture.hull.getPlot().getEmbeddedLevelAccessor().setBlock(new BlockPos(1, 0, 0), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!LetItWalkSupport.isWalker(fixture.controller), "Walker remained selectable without pilot controls");
            helper.assertTrue(LetItWalkSupport.hasCore(fixture.controller), "Native graph nodes incorrectly required pilot controls");
            helper.assertTrue(!fixture.controller.canApplyScmConfigurationProfile(profile), "The server accepted Walker without pilot controls");
        }
        helper.succeed();
    }

    // Keep passive graph reads harmless while native control nodes change the real core
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void walkerNodesRequireLiveExecution(GameTestHelper helper){
        try(var fixture = new Fixture(helper)){
            var park = GraphApi.runtimes().get("createthrusters:let_it_walk_parking");
            park.execute(fixture.context(false), Map.of("parked", GraphValue.bool(true)));
            helper.assertTrue(!fixture.core.parked, "A passive graph read parked the walker");
            park.execute(fixture.context(true), Map.of("parked", GraphValue.bool(true)));
            helper.assertTrue(fixture.core.parked, "The parking node did not reach the core");
            fixture.pilot().drive(new Vec3(0, 0, -1), Vec3.ZERO);
            helper.assertTrue(fixture.core.parked, "SCM movement undid the graph's parking command");
            park.execute(fixture.context(true), Map.of("parked", GraphValue.bool(false)));
            GraphApi.runtimes().get("createthrusters:let_it_walk_movement").execute(fixture.context(true),
                    Map.of("forward", GraphValue.number(-1), "gear", GraphValue.number(2)));
            helper.assertTrue((fixture.core.input & 3) == 2 && WalkerBrain.gearOf(fixture.core.input) == 2,
                    "The movement node lost reverse or native gear selection");
            GraphApi.runtimes().get("createthrusters:let_it_walk_jump").execute(fixture.context(true), Map.of("held", GraphValue.bool(true)));
            helper.assertTrue((fixture.core.input & (2 | WalkerBrain.KEY_JUMP)) == (2 | WalkerBrain.KEY_JUMP),
                    "A jump node cleared movement from the same tick");
            GraphApi.runtimes().get("createthrusters:let_it_walk_stop").execute(fixture.context(true), Map.of());
            helper.assertTrue(fixture.core.input == 0, "The stop node retained a native movement input");
        }
        helper.succeed();
    }

    // Carry ordinary SCM graph commands through a configured native pilot map
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void scmCommandsReachTheConfiguredPilot(GameTestHelper helper) throws Exception{
        var fixture = new Fixture(helper);
        try{
            var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("shipControlRuntime");
            field.setAccessible(true);
            var runtime = (ShipControlModuleRuntime)field.get(fixture.controller);
            var profile = new ScmConfigurationProfile();
            profile.setVehicleType("walker");
            var reference = new ScmConfigurationProfile.UnitReference(fixture.hull.getUniqueId(), fixture.station.getBlockPos(), "scm:let_it_walk_pilot");
            profile.replace(fixture.controller.getScmConfigurationMapId(),
                    List.of(new ScmConfigurationProfile.Group("pilot", "Pilot Controls", Set.of(reference))),
                    Map.of(ScmConfigurationProfile.AUTO_ACTION, "pilot"), Set.of());
            helper.assertTrue(fixture.controller.replaceScmConfigurationProfile(profile), "SCM rejected the native pilot assignment");
            for(int idx = 0; idx < 12; idx++) runtime.tick();
            helper.assertTrue(fixture.controller.executeShipControlGraphCommand("forward", "ship_forward", Map.of("amount", 1.0D)),
                    "SCM rejected Forward after native pilot initialization: " + runtime.initializationStatus());
            helper.assertTrue(fixture.controller.executeShipControlGraphCommand("yaw", "ship_yaw_right", Map.of("amount", 1.0D)),
                    "SCM rejected Yaw Right on the walker");
            runtime.tick();
            // Let native control ownership settle across real server ticks before dispatch
            helper.runAfterDelay(1, runtime::tick);
            helper.runAfterDelay(3, () -> {
                try(fixture){
                    runtime.tick();
                    helper.assertTrue((fixture.core.input & 9) == 9,
                            "SCM graph commands did not reach movement and steering together; keys=" + fixture.core.input);
                    fixture.controller.executeShipControlGraphCommand("stop", "ship_stop", Map.of());
                    helper.assertTrue(fixture.core.input == 0, "SCM Stop left native pilot input held");
                    helper.succeed();
                }
            });
        }catch(Exception err){
            fixture.close();
            throw err;
        }
    }

    // Carry turn-in-place speed through the complete configured SCM pilot dispatch
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void scmTurnsTowardARearTargetWithoutWalking(GameTestHelper helper) throws Exception{
        var fixture = new Fixture(helper);
        try{
            var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("shipControlRuntime");
            field.setAccessible(true);
            var runtime = (ShipControlModuleRuntime)field.get(fixture.controller);
            var profile = new ScmConfigurationProfile();
            profile.setVehicleType("walker");
            var reference = new ScmConfigurationProfile.UnitReference(fixture.hull.getUniqueId(), fixture.station.getBlockPos(), "scm:let_it_walk_pilot");
            profile.replace(fixture.controller.getScmConfigurationMapId(),
                    List.of(new ScmConfigurationProfile.Group("pilot", "Pilot Controls", Set.of(reference))),
                    Map.of(ScmConfigurationProfile.AUTO_ACTION, "pilot"), Set.of());
            helper.assertTrue(fixture.controller.replaceScmConfigurationProfile(profile), "SCM rejected the pilot profile");
            for(int idx = 0; idx < 12; idx++) runtime.tick();
            Vec3 target = SubLevelArchiveApi.position(fixture.hull).add(0, 0, 20);
            helper.assertTrue(fixture.controller.executeShipControlGraphCommand("rearTarget", "ship_navigate",
                    Map.of("x", target.x, "y", target.y, "z", target.z, "speed", 3.0D, "tolerance", 0.1D)),
                    "SCM rejected the rear target");
            runtime.tick();
            helper.runAfterDelay(1, runtime::tick);
            helper.runAfterDelay(3, () -> {
                try(fixture){
                    runtime.tick();
                    var core = SubLevelBlockEntityCollector.getBlockEntities(fixture.hull, WalkerCoreBlockEntity.class).getFirst();
                    var navigation = (ScmPilotControl.Navigation)runtimeState(runtime, "pilotNavigation");
                    helper.assertTrue(navigation != null && navigation.speed() == 0 && (core.input & 3) == 0 && core.throttle == 0,
                            "SCM kept walking while turning toward a rear target; input=" + core.input + "; navigation=" + navigation
                                    + "; status=" + runtime.initializationStatus() + "; commands=" + runtimeState(runtime, "activeCommands"));
                    helper.assertTrue(Math.abs(core.mouseTurn) + Math.abs(core.mouseTurnRate) > 0,
                            "The turn-in-place request lost native yaw input");
                    fixture.controller.executeShipControlGraphCommand("stop", "ship_stop", Map.of());
                    helper.succeed();
                }
            });
        }catch(Exception err){ fixture.close(); throw err; }
    }

    // Keep a mapped connector request pending while the walker raises its body into alignment
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void mappedConnectorWaitsForVerticalAlignment(GameTestHelper helper) throws Exception{
        var fixture = new Fixture(helper);
        try{
            var connectorBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("simulated", "docking_connector"));
            fixture.hull.getPlot().getEmbeddedLevelAccessor().setBlock(new BlockPos(0, 0, -1), Blocks.STONE.defaultBlockState(), 3);
            fixture.hull.getPlot().getEmbeddedLevelAccessor().setBlock(new BlockPos(0, 0, -2), connectorBlock.defaultBlockState(), 3);
            var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("shipControlRuntime");
            field.setAccessible(true);
            var runtime = (ShipControlModuleRuntime)field.get(fixture.controller);
            var profile = new ScmConfigurationProfile();
            profile.setVehicleType("walker");
            var reference = new ScmConfigurationProfile.UnitReference(fixture.hull.getUniqueId(), fixture.station.getBlockPos(), "scm:let_it_walk_pilot");
            profile.replace(fixture.controller.getScmConfigurationMapId(),
                    List.of(new ScmConfigurationProfile.Group("pilot", "Pilot Controls", Set.of(reference))),
                    Map.of(ScmConfigurationProfile.AUTO_ACTION, "pilot"), Set.of());
            helper.assertTrue(fixture.controller.replaceScmConfigurationProfile(profile), "SCM rejected the native pilot profile");
            for(int idx = 0; idx < 12; idx++) runtime.tick();
            var connector = fixture.controller.getShipDockingConnector(0);
            helper.assertTrue(connector != null, "SCM did not map the walker's docking connector");
            Vec3 target = connector.worldTipPosition().add(0, 0.2D, 0);
            helper.assertTrue(fixture.controller.executeShipControlGraphCommand("nativeDockHeight", "ship_navigate",
                    Map.of("x", target.x, "y", target.y, "z", target.z, "speed", 0.2D, "tolerance", 0.75D),
                    Map.of("target_point", "docking_connector:0")), "SCM rejected a tracked connector height target");
            runtime.tick();
            helper.runAfterDelay(1, runtime::tick);
            helper.runAfterDelay(3, () -> {
                try(fixture){
                    runtime.tick();
                    var liveCore = SubLevelBlockEntityCollector.getBlockEntities(fixture.hull, WalkerCoreBlockEntity.class).getFirst();
                    helper.assertTrue((liveCore.input & WalkerBrain.KEY_UP) != 0,
                            "The mapped connector error did not reach native body height control; keys=" + liveCore.input
                                    + "; status=" + runtime.initializationStatus() + "; controls=" + runtime.goggleControlTelemetry()
                                    + "; navigation=" + runtimeState(runtime, "pilotNavigation"));
                    helper.assertTrue(!fixture.controller.isShipControlGraphCommandComplete("nativeDockHeight", "ship_navigate"),
                            "SCM completed a connector arrival before its vertical position matched");
                    fixture.controller.executeShipControlGraphCommand("stop", "ship_stop", Map.of());
                    helper.succeed();
                }
            });
        }catch(Exception err){
            fixture.close();
            throw err;
        }
    }

    // Reduce the native gear for a slow approach while retaining a higher cruise setpoint
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void navigationSpeedSelectsTheNativeGear(GameTestHelper helper){
        try(var fixture = new Fixture(helper)){
            fixture.core.setSpeed(128);
            var pilot = fixture.pilot();
            pilot.drive(new ScmPilotControl.Command(new Vec3(0, 0, -1), Vec3.ZERO, false, false, false,
                    new ScmPilotControl.Navigation(new Vec3(0, 0, -30), Vec3.ZERO, 5, 0.1D, false)));
            int cruise = WalkerBrain.gearOf(fixture.core.input);
            pilot.drive(new ScmPilotControl.Command(new Vec3(0, 0, -1), Vec3.ZERO, false, false, false,
                    new ScmPilotControl.Navigation(new Vec3(0, 0, -1), Vec3.ZERO, 0.1D, 0.1D, true)));
            helper.assertTrue(cruise > WalkerBrain.gearOf(fixture.core.input), "SCM retained its cruise gear during a slow docking approach");
            helper.assertTrue(WalkerBrain.gearOf(fixture.core.input) <= WalkerBrain.maxGear(fixture.core.getTheoreticalSpeed()),
                    "SCM selected a gear above the native RPM limit");
        }
        helper.succeed();
    }

    // Stop native cruise smoothing before applying proportional navigation yaw
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void navigationYawReleasesCruiseMotionImmediately(GameTestHelper helper){
        try(var fixture = new Fixture(helper)){
            fixture.core.setSpeed(128);
            fixture.core.throttle = 1;
            fixture.core.strafe = 1;
            fixture.core.gear = 4;
            var pilot = fixture.pilot();
            pilot.drive(new ScmPilotControl.Command(Vec3.ZERO, new Vec3(0, 1, 0), false, false, false,
                    new ScmPilotControl.Navigation(new Vec3(0, 0, 20), Vec3.ZERO, 0, 0.1D, false)));
            helper.assertTrue((fixture.core.input & 15) == 0 && fixture.core.throttle == 0 && fixture.core.strafe == 0,
                    "Turning on the spot retained forward, crab or binary yaw input");
            helper.assertTrue(fixture.core.gear == 0 && fixture.core.mouseTurn > 0,
                    "The turn did not release cruise gear and reach native proportional yaw");
            double prev = fixture.core.mouseTurn;
            pilot.drive(new ScmPilotControl.Command(Vec3.ZERO, new Vec3(0, 0.02D, 0), false, false, false,
                    new ScmPilotControl.Navigation(Vec3.ZERO, Vec3.ZERO, 0, 0.1D, true)));
            helper.assertTrue(Math.abs(fixture.core.mouseTurn - prev - 0.0008D) < 1.0E-9D,
                    "Fine docking yaw was discarded by the ordinary digital steering deadband");
            pilot.restore();
        }
        helper.succeed();
    }

    // Detect replacement feet immediately and keep flight steering independent of wheel strategies
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void nativeFeetSelectMovementCapabilities(GameTestHelper helper){
        try(var fixture = new Fixture(helper)){
            fixture.controller.setShipControlMode("walker");
            helper.assertTrue(fixture.controller.getShipControlMode().usesGroundNavigation(), "Ordinary feet lost ground navigation");
            fixture.foot.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO,
                    ModContent.CLIMBING_FOOT.get().defaultBlockState(), 3);
            var climb = LetItWalkCompatibility.profile(fixture.controller);
            helper.assertTrue(climb.canClimb() && !climb.canFly(), "Spike feet were not detected from the live foot block");
            helper.assertTrue(!fixture.controller.getShipControlMode().usesGroundNavigation(), "Spike feet were confined to horizontal navigation");
            fixture.foot.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO,
                    ModContent.JET_FOOT.get().defaultBlockState(), 3);
            var flight = LetItWalkCompatibility.profile(fixture.controller);
            helper.assertTrue(flight.canFly() && !flight.canClimb(), "Jet feet were not detected after replacement");
            helper.assertTrue("custom".equals(fixture.controller.getScmDetectedSteeringType()), "Jet walker did not use custom steering");
            helper.assertTrue(!fixture.controller.getShipControlMode().avoidsWorldCollisions(), "Native walkers retained SCM terrain avoidance");
        }
        helper.succeed();
    }

    // Match the connector's vertical error instead of treating every lift request as free movement
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void dockingHeightFollowsTheTrackedConnector(GameTestHelper helper){
        try(var fixture = new Fixture(helper)){
            var pilot = fixture.pilot();
            pilot.drive(new ScmPilotControl.Command(Vec3.ZERO, Vec3.ZERO, false, false, false,
                    new ScmPilotControl.Navigation(new Vec3(0, 0.5D, 0), Vec3.ZERO, 0, 0.05D, true)));
            helper.assertTrue((fixture.core.input & WalkerBrain.KEY_UP) != 0, "A low connector did not raise the body");
            pilot.drive(new ScmPilotControl.Command(Vec3.ZERO, Vec3.ZERO, false, false, false,
                    new ScmPilotControl.Navigation(new Vec3(0, -0.5D, 0), Vec3.ZERO, 0, 0.05D, true)));
            helper.assertTrue((fixture.core.input & WalkerBrain.KEY_DOWN) != 0, "A high connector did not lower the body");
            pilot.drive(new ScmPilotControl.Command(Vec3.ZERO, Vec3.ZERO, false, false, false,
                    new ScmPilotControl.Navigation(new Vec3(0, 0.02D, 0), Vec3.ZERO, 0, 0.05D, true)));
            helper.assertTrue((fixture.core.input & (WalkerBrain.KEY_UP | WalkerBrain.KEY_DOWN)) == 0,
                    "Connector alignment kept changing body height inside tolerance");
        }
        helper.succeed();
    }

    // Preserve native fuel gates and replace the terrain hover cap only during owned flight control
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void jetPilotsHoldAbsoluteAltitudeAfterTheNativeFlightTick(GameTestHelper helper){
        try(var fixture = new Fixture(helper)){
            fixture.foot.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, ModContent.JET_FOOT.get().defaultBlockState(), 3);
            fixture.core.setSpeed(128);
            var pilot = fixture.pilot();
            var climb = new ScmPilotControl.Command(Vec3.ZERO, Vec3.ZERO, false, false, true,
                    new ScmPilotControl.Navigation(new Vec3(0, 30, 0), Vec3.ZERO, 0, 0.05D, false));
            pilot.drive(climb);
            helper.assertTrue(!fixture.core.flying, "SCM bypassed the native jet fuel requirement");
            fixture.core.lavaTank.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
            pilot.drive(climb);
            helper.assertTrue(fixture.core.flying, "A powered, fueled jet walker did not enter native flight");
            fixture.core.jetPower = 1;
            WalkerFlight.tick(fixture.core, fixture.hull, fixture.core.walking(), Vec3.ZERO, new Vector3d(), 0, helper.getLevel().getGameTime());
            helper.assertTrue(fixture.core.heightError > 0.9D && fixture.core.heightError <= 1.01D,
                    "Native flight overwrote the SCM's altitude request above the terrain hover limit; error=" + fixture.core.heightError);
            for(int idx = 0; idx < 8; idx++) WalkerFlight.tick(fixture.core, fixture.hull, fixture.core.walking(), Vec3.ZERO,
                    new Vector3d(), 0, helper.getLevel().getGameTime());
            helper.assertTrue(fixture.core.lavaTank.getFluidAmount() < 1000, "SCM flight bypassed native fuel consumption");
            pilot.drive(new ScmPilotControl.Command(Vec3.ZERO, Vec3.ZERO, false, false, true,
                    new ScmPilotControl.Navigation(Vec3.ZERO, Vec3.ZERO, 0, 0.05D, false)));
            WalkerFlight.tick(fixture.core, fixture.hull, fixture.core.walking(), Vec3.ZERO, new Vector3d(), 0, helper.getLevel().getGameTime());
            helper.assertTrue(Math.abs(fixture.core.heightError) < 0.01D, "A stationary jet walker could not hold absolute altitude");
            pilot.restore();
        }
        helper.succeed();
    }

    // Honor the saved ceiling for manual gear selection and percentage-controlled jet ascent
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void nativePilotsHonorSpeedCeilingsOnTheGroundAndInFlight(GameTestHelper helper){
        try(var fixture = new Fixture(helper)){
            fixture.core.setSpeed(128);
            var pilot = fixture.pilot();
            pilot.drive(new ScmPilotControl.Command(new Vec3(0, 0, -1), Vec3.ZERO, false, false, false, null));
            int unrestricted = WalkerBrain.gearOf(fixture.core.input);
            pilot.drive(new ScmPilotControl.Command(new Vec3(0, 0, -1), Vec3.ZERO, false, false, false, null, 0.1D));
            helper.assertTrue(WalkerBrain.gearOf(fixture.core.input) < unrestricted, "A manual pilot ignored Max Speed when selecting its gear");
            fixture.foot.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, ModContent.JET_FOOT.get().defaultBlockState(), 3);
            fixture.core.lavaTank.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
            pilot.drive(new ScmPilotControl.Command(Vec3.ZERO, Vec3.ZERO, false, false, true,
                    new ScmPilotControl.Navigation(new Vec3(0, 30, 0), Vec3.ZERO, 0.5D, 0.05D, false), 2));
            fixture.core.jetPower = 1;
            WalkerFlight.tick(fixture.core, fixture.hull, fixture.core.walking(), Vec3.ZERO, new Vector3d(), 0, helper.getLevel().getGameTime());
            helper.assertTrue(fixture.core.heightError > 0 && fixture.core.heightError <= 0.126D,
                    "Jet ascent ignored its percentage speed setpoint: " + fixture.core.heightError);
            pilot.drive(new ScmPilotControl.Command(Vec3.ZERO, Vec3.ZERO, false, false, true,
                    new ScmPilotControl.Navigation(new Vec3(0, 30, 0), Vec3.ZERO, 0, 0.05D, false), 2));
            WalkerFlight.tick(fixture.core, fixture.hull, fixture.core.walking(), Vec3.ZERO, new Vector3d(), 0, helper.getLevel().getGameTime());
            helper.assertTrue(Math.abs(fixture.core.heightError) < 0.01D, "Zero navigation speed retained jet ascent");
            pilot.drive(new ScmPilotControl.Command(Vec3.ZERO, Vec3.ZERO, false, false, true,
                    new ScmPilotControl.Navigation(new Vec3(0, 30, 0), Vec3.ZERO, 0.25D, 0.05D, false, true), 2));
            WalkerFlight.tick(fixture.core, fixture.hull, fixture.core.walking(), Vec3.ZERO, new Vector3d(), 0, helper.getLevel().getGameTime());
            helper.assertTrue(fixture.core.heightError > 0 && fixture.core.heightError <= 0.063D,
                    "An independent Climb to Y goal ignored its vertical speed: " + fixture.core.heightError);
            pilot.restore();
        }
        helper.succeed();
    }

    // Preserve a Climb to Y speed percentage through SCM dispatch without requiring route guidance
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void scmClimbSpeedUsesTheSavedLimitWithoutRouteGuidance(GameTestHelper helper) throws Exception{
        var fixture = new Fixture(helper);
        try{
            fixture.foot.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, ModContent.JET_FOOT.get().defaultBlockState(), 3);
            fixture.core.setSpeed(128);
            fixture.core.lavaTank.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
            var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("shipControlRuntime");
            field.setAccessible(true);
            var runtime = (ShipControlModuleRuntime)field.get(fixture.controller);
            var profile = new ScmConfigurationProfile();
            profile.setVehicleType("walker");
            var reference = new ScmConfigurationProfile.UnitReference(fixture.hull.getUniqueId(), fixture.station.getBlockPos(), "scm:let_it_walk_pilot");
            profile.replace(fixture.controller.getScmConfigurationMapId(),
                    List.of(new ScmConfigurationProfile.Group("pilot", "Pilot Controls", Set.of(reference))),
                    Map.of(ScmConfigurationProfile.AUTO_ACTION, "pilot"), Set.of());
            helper.assertTrue(fixture.controller.replaceScmConfigurationProfile(profile), "SCM rejected the flying pilot profile");
            for(int idx = 0; idx < 12; idx++) runtime.tick();
            helper.assertTrue(fixture.controller.setShipMaximumSpeed(2), "SCM rejected the maximum speed");
            helper.assertTrue(fixture.controller.executeShipControlGraphCommand("climb", "ship_climb",
                    Map.of("y", SubLevelArchiveApi.position(fixture.hull).y + 30, "speed", 0.25D,
                            "speed_fraction", 1.0D, "avoid_collisions", 0.0D)), "SCM rejected Climb to Y");
            runtime.tick();
            helper.runAfterDelay(1, () -> { fixture.core.setSpeed(128); runtime.tick(); });
            helper.runAfterDelay(3, () -> {
                try(fixture){
                    var core = SubLevelBlockEntityCollector.getBlockEntities(fixture.hull, WalkerCoreBlockEntity.class).getFirst();
                    core.setSpeed(128);
                    runtime.tick();
                    helper.assertTrue(core.flying, "The retained climb goal did not reach the native flying pilot; powered=" + core.isPowered()
                            + "; legs=" + core.walking().size() + "; upright=" + core.upright() + "; parked=" + core.parked
                            + "; stopping=" + core.jetsStopping + "; fuel=" + core.lavaTank.getFluidAmount()
                            + "; status=" + runtime.initializationStatus() + "; commands=" + runtimeState(runtime, "activeCommands"));
                    core.jetPower = 1;
                    WalkerFlight.tick(core, fixture.hull, core.walking(), Vec3.ZERO, new Vector3d(), 0, helper.getLevel().getGameTime());
                    helper.assertTrue(core.heightError > 0 && core.heightError <= 0.126D,
                            "Climb did not use 25% of 2 blocks/s: " + core.heightError);
                    fixture.controller.executeShipControlGraphCommand("stop", "ship_stop", Map.of());
                    helper.succeed();
                }
            });
        }catch(Exception err){ fixture.close(); throw err; }
    }

    // Keep prepared schedule legs direct instead of detouring around climbable terrain
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void walkerSchedulePreparationSkipsTerrainDetours(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            fixture.controller.setShipControlMode("walker");
            var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("shipControlRuntime");
            field.setAccessible(true);
            var runtime = (ShipControlModuleRuntime)field.get(fixture.controller);
            Vec3 origin = SubLevelArchiveApi.position(fixture.hull);
            helper.assertTrue(runtime.queueScheduledRouteDestinations(List.of(
                    new ShipControlModuleRuntime.ScheduledRouteDestination(0, null, origin, origin.add(0, 20, -10)))),
                    "Walker schedule route could not be prepared");
            helper.assertTrue(!runtime.isPrecalculatingScheduledRoutes(), "Walker queued a terrain pathfinding job");
        }
        helper.succeed();
    }

    // Detect unrelated bodies without blocking native terrain contact or the walker's own limbs
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void walkerAvoidanceIgnoresTerrainAndDetectsOtherShips(GameTestHelper helper) throws Exception{
        Map<BlockPos, BlockState> prev = new LinkedHashMap<>();
        ServerSubLevel obstacle = null;
        try(var fixture = new Fixture(helper)){
            var runtime = fixture.autopilot();
            Object ctx = NavigationSpeedGameTests.invoke(runtime, "collisionScanContext");
            helper.assertTrue(!(boolean)NavigationSpeedGameTests.invoke(ctx, "includeWorldBlocks"),
                    "Walker avoidance retained world terrain");
            helper.assertTrue(((Set<?>)NavigationSpeedGameTests.invoke(ctx, "excludedSubLevelIds")).containsAll(fixture.ids()),
                    "Walker avoidance did not exclude its connected limbs");
            Vec3 origin = SubLevelArchiveApi.position(fixture.hull);
            BlockPos wall = BlockPos.containing(origin).offset(0, 0, 4);
            for(int x = -3; x <= 3; x++) for(int y = -3; y <= 4; y++){
                BlockPos pos = wall.offset(x, y, 0);
                prev.put(pos, helper.getLevel().getBlockState(pos));
                helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            }
            double terrain = (double)NavigationSpeedGameTests.invoke(runtime, "collisionDistance", origin, new Vec3(0, 0, 1), 8.0D);
            helper.assertTrue(terrain == 8.0D, "A world wall blocked native walking: " + terrain);
            obstacle = ShipDoorGameTests.newBody(helper, origin.add(0, 0, -5));
            ServerSubLevel body = obstacle;
            SableSubLevelLifecycleApi.transfer(helper.getLevel(), body, () -> {
                var view = body.getPlot().getEmbeddedLevelAccessor();
                for(int x = -3; x <= 3; x++) for(int y = -3; y <= 4; y++)
                    view.setBlock(new BlockPos(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
            });
            double ship = (double)NavigationSpeedGameTests.invoke(runtime, "collisionDistance", origin, new Vec3(0, 0, -1), 8.0D);
            helper.assertTrue(ship < 5.0D, "Walker avoidance missed an unrelated sub-level: " + ship);
        }finally{
            if(obstacle != null) SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(obstacle));
            prev.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 3));
        }
        helper.succeed();
    }

    // Publish both walking routes to the real fleet coordinator and retain right-of-way braking
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void autopilotedWalkersParticipateInTraffic(GameTestHelper helper) throws Exception{
        try(var first = new Fixture(helper); var second = new Fixture(helper, new Vec3(-8, 0, 8))){
            var firstRuntime = first.autopilot();
            var secondRuntime = second.autopilot();
            for(var runtime : List.of(firstRuntime, secondRuntime)){
                Vec3 origin = (Vec3)NavigationSpeedGameTests.invoke(NavigationSpeedGameTests.invoke(runtime, "telemetry"), "position");
                Vec3 target = origin.add(runtime == firstRuntime ? new Vec3(0, 0, 20) : new Vec3(20, 0, 0));
                helper.assertTrue(runtime.execute("traffic", "ship_navigate", Map.of("x", target.x, "y", target.y,
                        "z", target.z, "speed", 3.0D, "tolerance", 0.1D, "avoid_collisions", 1.0D), Map.of()),
                        "The configured walker rejected navigation");
                Object cmd = ((Map<?, ?>)runtimeState(runtime, "activeCommands")).get("traffic\u0000ship_navigate");
                helper.assertTrue((boolean)NavigationSpeedGameTests.invoke(runtime, "collisionAvoidanceEnabled", cmd),
                        "Native terrain handling disabled all autonomous avoidance: " + cmd);
            }
            var field = ShipControlModuleRuntime.class.getDeclaredField("TRAFFIC_COORDINATORS");
            field.setAccessible(true);
            var coordinators = (Map<?, ?>)field.get(null);
            coordinators.remove(helper.getLevel());
            var coordinator = (TrafficCoordinator)NavigationSpeedGameTests.invoke(firstRuntime, "trafficCoordinator", helper.getLevel().getGameTime());
            for(var runtime : List.of(firstRuntime, secondRuntime)){
                helper.assertTrue(!((Map<?, ?>)runtimeState(runtime, "routeTrafficIntents")).isEmpty(),
                        "The traffic system treated an autopiloted walker as a stationary obstacle");
            }
            helper.assertTrue(coordinator.directive(first.hull.getUniqueId()).traffic().yield()
                            || coordinator.directive(second.hull.getUniqueId()).traffic().yield(),
                    "Intersecting walker routes did not produce a traffic yield: "
                            + coordinator.directive(first.hull.getUniqueId()) + "; " + coordinator.directive(second.hull.getUniqueId()));
        }
        helper.succeed();
    }

    // Preserve schedule arrival height for each missing connector combination
    @GameTest(template = "empty", batch = "walker", timeoutTicks = 100)
    public static void tallWalkerSchedulesIgnoreHeightWithoutAConnectorPair(GameTestHelper helper) throws Exception{
        List<Fixture> fixtures = new java.util.ArrayList<>();
        try{
            for(int missing = 0; missing < 3; missing++){
                var fixture = new Fixture(helper, new Vec3(0, missing * 40, 0));
                fixtures.add(fixture);
                var pipeline = SubLevelContainer.getContainer(helper.getLevel()).physicsSystem().getPipeline();
                pipeline.teleport(fixture.foot, new Vector3d(fixture.foot.logicalPose().position()).add(0, -14, 0),
                        fixture.foot.logicalPose().orientation());
                if(missing == 1){
                    var connector = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simulated:docking_connector"));
                    fixture.hull.getPlot().getEmbeddedLevelAccessor().setBlock(new BlockPos(2, 1, 0), connector.defaultBlockState(), 3);
                }
                fixture.autopilot();
            }
            helper.runAfterDelay(3, () -> {
                try{
                    for(int missing = 0; missing < fixtures.size(); missing++){
                        var fixture = fixtures.get(missing);
                        var schedule = (ShippingScheduleRuntime)NavigationSpeedGameTests.field(fixture.controller, "shippingScheduleRuntime");
                        Vec3 pos = (Vec3)NavigationSpeedGameTests.invoke(schedule, "currentPosition");
                        Vec3 dockPos = pos.add(0, -14, 0);
                        helper.assertTrue(fixture.controller.getShipEnvelope().height() >= 14,
                                "The native walker fixture did not retain its separated foot body");
                        boolean dockConnector = missing == 0;
                        var dock = new ShipDockRegistry.Dock(UUID.randomUUID(), helper.getLevel().dimension().location(), null, BlockPos.ZERO,
                                dockPos, new Vec3(0, 0, -1), "Tall walker stop", false, false, false, null,
                                dockConnector ? BlockPos.ZERO : null, dockConnector ? dockPos : null,
                                dockConnector ? new Vec3(0, 0, -1) : null, new Vec3(0, 1, 0), 0);
                        Vec3 target = (Vec3)NavigationSpeedGameTests.invoke(schedule, "connectorlessNavigationTarget", dock);
                        helper.assertTrue(target.distanceTo(pos) < 1.0E-6D,
                                "Connectorless navigation demanded an unreachable Y; missing configuration=" + missing + "; target=" + target + "; pos=" + pos);
                        helper.assertTrue((boolean)NavigationSpeedGameTests.invoke(schedule, "connectorlessDockTargetReached", dock, target),
                                "The tall walker did not reach the dock's UP detection axis; missing configuration=" + missing
                                        + "; envelope=" + fixture.controller.getShipEnvelope() + "; position=" + pos + "; dock=" + dockPos);
                        helper.assertTrue(!(boolean)NavigationSpeedGameTests.invoke(schedule, "usesPhysicalDocking", dock),
                                "The missing connector pair incorrectly selected physical docking");
                        helper.assertTrue(!fixture.controller.getShipDockingConnectors().isEmpty() == (missing == 1),
                                "The test did not retain the intended ship connector configuration");
                    }
                    helper.succeed();
                }catch(Exception err){ throw new IllegalStateException(err); }
                finally{ fixtures.forEach(Fixture::close); }
            });
        }catch(Exception err){
            fixtures.forEach(Fixture::close);
            throw err;
        }
    }

    // Include the current request in failed end-to-end dispatch assertions
    private static Object runtimeState(ShipControlModuleRuntime runtime, String name){
        try{
            var field = ShipControlModuleRuntime.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(runtime);
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
    }

    // Build real native block entities on three independent Sable bodies
    private static final class Fixture implements AutoCloseable{
        // Retain the physical bodies and native block identities for each isolated walker
        private final GameTestHelper helper;
        private final ServerSubLevel hull, limb, foot;
        private final List<ServerSubLevel> bodies;
        private final WalkerCoreBlockEntity core;
        private final LegJointBlockEntity joint;
        private final PilotControlsBlockEntity station;
        private final AdvancedContraptionControllerBlockEntity controller;

        // Retain native plot coordinates in the same format used by a built walker
        private Fixture(GameTestHelper helper){
            this(helper, Vec3.ZERO);
        }

        // Place independent walkers along intersecting traffic routes
        private Fixture(GameTestHelper helper, Vec3 offset){
            this.helper = helper;
            Vec3 origin = helper.absolutePos(BlockPos.ZERO).getCenter().add(0, 16, 0).add(offset);
            hull = ShipDoorGameTests.newBody(helper, origin);
            limb = ShipDoorGameTests.newBody(helper, origin);
            foot = ShipDoorGameTests.newBody(helper, origin);
            bodies = List.of(hull, limb, foot);
            var hullView = hull.getPlot().getEmbeddedLevelAccessor();
            hullView.setBlock(BlockPos.ZERO, ModContent.WALKER_CORE.get().defaultBlockState().setValue(WalkerCoreBlock.FACING, Direction.NORTH), 3);
            hullView.setBlock(new BlockPos(1, 0, 0), ModContent.PILOT_CONTROLS.get().defaultBlockState(), 3);
            hullView.setBlock(new BlockPos(0, 1, 0), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
            hullView.setBlock(new BlockPos(0, 2, 0), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
            limb.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, ModContent.LEG_HINGE.get().defaultBlockState(), 3);
            foot.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, ModContent.LEG_FOOT.get().defaultBlockState(), 3);
            var level = helper.getLevel();
            core = (WalkerCoreBlockEntity)level.getBlockEntity(hull.getPlot().getCenterBlock());
            station = (PilotControlsBlockEntity)level.getBlockEntity(hull.getPlot().getCenterBlock().offset(1, 0, 0));
            controller = (AdvancedContraptionControllerBlockEntity)level.getBlockEntity(hull.getPlot().getCenterBlock().offset(0, 2, 0));
            joint = (LegJointBlockEntity)level.getBlockEntity(limb.getPlot().getCenterBlock());
            joint.setChild(foot.getUniqueId(), new Vec3(1, 0, 0), Direction.DOWN);
            Vec3 a = hull.getPlot().getCenterBlock().getCenter();
            Vec3 b = limb.getPlot().getCenterBlock().getCenter();
            Vec3 c = foot.getPlot().getCenterBlock().getCenter();
            var built = new BuiltLeg(List.of(new BuiltLeg.Joint(hull.getUniqueId(), limb.getUniqueId(), a, b, new Vec3(1, 0, 0), false),
                    new BuiltLeg.Joint(limb.getUniqueId(), foot.getUniqueId(), b, c, new Vec3(1, 0, 0), false)),
                    List.of(limb.getUniqueId(), foot.getUniqueId()), new double[][]{{0, 0, 0}, {0, -1, 0}},
                    new double[][]{{1, 0, 0}, {1, 0, 0}}, new double[]{0, -2, 0}, false, new Vec3(1, 0, 0), new double[2]);
            built.footPlot = c.add(0, -0.5D, 0);
            core.setLegs(List.of(new LegRuntime(built, 0, 0, 1, false)));
        }

        // Return every physical body expected in the archive
        private Set<UUID> ids(){ return Set.of(hull.getUniqueId(), limb.getUniqueId(), foot.getUniqueId()); }
        // Initialize the native pilot through the same persisted assignment used by players
        private ShipControlModuleRuntime autopilot() throws Exception{
            var runtime = (ShipControlModuleRuntime)NavigationSpeedGameTests.field(controller, "shipControlRuntime");
            var profile = new ScmConfigurationProfile();
            profile.setVehicleType("walker");
            var reference = new ScmConfigurationProfile.UnitReference(hull.getUniqueId(), station.getBlockPos(), "scm:let_it_walk_pilot");
            profile.replace(controller.getScmConfigurationMapId(),
                    List.of(new ScmConfigurationProfile.Group("pilot", "Pilot Controls", Set.of(reference))),
                    Map.of(ScmConfigurationProfile.AUTO_ACTION, "pilot"), Set.of());
            helper.assertTrue(controller.replaceScmConfigurationProfile(profile), "SCM rejected the pilot profile");
            for(int idx = 0; idx < 12; idx++) runtime.tick();
            return runtime;
        }
        // Keep test storage within the ordinary archive limits
        private SubLevelArchiveApi.Limits limits(){ return new SubLevelArchiveApi.Limits(100, 16, 10000, 128, 128); }
        // Place an independent server player beside the walker
        private FakePlayer player(){
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "WalkerTest"));
            var pos = SubLevelArchiveApi.position(hull);
            player.setPos(pos.x, pos.y, pos.z);
            return player;
        }
        // Resolve the native pilot through the public SCM provider registry
        private ScmPilotControl pilot(){
            var target = new ScmTarget(hull.getUniqueId(), station.getBlockPos(), "let_it_walk:pilot_controls", "Pilot Controls", null, null);
            return (ScmPilotControl)ScmControlProbeRegistry.create(helper.getLevel(), station, target, Vec3.ZERO).getFirst();
        }
        // Supply the same live block-entity service used by the ACC graph runtime
        private GraphExecutionContext context(boolean triggered){
            return new GraphExecutionContext(){
                private final Map<String, GraphValue> state = new HashMap<>();
                // Use the owning world's clock
                @Override public long tick(){ return helper.getLevel().getGameTime(); }
                // Distinguish live pulses from passive graph reads
                @Override public boolean executionTriggered(){ return triggered; }
                // Read isolated node state
                @Override public GraphValue state(String key){ return state.get(key); }
                // Retain isolated node state
                @Override public void state(String key, GraphValue val){ state.put(key, val); }
                // Expose the same typed host service used by the live ACC
                @Override public <T> Optional<T> service(GraphServiceKey<T> key){
                    return GraphHostServices.BLOCK_ENTITY.equals(key) ? Optional.of(key.type().cast(controller)) : Optional.empty();
                }
            };
        }
        // Remove restored instances as well as bodies which survived a rejected archive
        @Override public void close(){
            var container = SubLevelContainer.getContainer(helper.getLevel());
            var live = ids().stream().map(container::getSubLevel).filter(val -> val instanceof ServerSubLevel)
                    .map(val -> (ServerSubLevel)val).toList();
            SableSubLevelLifecycleApi.remove(helper.getLevel(), live);
        }
    }
}

package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationDestinations;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationProgress;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.rieno.gadgetsandgizmos.content.NavigationSpeedGameTests.field;
import static com.rieno.gadgetsandgizmos.content.NavigationSpeedGameTests.invoke;
import static com.rieno.gadgetsandgizmos.content.NavigationSpeedGameTests.set;

// Verify manual command ETAs reach the dashboard without an installed shipping schedule
@GameTestHolder("createthrusters_navigation_speed")
@PrefixGameTestTemplate(false)
public final class NavigationEtaGameTests{
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nodeNavigationEtaUpdatesFromLiveMotionAndClearsOnCompletion(GameTestHelper helper) throws Exception{
        try(var fixture = new NavigationSpeedGameTests.Fixture(helper)){
            var runtime = fixture.runtime;
            var ctrl = (AdvancedContraptionControllerBlockEntity)field(runtime, "controller");
            var origin = (Vec3)invoke(fixture.telemetry, "position");
            var target = origin.add(0, 0, 2000);
            helper.assertTrue(runtime.execute("eta_node", "ship_navigate", values(target, 20), Map.of()), "The navigate node was rejected");
            var starting = snapshot(ctrl);
            helper.assertTrue(starting.getBoolean("HasTarget") && starting.getLong("EtaSeconds") > 0, "The navigate node had no ETA");
            motion(fixture, origin.add(0, 0, 1000), new Vec3(0, 0, 20));
            var moving = snapshot(ctrl);
            motion(fixture, origin.add(0, 0, 1900), new Vec3(0, 0, 10));
            var closer = snapshot(ctrl);
            helper.assertTrue(starting.getLong("EtaSeconds") > moving.getLong("EtaSeconds")
                    && moving.getLong("EtaSeconds") > closer.getLong("EtaSeconds") && closer.getLong("EtaSeconds") > 0,
                    "Live node ETA did not decrease: " + starting + "; " + moving + "; " + closer);
            helper.assertTrue(closer.getDouble("TargetZ") == target.z, "The ETA target changed");
            String key = ((Map<String, ?>)field(runtime, "activeCommands")).keySet().stream().filter(val -> val.startsWith("eta_node")).findFirst().orElseThrow();
            ((Set<String>)field(runtime, "completedCommands")).add(key);
            helper.assertTrue(!runtime.navigationProgress().active() && snapshot(ctrl).getLong("EtaSeconds") == -1,
                    "A completed command retained its ETA");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coordinateAndSavedDestinationsBothPublishAnEta(GameTestHelper helper) throws Exception{
        try(var fixture = new NavigationSpeedGameTests.Fixture(helper)){
            var ctrl = (AdvancedContraptionControllerBlockEntity)field(fixture.runtime, "controller");
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EtaTest"));
            var ctx = new TabletActionContext(player, ItemStack.EMPTY, null, null, false, UUID.randomUUID(), null, null);
            var target = ((Vec3)invoke(fixture.telemetry, "position")).add(0, 0, 2000);
            String coordinates = target.x + ", " + target.y + ", " + target.z;
            var method = DiagnosticTabletAutoApp.class.getDeclaredMethod("navigate", TabletActionContext.class,
                    AdvancedContraptionControllerBlockEntity.class, String.class);
            method.setAccessible(true);
            helper.assertTrue(((TabletActionHandler.Result)method.invoke(null, ctx, ctrl, coordinates)).success(), "Coordinate navigation failed");
            helper.assertTrue(snapshot(ctrl).getLong("EtaSeconds") > 0, "Coordinate navigation had no ETA");
            helper.assertTrue(DiagnosticTabletAutoApp.editDestination(ctx, "destination_save", "Arrival test|" + coordinates).success(), "Saving failed");
            var saved = NavigationDestinations.fromTag(TabletStorageApi.storage().app(ctx.sourceTabletId(), DiagnosticTabletData.appId("gg_auto")));
            helper.assertTrue(DiagnosticTabletAutoApp.navigateDestination(ctx, ctrl, saved.entries().getFirst().id().toString()).success(), "Saved navigation failed");
            helper.assertTrue(snapshot(ctrl).getLong("EtaSeconds") > 0, "Saved destination navigation had no ETA");
            helper.assertTrue(fixture.runtime.execute("stop", "ship_stop", Map.of(), Map.of()), "Stopping failed");
            helper.assertTrue(!fixture.runtime.navigationProgress().active(), "Stop retained a stale arrival estimate");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void etaCountsTheCompleteDetourInsteadOfTheMapHorizon(GameTestHelper helper) throws Exception{
        try(var fixture = new NavigationSpeedGameTests.Fixture(helper)){
            var origin = (Vec3)invoke(fixture.telemetry, "position");
            var target = origin.add(2000, 0, 0);
            helper.assertTrue(fixture.runtime.execute("detour_eta", "ship_navigate", values(target, 20), Map.of()), "Navigation failed");
            var paths = (Map<String, Object>)field(fixture.runtime, "navigationPathStates");
            var type = Class.forName(ShipControlModuleRuntime.class.getName() + "$NavigationPathState");
            var ctor = type.getDeclaredConstructor(); ctor.setAccessible(true);
            var path = ctor.newInstance();
            set(path, "waypoints", List.of(origin.add(0, 0, 1000), origin.add(2000, 0, 1000), target));
            set(path, "waypointIndex", 0);
            String key = ((Map<String, ?>)field(fixture.runtime, "activeCommands")).keySet().stream().filter(val -> val.startsWith("detour_eta")).findFirst().orElseThrow();
            paths.put(key, path);
            var progress = fixture.runtime.navigationProgress();
            helper.assertTrue(Math.abs(progress.remainingDistance() - 4000) < .01 && progress.etaSeconds() >= 200,
                    "The ETA used direct distance or the map horizon: " + progress);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void activeScheduleEstimatesRemainOwnedByTheirMatchingTarget(GameTestHelper helper) throws Exception{
        try(var fixture = new NavigationSpeedGameTests.Fixture(helper)){
            var ctrl = (AdvancedContraptionControllerBlockEntity)field(fixture.runtime, "controller");
            var target = ((Vec3)invoke(fixture.telemetry, "position")).add(0, 0, 2000);
            helper.assertTrue(fixture.runtime.execute("schedule_eta", "ship_navigate", values(target, 20), Map.of()), "Navigation failed");
            var data = new CompoundTag();
            data.putBoolean("HasTarget", true);
            data.putDouble("TargetX", target.x); data.putDouble("TargetY", target.y); data.putDouble("TargetZ", target.z);
            data.putLong("EtaSeconds", 123);
            DiagnosticTabletAutoApp.addNavigationProgress(ctrl, data);
            helper.assertTrue(data.getLong("EtaSeconds") == 123, "A matching schedule estimate was overwritten");
            data.putDouble("TargetX", target.x + 1000);
            DiagnosticTabletAutoApp.addNavigationProgress(ctrl, data);
            helper.assertTrue(data.getDouble("TargetX") == target.x && data.getLong("EtaSeconds") != 123,
                    "A manual target inherited an unrelated schedule ETA");
        }
        helper.succeed();
    }

    private static Map<String, Double> values(Vec3 target, double speed){
        return Map.of("x", target.x, "y", target.y, "z", target.z, "speed", speed, "tolerance", 1D, "avoid_collisions", 1D);
    }

    private static CompoundTag snapshot(AdvancedContraptionControllerBlockEntity ctrl){
        var data = new CompoundTag(); data.putLong("EtaSeconds", -1);
        DiagnosticTabletAutoApp.addNavigationProgress(ctrl, data);
        return data;
    }

    private static void motion(NavigationSpeedGameTests.Fixture fixture, Vec3 position, Vec3 velocity) throws Exception{
        var ctor = fixture.telemetry.getClass().getDeclaredConstructors()[0]; ctor.setAccessible(true);
        var telemetry = ctor.newInstance(true, position, velocity, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        invoke(fixture.runtime, "cacheTelemetry", fixture.body, telemetry);
    }
}

package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmAdaptiveStateModel;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Verify tablet, graph and schedule requests through their real command paths
@GameTestHolder("createthrusters_navigation_speed")
@PrefixGameTestTemplate(false)
public final class NavigationSpeedGameTests{
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bothTabletAppsRequestFullPercentageSpeed(GameTestHelper helper) throws Exception{
        var ctrl = new CapturingController();
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "NavigationTest"), ClientInformation.createDefault());
        var ctx = new TabletActionContext(player, ItemStack.EMPTY, null, null);
        invokeStatic(DiagnosticTabletAutoApp.class, "navigate", ctx, ctrl, "2000, 80, 0");
        verifyPercentage(helper, ctrl.parameters, 1);
        invokeStatic(DiagnosticTabletScmActions.class, "navigate", ctrl, ctx, "2000, 80, 0");
        verifyPercentage(helper, ctrl.parameters, 1);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void schedulesSeparateCruisePercentagesFromDockingSpeeds(GameTestHelper helper) throws Exception{
        var ctrl = new CapturingController();
        var schedule = field(ctrl, "shippingScheduleRuntime");
        set(schedule, "routeThrottle", .75D);
        var cruise = (Map<String, Double>)invoke(schedule, "routeCruiseParameters", Map.of("speed", 28.0D));
        verifyPercentage(helper, cruise, .75);
        var docking = (Map<String, Double>)invoke(schedule, "routeControlParameters", Map.of("speed", 1.0D));
        helper.assertTrue(docking.get("speed") == 1 && !docking.containsKey("speed_fraction"),
                "Precision docking was converted into a cruise percentage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void graphPercentagesUseLiveAuthorityWithoutAFixedCruiseCeiling(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            Object full = fixture.command(1);
            double maximum = number(invoke(fixture.runtime, "navigationSpeedCommand", full, fixture.telemetry), "targetSpeed");
            helper.assertTrue(maximum > 100, "The navigate node retained its fixed 28 blocks/s ceiling");
            Object half = fixture.command(.5);
            double partial = number(invoke(fixture.runtime, "navigationSpeedCommand", half, fixture.telemetry), "targetSpeed");
            helper.assertTrue(Math.abs(partial - maximum * .5) < 1E-6,
                    "The speed percentage was lost or divided twice");
            helper.assertTrue(number(full, "driveThrottle") == 1 && number(half, "driveThrottle") == .5,
                    "Propulsion could not use the requested power");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void clearCorridorsAllowFullSpeedWithAnticipatoryCollisionScans(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            Object plan = fixture.speedPlan();
            helper.assertTrue(number(plan, "scanRange") >= 10,
                    "Collision scans omitted the acceleration possible before the next control update");
            helper.assertTrue(number(plan, "permittedSpeed") > 100,
                    "The clear corridor retained a fixed cruise or collision ceiling: " + plan);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nearbyWallsStillLimitHighAuthorityShips(GameTestHelper helper) throws Exception{
        Map<BlockPos, BlockState> prev = new LinkedHashMap<>();
        try(var fixture = new Fixture(helper)){
            var center = BlockPos.containing((Vec3)invoke(fixture.telemetry, "position")).offset(0, 0, 8);
            for(int x = -4; x <= 4; x++){
                for(int y = -4; y <= 4; y++){
                    var pos = center.offset(x, y, 0);
                    prev.put(pos, helper.getLevel().getBlockState(pos));
                    helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                }
            }
            Object plan = fixture.speedPlan();
            double speed = number(plan, "permittedSpeed");
            double clearance = number(plan, "travelClearance");
            helper.assertTrue(speed > 0 && speed < 100 && clearance < 8,
                    "The live collision envelope ignored the wall: " + plan);
            helper.assertTrue(speed * .35 + speed * speed / 200 <= Math.max(0, clearance - .25) + 1E-6,
                    "The high-authority ship could not stop before the wall: " + plan);
        }finally{
            prev.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 3));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void savedPercentCommandsResumeWithoutBecomingAbsoluteSpeedCommands(GameTestHelper helper) throws Exception{
        try(var fixture = new Fixture(helper)){
            Object command = fixture.command(.8);
            var tags = (ListTag)invokeStatic(ShipControlModuleRuntime.class, "commandListTag", List.of(command));
            helper.assertTrue(tags.getCompound(0).getDouble("SpeedFraction") == .8,
                    "Saving lost the requested percentage");
            Map<String, Object> restored = new LinkedHashMap<>();
            invokeStatic(ShipControlModuleRuntime.class, "readCommands", tags, restored);
            var resolved = invoke(fixture.runtime, "navigationSpeedCommand", restored.values().iterator().next(), fixture.telemetry);
            helper.assertTrue(number(resolved, "speedFraction") == .8 && number(resolved, "targetSpeed") > 100,
                    "Restoring a tablet or node command reintroduced its fixed cruise ceiling");
            tags.getCompound(0).remove("SpeedFraction");
            tags.getCompound(0).putDouble("TargetSpeed", 12);
            restored.clear();
            invokeStatic(ShipControlModuleRuntime.class, "readCommands", tags, restored);
            resolved = invoke(fixture.runtime, "navigationSpeedCommand", restored.values().iterator().next(), fixture.telemetry);
            helper.assertTrue(number(resolved, "targetSpeed") == 12 && number(resolved, "speedFraction") < 0,
                    "A legacy absolute-speed command changed its units");
        }
        helper.succeed();
    }

    private static void verifyPercentage(GameTestHelper helper, Map<String, Double> values, double expected){
        helper.assertTrue(values.getOrDefault("speed_fraction", 0.0D) == 1
                        && values.get("speed") == expected && values.getOrDefault("avoid_collisions", 1.0D) == 1,
                "Navigation used a tiny absolute speed or lost its collision policy: " + values);
    }

    private static final class CapturingController extends AdvancedContraptionControllerBlockEntity{
        private Map<String, Double> parameters;

        private CapturingController(){ super(BlockPos.ZERO, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()); }

        @Override
        public boolean executeShipControlGraphCommand(String id, String type, Map<String, Double> values, Map<String, String> text){
            parameters = Map.copyOf(values);
            return true;
        }
    }

    static final class Fixture implements AutoCloseable{
        private final GameTestHelper helper;
        final ServerSubLevel body;
        final ShipControlModuleRuntime runtime;
        final Object telemetry;
        private final Vec3 target;

        Fixture(GameTestHelper helper) throws Exception{
            this.helper = helper;
            body = ShipDoorGameTests.cabin(helper, AllBlocks.TRAIN_DOOR.getDefaultState(), false);
            var view = body.getPlot().getEmbeddedLevelAccessor();
            view.setBlock(new BlockPos(0, 1, 0), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
            view.setBlock(new BlockPos(0, 2, 0), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
            var pos = body.getPlot().getCenterBlock().offset(0, 2, 0);
            var ctrl = (AdvancedContraptionControllerBlockEntity)body.getLevel().getBlockEntity(pos);
            runtime = (ShipControlModuleRuntime)field(ctrl, "shipControlRuntime");
            var map = new ShipControlMap(UUID.randomUUID(), helper.getLevel().dimension().location().toString(),
                    body.getUniqueId(), pos, Vec3.ZERO, List.of(), List.of(), List.of(), List.of(), List.of(), 0);
            ShipControlMapStore.save(helper.getLevel(), map);
            runtime.setMapId(map.id());
            runtime.tick();
            telemetry = invoke(runtime, "telemetry");
            target = ((Vec3)invoke(telemetry, "position")).add(0, 0, 2000);
            set(runtime, "adaptiveStateModel", ScmAdaptiveStateModel.sample(List.of(
                    new ScmAdaptiveStateModel.Actuator(new Vec3(0, 0, 100), Vec3.ZERO, 0),
                    new ScmAdaptiveStateModel.Actuator(new Vec3(0, 0, -100), Vec3.ZERO, 0)), .05, .25, .01));
        }

        private Object command(double fraction) throws Exception{
            helper.assertTrue(runtime.execute("speed_test", "ship_navigate", Map.of("x", target.x, "y", target.y,
                    "z", target.z, "speed", fraction, "speed_fraction", 1.0D,
                    "tolerance", 1.0D, "avoid_collisions", 1.0D), Map.of()),
                    "The initialized SCM rejected a percentage navigation command");
            return ((Map<?, ?>)field(runtime, "activeCommands")).values().stream()
                    .filter(val -> { try{ return "ship_navigate".equals(invoke(val, "type")); }catch(Exception err){ throw new RuntimeException(err); } })
                    .findFirst().orElseThrow();
        }

        private Object speedPlan() throws Exception{
            Object cmd = invoke(runtime, "navigationSpeedCommand", command(1), telemetry);
            var forward = new Vec3(0, 0, 1);
            var guidance = new ShipControlModuleRuntime.NavigationGuidance(
                    forward, target, .6, false, true, false, forward, false);
            String key = (String)invokeStatic(ShipControlModuleRuntime.class, "commandKey", "speed_test", "ship_navigate");
            invoke(runtime, "navigationSpeedPlan", key, telemetry, cmd, guidance, forward, forward, false);
            // Finish the same bounded work a live ship advances across successive server ticks
            for(Object pending : (java.util.Collection<?>)field(runtime, "safetyHullScans")){
                Object work = field(pending, "work");
                for(int idx = 0; idx < 64 && !(boolean)invoke(work, "isComplete"); idx++){
                    invoke(work, "advance", 32_768, 50_000_000L);
                }
                helper.assertTrue((boolean)invoke(work, "isComplete"), "The collision sweep did not complete");
            }
            return invoke(runtime, "navigationSpeedPlan", key, telemetry, cmd, guidance, forward, forward, false);
        }

        @Override public void close(){
            runtime.close();
            SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body));
        }
    }

    private static double number(Object owner, String name) throws Exception{ return (double)invoke(owner, name); }

    static Object field(Object owner, String name) throws Exception{
        Class<?> type = owner.getClass();
        while(type != null){
            try{ var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch(NoSuchFieldException err){ type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    static void set(Object owner, String name, Object val) throws Exception{
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, val);
    }

    static Object invoke(Object owner, String name, Object... args) throws Exception{
        return invokeMethod(owner.getClass(), owner, name, args);
    }

    private static Object invokeStatic(Class<?> type, String name, Object... args) throws Exception{
        return invokeMethod(type, null, name, args);
    }

    private static Object invokeMethod(Class<?> type, Object owner, String name, Object[] args) throws Exception{
        for(Method method : type.getDeclaredMethods()){
            if(!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            method.setAccessible(true);
            return method.invoke(owner, args);
        }
        throw new NoSuchMethodException(name);
    }
}

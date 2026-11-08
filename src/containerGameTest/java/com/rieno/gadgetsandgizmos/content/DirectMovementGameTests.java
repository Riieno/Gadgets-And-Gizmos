package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmPrecisionAllocator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.rieno.gadgetsandgizmos.content.NavigationSpeedGameTests.field;
import static com.rieno.gadgetsandgizmos.content.NavigationSpeedGameTests.invoke;
import static com.rieno.gadgetsandgizmos.content.NavigationSpeedGameTests.set;

// Exercise direct thrust together with the same hover and stabilization used by live ships
@GameTestHolder("createthrusters_navigation_speed")
@PrefixGameTestTemplate(false)
public final class DirectMovementGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void movementNodesKeepTheirThrustAtHighSpeed(GameTestHelper helper) throws Exception{
        try(var fixture = new Flight(helper)){
            var directions = Map.of("ship_forward", fixture.forward, "ship_backward", fixture.forward.scale(-1),
                    "ship_reverse", fixture.forward.scale(-1), "ship_strafe", fixture.right,
                    "ship_strafe_left", fixture.right.scale(-1), "ship_strafe_right", fixture.right,
                    "ship_ascend", fixture.up, "ship_descend", fixture.up.scale(-1));
            for(var entry : directions.entrySet()){
                for(double amount : new double[]{.5, 1}){
                    fixture.clear();
                    fixture.command("hover", "ship_hover", "strength", 1);
                    fixture.command("stabilize", "ship_stabilize", "strength", 1);
                    fixture.command("drive", entry.getKey(), "amount", amount);
                    var slow = fixture.demand(Vec3.ZERO, Vec3.ZERO);
                    var fast = fixture.demand(entry.getValue().scale(300), Vec3.ZERO);
                    Vec3 requested = (Vec3)invoke(fast, "controlForce");
                    helper.assertTrue(Math.abs(requested.dot(entry.getValue()) - amount) < 1E-6,
                            entry.getKey() + " lost its requested thrust at high speed: " + requested);
                    helper.assertTrue(((Vec3)invoke(slow, "force")).distanceTo((Vec3)invoke(fast, "force")) < 1E-6,
                            entry.getKey() + " applied a velocity ceiling");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hoverYieldsToVerticalInputAndRecoversWhenItIsReleased(GameTestHelper helper) throws Exception{
        try(var fixture = new Flight(helper)){
            for(var entry : Map.of("ship_ascend", 1D, "ship_descend", -1D).entrySet()){
                fixture.clear();
                fixture.command("hover", "ship_hover", "strength", 1);
                fixture.command("drive", entry.getKey(), "amount", 1);
                Vec3 velocity = fixture.up.scale(entry.getValue() * 40);
                var moving = fixture.demand(velocity, Vec3.ZERO);
                helper.assertTrue(((Vec3)invoke(moving, "controlForce")).dot(velocity) > 0,
                        "Hover opposed active " + entry.getKey());
                fixture.command("drive", entry.getKey(), "amount", 0);
                var released = fixture.demand(velocity, Vec3.ZERO);
                helper.assertTrue(((Vec3)invoke(released, "controlForce")).dot(velocity) < 0,
                        "Hover did not arrest vertical motion after releasing " + entry.getKey());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void simultaneousMovementKeepsHoverAndStabilizationActive(GameTestHelper helper) throws Exception{
        try(var fixture = new Flight(helper)){
            fixture.command("forward", "ship_forward", "amount", 1);
            fixture.command("strafe", "ship_strafe_right", "amount", 1);
            fixture.command("ascend", "ship_ascend", "amount", 1);
            fixture.command("hover", "ship_hover", "strength", 1);
            fixture.command("stabilize", "ship_stabilize", "strength", 1);
            Vec3 rate = fixture.right.scale(.15);
            var demand = fixture.demand(new Vec3(200, 200, 200), rate);
            Vec3 force = (Vec3)invoke(demand, "controlForce");
            helper.assertTrue(force.dot(fixture.forward) > .99 && force.dot(fixture.right) > .99
                            && force.dot(fixture.up) > .99, "Simultaneous movement lost a thrust axis: " + force);
            helper.assertTrue(((Vec3)invoke(demand, "torque")).dot(rate) < 0,
                    "Movement suppressed the stabilization correction");
            helper.assertTrue(((Vec3)invoke(invoke(demand, "gravitySupport"), "physicalForce")).lengthSqr() > 0,
                    "Movement suppressed gravity compensation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void unevenEnginesYieldThrustToHoverAndStabilization(GameTestHelper helper) throws Exception{
        try(var fixture = new Flight(helper, false)){
            fixture.command("forward", "ship_forward", "amount", 1);
            fixture.command("hover", "ship_hover", "strength", 1);
            fixture.command("stabilize", "ship_stabilize", "strength", 1);
            var demand = fixture.demand(fixture.forward.scale(300), fixture.right.scale(.05));
            Vec3 hold = (Vec3)invoke(invoke(demand, "gravitySupport"), "physicalForce");
            Vec3 force = ScmPrecisionAllocator.physicalForce(fixture.units,
                    ((Vec3)invoke(demand, "force")).subtract((Vec3)invoke(invoke(demand, "gravitySupport"), "demand")), hold).add(hold);
            Vec3 torque = ScmPrecisionAllocator.physicalTorque(fixture.units, (Vec3)invoke(demand, "torque"));
            var allocation = invoke(fixture.nativeFixture.runtime, "precisionAllocation", fixture.map,
                    fixture.units, force, torque, Vec3.ZERO);
            double[] controls = (double[])invoke(allocation, "controls");
            Vec3 actualForce = Vec3.ZERO, actualTorque = Vec3.ZERO;
            for(int idx = 0; idx < controls.length; idx++){
                actualForce = actualForce.add(fixture.units.get(idx).force().scale(controls[idx]));
                actualTorque = actualTorque.add(fixture.units.get(idx).torque().scale(controls[idx]));
            }
            double available = ScmPrecisionAllocator.physicalForce(fixture.units, fixture.forward).dot(fixture.forward);
            double travel = actualForce.dot(fixture.forward);
            helper.assertTrue(travel > available * .3 && travel < available * .95,
                    "Uneven propulsion did not retain useful balanced thrust: " + travel + " / " + available);
            helper.assertTrue(actualTorque.distanceTo(torque) < available * .01,
                    "Full thrust overrode the stabilizer: " + actualTorque + " / " + torque);
            helper.assertTrue(actualForce.subtract(hold).dot(fixture.up) > -hold.length() * .02,
                    "Full thrust starved hover support: " + actualForce + " / " + hold);
        }
        helper.succeed();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final class Flight implements AutoCloseable{
        private final GameTestHelper helper;
        private final NavigationSpeedGameTests.Fixture nativeFixture;
        private final ShipControlMap map;
        private final List<ScmPrecisionAllocator.Unit> units;
        private final SableAssemblyDynamicsApi.Snapshot dynamics;
        private final Vec3 forward, up, right;
        private final Object topology, gravity;

        private Flight(GameTestHelper helper) throws Exception{
            this(helper, true);
        }

        @SuppressWarnings("unchecked")
        private Flight(GameTestHelper helper, boolean allDirections) throws Exception{
            this.helper = helper;
            nativeFixture = new NavigationSpeedGameTests.Fixture(helper);
            var runtime = nativeFixture.runtime;
            forward = (Vec3)invoke(runtime, "controllerForwardRoot");
            up = (Vec3)invoke(runtime, "controllerUpRoot");
            right = forward.cross(up).normalize();
            dynamics = SableAssemblyDynamicsApi.sample(nativeFixture.body);
            helper.assertTrue(dynamics.massAvailable(), "The native fixture did not expose its mass");
            topology = invoke(runtime, "assemblyTopology", nativeFixture.body);
            double thrust = dynamics.mass() * 40;
            var engines = new ArrayList<ShipControlMap.PropulsionUnit>();
            for(Vec3 dir : List.of(forward, forward.scale(-1), right, right.scale(-1), up, up.scale(-1))){
                if(!allDirections && dir.dot(forward) < .9 && dir.dot(up) < .9) continue;
                for(int side : new int[]{-1, 1}){
                    double capacity = dir.dot(forward) > .9 && side > 0 ? thrust * .4 : thrust;
                    Vec3 arm = Math.abs(dir.dot(forward)) > .9 ? right
                            : Math.abs(dir.dot(right)) > .9 ? up : forward;
                    engines.add(new ShipControlMap.PropulsionUnit(engines.size(), nativeFixture.body.getUniqueId(),
                            new BlockPos(engines.size(), 0, 0), "minecraft:stone", "thruster", true,
                            arm.scale(side), dir, 0, 1, 0, capacity, 0, List.of()));
                }
            }
            map = new ShipControlMap(UUID.randomUUID(), helper.getLevel().dimension().location().toString(),
                    nativeFixture.body.getUniqueId(), nativeFixture.body.getPlot().getCenterBlock().offset(0, 2, 0),
                    Vec3.ZERO, engines, List.of(), List.of(), List.of(), List.of(), 0);
            units = (List<ScmPrecisionAllocator.Unit>)invoke(runtime, "precisionUnits", map);
            gravity = invoke(runtime, "gravityCompensation", map);
            set(runtime, "adaptiveStateModel", invoke(runtime, "sampleAdaptiveStateModel", map, dynamics,
                    invoke(gravity, "physicalForce"), units));
            clear();
        }

        private void clear() throws Exception{ ((Map<?, ?>)field(nativeFixture.runtime, "activeCommands")).clear(); }

        private void command(String id, String type, String input, double val){
            helper.assertTrue(nativeFixture.runtime.execute(id, type, Map.of(input, val), Map.of()), "SCM rejected " + type);
        }

        private Object demand(Vec3 velocity, Vec3 angularVelocity) throws Exception{
            var type = nativeFixture.telemetry.getClass();
            var ctor = type.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            var telemetry = ctor.newInstance(true, invoke(nativeFixture.telemetry, "position"), velocity,
                    angularVelocity, Vec3.ZERO, Vec3.ZERO);
            return invoke(nativeFixture.runtime, "demandFor", telemetry, map, dynamics, topology, gravity);
        }

        @Override public void close(){ nativeFixture.close(); }
    }
}

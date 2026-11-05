package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.ContraptionDiagramControllerCompat;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicsTelemetry;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.AllBlocks;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.List;

// Exercise real sail propulsion, native diagrams and graph telemetry across a bearing joint
@GameTestHolder("createthrusters_bearing_forces")
@PrefixGameTestTemplate(false)
public final class VectorBearingForceGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty", batch = "bearing_force_report", timeoutTicks = 120)
    public static void rotatingSailsReachParentDiagramsAndTelemetry(GameTestHelper helper){
        fixture(helper, fixture -> helper.runAfterDelay(30, () -> {
            try{
                verifyReports(helper, fixture);
                var velocity = RigidBodyHandle.of(fixture.parent()).getLinearVelocity(new Vector3d());
                var force = fixture.parent().logicalPose().transformNormal(
                        vector(fixture.bearing().forceTelemetry().getFirst().force()), new Vector3d());
                helper.assertTrue(velocity.x * force.x + velocity.z * force.z > 1.0E-5D,
                        "Sail propulsion did not move the parent through its native joint: velocity=" + velocity + ", force=" + force);
            }finally{ fixture.close(); }
            helper.succeed();
        }));
    }

    @GameTest(template = "empty", batch = "bearing_force_stop", timeoutTicks = 120)
    public static void stoppingTheShaftClearsParentForceReports(GameTestHelper helper){
        fixture(helper, fixture -> helper.runAfterDelay(30, () -> {
            try{
                verifyReports(helper, fixture);
                fixture.parent().getPlot().getEmbeddedLevelAccessor().setBlock(new BlockPos(0, 0, 1), Blocks.AIR.defaultBlockState(), 3);
            }catch(Throwable err){ fixture.close(); throw err; }
            helper.runAfterDelay(10, () -> {
                try{ verifyStopped(helper, fixture); }
                finally{ fixture.close(); }
                helper.succeed();
            });
        }));
    }

    @GameTest(template = "empty", batch = "bearing_force_detach", timeoutTicks = 120)
    public static void disassemblingTheHeadClearsParentForceReports(GameTestHelper helper){
        fixture(helper, fixture -> helper.runAfterDelay(30, () -> {
            try{
                verifyReports(helper, fixture);
                fixture.bearing().disassembleMountedBlock();
            }catch(Throwable err){ fixture.close(); throw err; }
            helper.runAfterDelay(2, () -> {
                try{ verifyStopped(helper, fixture); }
                finally{ fixture.close(); }
                helper.succeed();
            });
        }));
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static void fixture(GameTestHelper helper, java.util.function.Consumer<Fixture> ready){
        var parent = ShipDoorGameTests.newBody(helper, helper.absolutePos(BlockPos.ZERO).getCenter().add(0, 64, 0));
        var plot = parent.getPlot(); var origin = plot.getCenterBlock();
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO,
                CTBlocks.VECTOR_BEARING.get().defaultBlockState().setValue(VectorBearingBlock.FACING, Direction.NORTH), 3);
        plot.getEmbeddedLevelAccessor().setBlock(new BlockPos(0, 0, 1),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH), 3);
        parent.updateBoundingBox();
        var motor = (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)parent.getLevel().getBlockEntity(origin.offset(0, 0, 1));
        motor.generatedSpeed.setValue(256);
        var bearing = (VectorBearingBlockEntity)parent.getLevel().getBlockEntity(origin);
        // Let the native topology settle before requesting a nested assembly
        helper.runAfterDelay(10, () -> {
            bearing.toggleMountedBlock();
            var head = (ServerSubLevel)SubLevelContainer.getContainer(parent.getLevel()).getSubLevel(bearing.getMountedSubLevelId());
            helper.assertTrue(head != null, "Vector Bearing head failed to assemble");
            var headOrigin = head.getPlot().getCenterBlock();
            var mounted = bearing.getMountedLocalPos().subtract(headOrigin);
            for(var offset : List.of(new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0), new BlockPos(0, 1, 0), new BlockPos(0, -1, 0))){
                head.getPlot().getEmbeddedLevelAccessor().setBlock(mounted.offset(offset), AllBlocks.SAIL.getDefaultState(), 3);
            }
            head.updateBoundingBox();
            head.enableIndividualQueuedForcesTracking(true);
            bearing.setComputerAnglesDegrees(12, 18);
            ready.accept(new Fixture(parent, bearing, head));
        });
    }

    private static void verifyReports(GameTestHelper helper, Fixture fixture){
        var bearing = fixture.bearing(); var parent = fixture.parent();
        helper.assertTrue(bearing.isActive() && Math.abs(bearing.getSpeed()) > 0,
                "Native motor did not drive the sail fixture: speed=" + bearing.getSpeed() + ", sails=" + bearing.getMountedSailPower());
        var reported = bearing.forceTelemetry();
        helper.assertTrue(reported.size() == 1, "The parent did not expose its mounted propeller force");
        var expected = vector(reported.getFirst().force());
        var link = (VectorBearingLinkBlockEntity)parent.getLevel().getBlockEntity(bearing.getMountedLocalPos());
        helper.assertTrue(expected.lengthSquared() > 0, "Mounted sail thrust was zero: speed=" + bearing.getSpeed()
                + ", thrust=" + link.getThrust() + ", airflow=" + link.getAirflow()
                + ", scaling=" + link.getAirflowScaling() + ", pressure=" + link.getCurrentAirPressure());
        var snapshot = SablePhysicsTelemetry.sample(parent.getLevel(), parent.getUniqueId());
        var propulsion = snapshot.forces().stream().filter(force -> force.groupId().equals(propulsionId())).toList();
        helper.assertTrue(propulsion.size() == 1, "Parent telemetry omitted or duplicated mounted propulsion: " + propulsion);
        helper.assertTrue(propulsion.getFirst().force().distance(expected) < 1.0E-6D, "Parent telemetry changed the signed force or its units");
        var point = vector(reported.getFirst().point());
        helper.assertTrue(propulsion.getFirst().point().distance(point) < 1.0E-6D, "Parent telemetry used the stationary shaft as the application point");
        var expectedWorld = parent.logicalPose().transformNormal(expected, new Vector3d());
        helper.assertTrue(propulsion.getFirst().worldForce().distance(expectedWorld) < 1.0E-6D, "Parent world force used the head's local frame");
        var packet = diagram(parent);
        var diagramForces = packet.forces().get(ForceGroups.PROPULSION.get());
        helper.assertTrue(diagramForces != null && diagramForces.size() == 1
                && diagramForces.getFirst().force().distance(expected) < 1.0E-6D
                && diagramForces.getFirst().point().distance(point) < 1.0E-6D, "Native parent diagram omitted, duplicated or rescaled the sail force");
        var target = new ControllerDiscoveryNode("bearing-force-parent", ControllerDiscoveryKind.UNKNOWN, "fixture",
                ContraptionNetworkLinkerData.CONTRAPTION_DIAGRAM_TARGET_ID, "Parent", parent.getUniqueId(), parent.getPlot().getCenterBlock());
        var graph = ContraptionDiagramControllerCompat.readTargetData(parent.getLevel(), target, "world_total_force_z");
        helper.assertTrue(graph != null && Math.abs(graph.asNumber() - snapshot.worldTotalForce().z) < 1.0E-6D,
                "Ship telemetry graph ports did not use the parent's complete force report");
        var parentGroups = parent.getQueuedForceGroups();
        var physicalParent = parentGroups == null ? null : parentGroups.get(ForceGroups.PROPULSION.get());
        helper.assertTrue(physicalParent == null || physicalParent.getRecordedPointForces().isEmpty(),
                "Diagnostics applied sail thrust a second time to the physical parent");
        var headGroups = fixture.head().getQueuedForceGroups();
        var physicalHead = headGroups == null ? null : headGroups.get(ForceGroups.PROPULSION.get());
        helper.assertTrue(physicalHead != null && !physicalHead.getRecordedPointForces().isEmpty(),
                "The physical sail force stopped being applied to its head");
    }

    private static void verifyStopped(GameTestHelper helper, Fixture fixture){
        helper.assertTrue(fixture.bearing().forceTelemetry().isEmpty(), "Inactive or removed head retained a force report");
        var snapshot = SablePhysicsTelemetry.sample(fixture.parent().getLevel(), fixture.parent().getUniqueId());
        helper.assertTrue(snapshot.forces().stream().noneMatch(force -> force.groupId().equals(propulsionId())), "Parent telemetry retained stale sail thrust");
        helper.assertTrue(!diagram(fixture.parent()).forces().containsKey(ForceGroups.PROPULSION.get()), "Native diagram retained stale sail thrust");
    }

    private static Vector3d vector(net.minecraft.world.phys.Vec3 val){ return new Vector3d(val.x, val.y, val.z); }
    private static String propulsionId(){ return ForceGroups.REGISTRY.getKey(ForceGroups.PROPULSION.get()).toString(); }

    private static DiagramDataPacket diagram(ServerSubLevel body){
        try{
            var method = DiagramEntity.class.getDeclaredMethod("makeDiagramDataPacket", ServerSubLevel.class);
            method.setAccessible(true);
            return (DiagramDataPacket)method.invoke(null, body);
        }catch(ReflectiveOperationException err){ throw new AssertionError("Could not read the native diagram packet", err); }
    }

    private record Fixture(ServerSubLevel parent, VectorBearingBlockEntity bearing, ServerSubLevel head) implements AutoCloseable{
        @Override public void close(){
            var container = SubLevelContainer.getContainer(parent.getLevel());
            var loaded = List.of(head, parent).stream().filter(body -> container.getSubLevel(body.getUniqueId()) == body).toList();
            if(!loaded.isEmpty()) SableSubLevelLifecycleApi.remove(parent.getLevel(), loaded);
        }
    }
}

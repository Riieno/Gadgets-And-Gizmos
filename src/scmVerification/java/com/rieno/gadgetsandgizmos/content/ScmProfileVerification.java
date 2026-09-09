package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyBoundsApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Verify profile migration and editing without starting a game world. */
public final class ScmProfileVerification {
    public static void main(String[] args){
        ScmOrientation defaults = new ScmOrientation(Direction.SOUTH, Direction.UP);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("Version", 4);
        ScmConfigurationProfile profile = ScmConfigurationProfile.fromTag(legacy);
        require(profile.orientationOverride() == null, "Legacy profile must follow ACC");
        require(profile.vehicleType().equals("auto"), "Legacy profiles default to auto vehicle type");
        profile.setVehicleType("car");
        require(profile.resolveOrientation(defaults).equals(defaults), "ACC default sign preserved");
        for(Direction forward : Direction.values()){
            for(Direction up : Direction.values()){
                if(!ScmOrientation.isValid(forward, up)) continue;
                ScmOrientation frame = new ScmOrientation(forward, up);
                profile.setOrientationOverride(frame);
                profile.replace(UUID.randomUUID(), List.of(), Map.of(), Set.of());
                profile = ScmConfigurationProfile.fromTag(profile.toTag());
                require(frame.equals(profile.resolveOrientation(defaults)), "Frame survived save/rebase");
                require(profile.vehicleType().equals("car"), "Explicit mode survives save/rebase");
                require(profile.toTag().getInt("Version") == 6, "Profile version");
            }
        }
        profile.setOrientationOverride(null);
        require(!profile.toTag().contains("Orientation"), "Reset stores automatic defaults");
        require(ScmConfigurationProfile.fromTag(profile.toTag()).resolveOrientation(defaults).equals(defaults),
                "Reset survives reload");
        CompoundTag malformed = profile.toTag();
        CompoundTag orientation = new CompoundTag();
        orientation.putString("Forward", "up");
        orientation.putString("Up", "down");
        malformed.put("Orientation", orientation);
        require(ScmConfigurationProfile.fromTag(malformed).orientationOverride() == null, "Invalid save fallback");
        UUID dockId = UUID.randomUUID();
        ShipDockRegistry.ConnectorTarget connector = new ShipDockRegistry.ConnectorTarget(
                null, new BlockPos(3, 4, 5), new Vec3(3.5D, 4.5D, 6.0D),
                new Vec3(0.0D, 0.0D, 1.0D), new Vec3(0.0D, 1.0D, 0.0D));
        ShipDockRegistry.Dock storedDock = new ShipDockRegistry.Dock(
                dockId, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), null,
                BlockPos.ZERO, Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), "Dock",
                false, false, false, connector.subLevelId(), connector.pos(),
                connector.worldPosition(), connector.facing(), connector.up(), 1L,
                List.of(connector));
        ShipDockRegistry.Dock loadingDock = new ShipDockRegistry.Dock(
                dockId, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), null,
                BlockPos.ZERO, Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), "Dock",
                false, false, false, null, null, null, null, null, 2L, List.of());
        require(ShipDockRegistry.preserveLoadingConnectorTargets(
                        storedDock, loadingDock, false).hasDockingConnector(),
                "Loading a dock erased its persisted connector target");
        require(!ShipDockRegistry.preserveLoadingConnectorTargets(
                        storedDock, loadingDock, true).hasDockingConnector(),
                "An explicit connector edit did not clear its persisted target");
        require(ShipControlModuleRuntime.navigationCollisionScanRange(0.0D) == 8.0D
                        && ShipControlModuleRuntime.navigationCollisionScanRange(8.0D) < 64.0D,
                "Autopilot reused the graph telemetry range as its per-tick collision minimum");
        double movingLookahead =
                ShipControlModuleRuntime.navigationCollisionScanRange(4.0D);
        require(ShipControlModuleRuntime.navigationSafeTravelSpeed(movingLookahead) >= 4.0D,
                "Autopilot live probe no longer covers its stopping envelope");
        require(ShipControlModuleRuntime.navigationCaptureApproachSpeed(6.5D, 6.35D)
                        >= 1.5D
                        && ShipControlModuleRuntime.navigationCaptureApproachSpeed(6.35D, 6.35D)
                        == 0.0D,
                "Autopilot target capture can stall just outside its arrival radius");
        SableAssemblyBoundsApi.Envelope arrivalHull =
                new SableAssemblyBoundsApi.Envelope(3.0D, 4.0D, 2.0D, 5.0D);
        require(Math.abs(ShippingScheduleRuntime.connectorlessArrivalTolerance(arrivalHull)
                        - 7.0D) <= 1.0E-9D,
                "Connectorless arrival does not use the complete hull plus two blocks");
        AdvancedGraphCatalog.Definition brain = AdvancedGraphCatalog.get("scm_brain_debug");
        require(brain != null && AdvancedGraphCatalog.isShipControlPassiveType(brain.id())
                        && "map".equals(brain.outputs().get("scm_brain_data")),
                "SCM brain debug graph node is missing or not passive");
        AdvancedGraphDocument retiredGraph = new AdvancedGraphDocument();
        retiredGraph.nodes().add(new AdvancedGraphDocument.Node(
                "old_initialize", "ship_initialize", "Initialize", 0.0D, 0.0D,
                new CompoundTag()));
        retiredGraph.nodes().add(new AdvancedGraphDocument.Node(
                "survivor", "constant_boolean", "Keep", 100.0D, 0.0D,
                new CompoundTag()));
        retiredGraph.edges().add(new AdvancedGraphDocument.Edge(
                "old_edge", "old_initialize", "complete", "survivor", "value"));
        AdvancedGraphDocument.FunctionGraph retiredFunction =
                new AdvancedGraphDocument.FunctionGraph("legacy", "Legacy");
        retiredFunction.nodes().add(new AdvancedGraphDocument.Node(
                "nested_initialize", "ship_initialize", "Initialize", 0.0D, 0.0D,
                new CompoundTag()));
        retiredFunction.nodes().add(new AdvancedGraphDocument.Node(
                "nested_survivor", "constant_boolean", "Keep", 100.0D, 0.0D,
                new CompoundTag()));
        retiredFunction.edges().add(new AdvancedGraphDocument.Edge(
                "nested_old_edge", "nested_survivor", "value",
                "nested_initialize", "exec"));
        retiredGraph.functions().add(retiredFunction);
        retiredGraph.setScmActionFunction("ship_initialize", "legacy");
        AdvancedGraphDocument cleanedGraph = AdvancedGraphDocument.fromTag(
                retiredGraph.toTag());
        require(cleanedGraph.nodes().stream().noneMatch(
                        node -> "ship_initialize".equals(node.type()))
                        && cleanedGraph.edges().stream().noneMatch(edge ->
                        "old_initialize".equals(edge.fromNode())
                                || "old_initialize".equals(edge.toNode()))
                        && cleanedGraph.function("legacy").nodes().stream().noneMatch(
                        node -> "ship_initialize".equals(node.type()))
                        && cleanedGraph.function("legacy").edges().isEmpty()
                        && cleanedGraph.scmActionFunction("ship_initialize").isBlank(),
                "Retired Initialize nodes or their incident edges survived graph load");
        System.out.println("SCM profiles: legacy defaults, 24 custom frames, rebase, reload and reset passed.");
    }

    private static void require(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }
}

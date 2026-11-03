package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelTelemetryApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableMapMarkers;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationMapData;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationHeading;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkSnapshot;
import com.rieno.gadgetsandgizmos.lib.shipping.LogisticsBindings.Group;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppAccess;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPurchaseScope;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Supply ship telemetry, music controls and shipping resources to the painted dashboard
final class DiagnosticTabletAutoApp {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet auto app
    private DiagnosticTabletAutoApp() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the diagnostic tablet auto app
    static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action) {
        AutoTarget target = target(ctx);
        if (target == null) {
            sendSnapshot(ctx, appSnapshot(ctx));
            return failure("G&G Auto is available only on an initialized ship");
        }
        AdvancedContraptionControllerBlockEntity controller = target.controller();
        String val = action.arguments().getOrDefault("value", "").strip();
        TabletActionHandler.Result res = switch (action.actionId()) {
            case "refresh", "select" -> success("");
            case "hover" -> command(ctx, controller, "ship_hover", java.util.Map.of("strength", 1.0D));
            case "dock" -> command(ctx, controller, "ship_dock", java.util.Map.of());
            case "navigate" -> navigate(ctx, controller, val);
            case "media_pause", "media_previous", "media_next" -> media(ctx, action);
            default -> failure("Unknown G&G Auto action");
        };
        CompoundTag data = snapshot(ctx, target, "drive".equals(action.tabId()));
        if("manifest".equals(action.tabId())){
            if(!data.getBoolean("ManifestInstalled")) data.putString("Error", "Install Manifest to inspect ship resources");
            else if(!WorldAccessPolicy.canAccessLocal(ctx.player(), ctx.player().serverLevel(),
                    SimulatedHelper.getContainingSubLevelId(controller), controller.getBlockPos())){
                data.putString("Error", "This ship's storage is not accessible");
            }else{
                String[] args = val.split("\\|", 3);
                String category = args.length > 0 && Set.of("items", "fluids", "fuel").contains(args[0]) ? args[0] : "items";
                int offset = 0;
                try{ if(args.length > 1) offset = Math.max(0, Integer.parseInt(args[1])); }catch(NumberFormatException ignored){}
                String filter = args.length > 2 ? args[2] : "";
                data.put("Manifest", resourceSnapshot(controller, category, filter, offset));
            }
        }
        if(!res.success()) data.putString("Error", res.message().getString());
        sendSnapshot(ctx, data);
        return res;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is available
    static boolean available(TabletActionContext ctx) {
        return target(ctx) != null;
    }

    // Get the target
    private static AutoTarget target(TabletActionContext ctx) {
        UUID sourceSubLevelId = sourceSubLevelId(ctx);
        if (sourceSubLevelId == null) return null;
        Set<UUID> connectedSubLevelIds = SableSubLevelTelemetryApi.connectedSubLevelIds(
                ctx.player().serverLevel(), sourceSubLevelId);
        if (connectedSubLevelIds.isEmpty()) return null;
        for(UUID id : connectedSubLevelIds){
            Object candidate = SubLevelBlockEntityCollector.getSubLevel(ctx.player().level(), id);
            if(candidate == null) continue;
            AdvancedContraptionControllerBlockEntity controller = readyController(candidate);
            if (controller != null) {
                return new AutoTarget(controller, sourceSubLevelId, connectedSubLevelIds);
            }
        }
        return null;
    }

    // Get the source sublevel id
    private static UUID sourceSubLevelId(TabletActionContext ctx) {
        if (ctx.placedSource()) return ctx.sourceSubLevelId();
        Object tracking = SimulatedHelper.getEntityTrackingSubLevel(ctx.player());
        return tracking instanceof SubLevel subLevel && !subLevel.isRemoved()
                ? subLevel.getUniqueId() : null;
    }

    // Get the ready controller
    private static AdvancedContraptionControllerBlockEntity readyController(Object subLevel) {
        for(var controller : SubLevelBlockEntityCollector.getBlockEntities(subLevel, AdvancedContraptionControllerBlockEntity.class)){
            if(controller.getShipControlGraphValue("ready").asBoolean()) return controller;
        }
        return null;
    }

    // Navigate the diagnostic tablet auto app
    private static TabletActionHandler.Result navigate(TabletActionContext ctx,
                                                        AdvancedContraptionControllerBlockEntity controller,
                                                        String val) {
        String[] parts = val.split("[, ]+");
        if (parts.length < 3) return failure("Enter X, Y and Z coordinates");
        try {
            return command(ctx, controller, "ship_navigate", java.util.Map.of(
                    "x", Double.parseDouble(parts[0]), "y", Double.parseDouble(parts[1]),
                    "z", Double.parseDouble(parts[2]), "speed", 0.65D,
                    "tolerance", 1.0D, "avoid_collisions", 1.0D));
        } catch (NumberFormatException err) {
            return failure("Coordinates must be numbers");
        }
    }

    // Get the command
    private static TabletActionHandler.Result command(TabletActionContext ctx,
                                                       AdvancedContraptionControllerBlockEntity controller,
                                                       String command,
                                                       java.util.Map<String, Double> values) {
        boolean accepted = controller.executeShipControlGraphCommand(
                "tablet_auto_" + ctx.player().getUUID() + "_" + command,
                command, values, java.util.Map.of());
        return accepted ? success("") : failure("The ship rejected that command");
    }

    // Get the snapshot
    private static CompoundTag snapshot(TabletActionContext ctx, AutoTarget target, boolean navigation) {
        AdvancedContraptionControllerBlockEntity controller = target.controller();
        CompoundTag data = appSnapshot(ctx);
        data.putBoolean("Available", true);
        data.putString("Ship", controller.getShipName());
        data.putString("Status", controller.getShipControlGraphValue("status").asString());
        var trip = controller.getCrnShipDisplayData();
        data.putString("Destination", trip.targetStop());
        data.putBoolean("HasTarget", !trip.targetStop().isBlank());
        data.putDouble("TargetX", trip.targetPosition().x);
        data.putDouble("TargetY", trip.targetPosition().y);
        data.putDouble("TargetZ", trip.targetPosition().z);
        data.putLong("EtaSeconds", trip.etaSeconds());
        var orientation = controller.getScmConfigurationProfile().resolveOrientation(controller.getScmOrientation());
        var forward = SableTransformApi.toWorldDirection(controller, orientation.forwardVector());
        data.putString("Facing", Direction.getNearest((float)forward.x, (float)forward.y, (float)forward.z).getSerializedName());
        data.putDouble("Yaw", NavigationHeading.normalize(Math.toDegrees(Math.atan2(forward.x, -forward.z))));
        SableSubLevelTelemetryApi.Snapshot motion = SableSubLevelTelemetryApi.sample(
                ctx.player().serverLevel(), target.sourceSubLevelId());
        data.putBoolean("PhysicsAvailable", motion.physicsAvailable());
        data.putDouble("Speed", motion.speed());
        data.putDouble("VelocityX", motion.linearVelocity().x);
        data.putDouble("VelocityY", motion.linearVelocity().y);
        data.putDouble("VelocityZ", motion.linearVelocity().z);
        data.putDouble("AngularVelocityX", motion.angularVelocity().x);
        data.putDouble("AngularVelocityY", motion.angularVelocity().y);
        data.putDouble("AngularVelocityZ", motion.angularVelocity().z);
        data.putDouble("X", motion.position().x);
        data.putDouble("Y", motion.position().y);
        data.putDouble("Z", motion.position().z);
        if(navigation) addNavigationMap(ctx, target, motion, data);
        data.putDouble("Weight", shipMass(ctx, target, motion));
        ShipCargoAutomation.FuelStatus fuel = ShipCargoAutomation.fuelStatus(controller);
        data.putDouble("Fuel", fuel.ratio());
        data.putBoolean("FuelInfinite", fuel.infinite());
        ShipCargoAutomation.ResourceStatus resources = ShipCargoAutomation.resourceStatus(controller);
        data.putLong("Cargo", resources.items());
        data.putLong("Fluids", resources.fluids());
        data.putLong("Energy", resources.energy());
        data.putInt("StockNetworks",
                controller.getShipStockNetworkSnapshot().networks().size());
        data.putBoolean("Pilot", controller.hasShippingSchedule());
        data.putLong("SampleTick", ctx.player().serverLevel().getGameTime());
        return data;
    }

    private static void addNavigationMap(TabletActionContext ctx, AutoTarget target, SableSubLevelTelemetryApi.Snapshot motion, CompoundTag data){
        var excluded = new LinkedHashSet<>(target.connectedSubLevelIds());
        excluded.addAll(target.controller().getMappedShipSubLevelIds());
        var destination = data.getBoolean("HasTarget")
                ? new net.minecraft.world.phys.Vec3(data.getDouble("TargetX"), data.getDouble("TargetY"), data.getDouble("TargetZ")) : null;
        var route = target.controller().navigationMapRoute(motion.position(), destination);
        data.put("NavigationMap", new NavigationMapData(nearbyShips(ctx.player().serverLevel(), motion.position(), excluded), route).toTag());
        if(route.target() != null){
            data.putBoolean("HasTarget", true);
            data.putDouble("TargetX", route.target().x); data.putDouble("TargetY", route.target().y); data.putDouble("TargetZ", route.target().z);
        }
    }

    // Show only initialized SCM bodies, keeping loose assemblies and uninitialized controllers off the map
    static List<NavigationMapData.Marker> nearbyShips(ServerLevel level, Vec3 pos, Set<UUID> excluded){
        return SableMapMarkers.capture(level, pos, excluded, 256, body -> readyController(body) != null);
    }

    // Shipping groups define the ship's cargo; dock buffers are deliberately outside this view
    static CompoundTag resourceSnapshot(AdvancedContraptionControllerBlockEntity controller, String category, String filter, int offset){
        var logistics = controller.logistics();
        var pool = switch(category){
            case "fluids" -> logistics.pool(Group.FLUIDS);
            case "fuel" -> logistics.fuelStorage();
            default -> logistics.pool(Group.ITEMS);
        };
        var data = ContainerNetworkSnapshot.capturePool(pool, switch(category){
            case "items" -> "item";
            case "fluids" -> "fluid";
            default -> "";
        }, filter, offset, 32);
        data.putString("Category", category);
        data.putString("Filter", filter);
        return data;
    }

    private static CompoundTag appSnapshot(TabletActionContext ctx){
        var data = new CompoundTag();
        data.putBoolean("MediaInstalled", installed(ctx, DiagnosticTabletMediaApp.ID));
        data.putBoolean("ManifestInstalled", installed(ctx, DiagnosticTabletData.appId("manifest")));
        if(data.getBoolean("MediaInstalled")){
            var media = DiagnosticTabletDatabase.forServer(ctx.player().server).app(ctx.sourceTabletId(), DiagnosticTabletMediaApp.ID);
            data.put("Media", media.getCompound("Playback").copy());
        }
        return data;
    }

    private static boolean installed(TabletActionContext ctx, ResourceLocation app){
        return ctx.sourceTabletId() != null
                && DiagnosticTabletDatabase.forServer(ctx.player().server).installedApps(ctx.sourceTabletId()).contains(app)
                && TabletAppAccess.canUse(TabletAppRegistry.definition(app), TabletAppPurchaseScope.PLAYER,
                ctx.player().getUUID(), ctx.sourceTabletId());
    }

    private static TabletActionHandler.Result media(TabletActionContext ctx, TabletAction action){
        if(!installed(ctx, DiagnosticTabletMediaApp.ID)) return failure("Install Media to use the music controls");
        return DiagnosticTabletMediaApp.execute(ctx, new TabletAction(DiagnosticTabletMediaApp.ID, "player",
                action.actionId().substring("media_".length()), action.arguments()));
    }

    // Get the ship mass
    private static double shipMass(TabletActionContext ctx,
                                   AutoTarget target,
                                   SableSubLevelTelemetryApi.Snapshot src) {
        Set<UUID> subLevelIds = new LinkedHashSet<>(target.connectedSubLevelIds());
        subLevelIds.addAll(target.controller().getMappedShipSubLevelIds());
        subLevelIds.add(target.sourceSubLevelId());
        double mass = 0.0D;
        for (UUID subLevelId : subLevelIds) {
            double bodyMass = SableSubLevelTelemetryApi.sample(
                    ctx.player().serverLevel(), subLevelId).mass();
            if (!Double.isFinite(bodyMass) || bodyMass <= 0.0D) continue;
            if (mass > Double.MAX_VALUE - bodyMass) return Double.MAX_VALUE;
            mass += bodyMass;
        }
        return mass > 0.0D ? mass : src.mass();
    }

    // Send the snapshot
    private static void sendSnapshot(TabletActionContext ctx, CompoundTag data) {
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(
                DiagnosticTabletData.appId("gg_auto"), ctx, data));
    }

    // Create a successful diagnostic tablet auto app
    private static TabletActionHandler.Result success(String msg) {
        return TabletActionHandler.Result.success(Component.literal(msg));
    }

    // Create a failed diagnostic tablet auto app
    private static TabletActionHandler.Result failure(String msg) {
        return TabletActionHandler.Result.failure(Component.literal(msg));
    }

    // Store the auto target
    private record AutoTarget(AdvancedContraptionControllerBlockEntity controller,
                              UUID sourceSubLevelId,
                              Set<UUID> connectedSubLevelIds) {
        // Initialize the auto target
        private AutoTarget {
            connectedSubLevelIds = Set.copyOf(connectedSubLevelIds);
        }
    }
}

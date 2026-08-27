package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEvent;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEventBus;
import com.rieno.gadgetsandgizmos.lib.namedevents.NamedEventSource;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// Queue named graph events and bridge them through the shared transport bus
public final class AdvancedControllerNamedEventBus {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String TRANSPORT_ID = "gadgetsandgizmos:acc";
    private static final Map<MinecraftServer, Set<AdvancedContraptionControllerBlockEntity>> CONTROLLERS =
            new WeakHashMap<>();
    private static NamedEventBus.Subscription transportSubscription;
    private static ExternalBridge externalBridge;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced controller named event bus
    private AdvancedControllerNamedEventBus() {
    }

    // Register the advanced controller named event bus
    public static synchronized void register(AdvancedContraptionControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null
                || controller.getLevel().isClientSide || controller.getLevel().getServer() == null) {
            return;
        }
        installTransport();
        CONTROLLERS.computeIfAbsent(controller.getLevel().getServer(),
                ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(controller);
        if (externalBridge != null) {
            externalBridge.register(controller);
        }
    }

    // Unregister the advanced controller named event bus
    public static synchronized void unregister(AdvancedContraptionControllerBlockEntity controller) {
        if (controller == null) {
            return;
        }
        for (Set<AdvancedContraptionControllerBlockEntity> controllers : CONTROLLERS.values()) {
            controllers.remove(controller);
        }
        if (externalBridge != null) {
            externalBridge.unregister(controller);
        }
    }

    // Install the ACC endpoint on the shared named event transport bus
    private static synchronized void installTransport() {
        if (transportSubscription == null) {
            transportSubscription = NamedEventBus.subscribe(TRANSPORT_ID,
                    AdvancedControllerNamedEventBus::receive);
        }
    }

    // Install one optional controller endpoint bridge for physical transport registration
    public static synchronized void installExternalBridge(ExternalBridge bridge) {
        if (externalBridge == bridge) {
            return;
        }
        if (externalBridge != null) {
            CONTROLLERS.values().forEach(controllers -> controllers.forEach(externalBridge::unregister));
        }
        externalBridge = bridge;
        if (bridge != null) {
            CONTROLLERS.values().forEach(controllers -> controllers.forEach(bridge::register));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Publish the advanced controller named event bus
    public static void publish(AdvancedContraptionControllerBlockEntity src, String name,
                               AdvancedGraphDocument.Value data) {
        publish(src, name, data, 0);
    }

    // Publish the advanced controller named event bus
    public static void publish(AdvancedContraptionControllerBlockEntity src, String name,
                               AdvancedGraphDocument.Value data, int maximumDistance) {
        if (!valid(src) || name == null || name.isBlank()) {
            return;
        }
        int configuredMaximum = Math.max(1, AllConfigs.server().logistics.linkRange.get());
        int distance = Math.max(0, Math.min(maximumDistance, configuredMaximum));
        NamedEventBus.publish(NamedEvent.of(source(src), name,
                GraphRuntime.toLibraryValue(normalize(data)), distance));
    }

    // Publish a named event from an external block entity transport
    public static void publishExternal(BlockEntity src, String name,
                                       AdvancedGraphDocument.Value data) {
        if (!valid(src) || name == null || name.isBlank()) {
            return;
        }
        Vec3 position = SimulatedHelper.toGlobalWorldPosition(
                src, Vec3.atCenterOf(src.getBlockPos()));
        NamedEventSource source = new NamedEventSource(src.getLevel().getServer(),
                src.getLevel().dimension(), position, endpointId(src));
        NamedEventBus.publish(NamedEvent.of(source, name,
                GraphRuntime.toLibraryValue(normalize(data)), 0));
    }

    // Receive one shared named event at every matching ACC graph
    private static void receive(NamedEvent event) {
        if (event == null || event.source() == null) {
            return;
        }
        MinecraftServer server = event.source().server();
        ArrayList<AdvancedContraptionControllerBlockEntity> recipients;
        synchronized (AdvancedControllerNamedEventBus.class) {
            Set<AdvancedContraptionControllerBlockEntity> registered = CONTROLLERS.get(server);
            recipients = registered == null ? new ArrayList<>() : new ArrayList<>(registered);
        }
        AdvancedGraphDocument.Value data = GraphRuntime.fromLibraryValue(event.data());
        for (AdvancedContraptionControllerBlockEntity controller : recipients) {
            if (!valid(controller) || controller.getLevel().getServer() != server) {
                continue;
            }
            boolean sameDimension = event.source().dimension().equals(controller.getLevel().dimension());
            if (!withinDistance(sameDimension, event.source().position(),
                    controller.namedControllerEventPosition(), event.maximumDistance())) {
                continue;
            }
            controller.receiveNamedControllerEvent(event.name(), data);
        }
    }

    // Check if the recipient is within range
    static boolean withinDistance(boolean sameDimension, Vec3 src, Vec3 recipient, int maximumDistance) {
        if (maximumDistance <= 0) {
            return true;
        }
        if (!sameDimension || src == null || recipient == null) {
            return false;
        }
        double maximumDistanceSquared = maximumDistance * (double) maximumDistance;
        return src.distanceToSqr(recipient) <= maximumDistanceSquared;
    }

    // Normalize nullable advanced graph data for the reusable library boundary
    private static AdvancedGraphDocument.Value normalize(AdvancedGraphDocument.Value data) {
        return data == null ? AdvancedGraphDocument.Value.number(0) : data;
    }

    // Create one stable endpoint id for a block entity publisher
    private static String endpointId(BlockEntity source) {
        return source.getType().builtInRegistryHolder().key().location()
                + "/" + source.getLevel().dimension().location()
                + "/" + source.getBlockPos().asLong();
    }

    // Create a shared named event source for an ACC graph
    private static NamedEventSource source(AdvancedContraptionControllerBlockEntity controller) {
        return new NamedEventSource(controller.getLevel().getServer(),
                controller.getLevel().dimension(), controller.namedControllerEventPosition(),
                endpointId(controller));
    }

    // Check if one block entity can publish a server named event
    private static boolean valid(BlockEntity source) {
        return source != null && !source.isRemoved() && source.getLevel() != null
                && !source.getLevel().isClientSide && source.getLevel().getServer() != null;
    }

    // Register physical endpoint receivers without coupling the shared event transport to an optional mod
    public interface ExternalBridge {
        void register(AdvancedContraptionControllerBlockEntity controller);

        void unregister(AdvancedContraptionControllerBlockEntity controller);
    }
}

package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computed.ComputedEventCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// Queue named graph events and wake only listeners for that event
public final class AdvancedControllerNamedEventBus {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<MinecraftServer, Set<AdvancedContraptionControllerBlockEntity>> CONTROLLERS =
            new WeakHashMap<>();

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
        CONTROLLERS.computeIfAbsent(controller.getLevel().getServer(),
                ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(controller);
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
        if (src == null || src.getLevel() == null || src.getLevel().getServer() == null
                || name == null || name.isBlank()) {
            return;
        }
        MinecraftServer server = src.getLevel().getServer();
        int configuredMaximum = Math.max(1, AllConfigs.server().logistics.linkRange.get());
        int distance = Math.max(0, Math.min(maximumDistance, configuredMaximum));
        Vec3 sourcePosition = src.namedControllerEventPosition();
        publishToControllers(src, name, data, distance, sourcePosition);
        ComputedEventCompat.publishFromGadgets(src, name, data, distance);
    }

    // Publish the external
    public static void publishExternal(BlockEntity src, String name,
                                       AdvancedGraphDocument.Value data) {
        if (src == null || src.getLevel() == null || src.getLevel().getServer() == null
                || name == null || name.isBlank()) {
            return;
        }
        Vec3 sourcePosition = SimulatedHelper.toGlobalWorldPosition(
                src, Vec3.atCenterOf(src.getBlockPos()));
        publishToControllers(src, name, data, 0, sourcePosition);
    }

    // Publish the controllers
    private static void publishToControllers(BlockEntity src, String name,
                                             AdvancedGraphDocument.Value data, int distance,
                                             Vec3 sourcePosition) {
        MinecraftServer server = src.getLevel().getServer();
        ArrayList<AdvancedContraptionControllerBlockEntity> recipients;
        synchronized (AdvancedControllerNamedEventBus.class) {
            Set<AdvancedContraptionControllerBlockEntity> registered = CONTROLLERS.get(server);
            recipients = registered == null ? new ArrayList<>() : new ArrayList<>(registered);
        }
        for (AdvancedContraptionControllerBlockEntity controller : recipients) {
            if (controller == null || controller.isRemoved()
                    || controller.getLevel() == null || controller.getLevel().getServer() != server) {
                continue;
            }
            boolean sameDimension = src.getLevel().dimension().equals(controller.getLevel().dimension());
            if (!withinDistance(sameDimension, sourcePosition,
                    controller.namedControllerEventPosition(), distance)) {
                continue;
            }
            controller.receiveNamedControllerEvent(name,
                    data == null ? AdvancedGraphDocument.Value.number(0) : data);
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
}

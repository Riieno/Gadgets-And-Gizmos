package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// Find the controller behind a connected ACC display without rescanning the surface for every panel
final class AccDisplayControllerRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<MinecraftServer, Set<AdvancedContraptionControllerBlockEntity>> CONTROLLERS =
            new WeakHashMap<>();
    private static final Set<MinecraftServer> STOPPING_SERVERS =
            Collections.newSetFromMap(new WeakHashMap<>());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display controller
    private AccDisplayControllerRegistry() {
    }

    // Register the ACC display controller
    static synchronized void register(AdvancedContraptionControllerBlockEntity controller) {
        Level level = controller == null ? null : controller.getLevel();
        MinecraftServer server = level == null ? null : level.getServer();
        if (server == null || level.isClientSide) {
            return;
        }
        CONTROLLERS.computeIfAbsent(server,
                ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(controller);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Begin the shutdown
    static void beginShutdown(MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<AdvancedContraptionControllerBlockEntity> controllers;
        synchronized (AccDisplayControllerRegistry.class) {
            STOPPING_SERVERS.add(server);
            Set<AdvancedContraptionControllerBlockEntity> registered = CONTROLLERS.get(server);
            controllers = registered == null ? List.of() : new ArrayList<>(registered);
        }
        for (AdvancedContraptionControllerBlockEntity controller : controllers) {
            if (controller != null && !controller.isRemoved()) {
                controller.prepareForServerShutdown();
            }
        }
    }

    // Check if this is stopping
    static synchronized boolean isStopping(Level level) {
        MinecraftServer server = level == null ? null : level.getServer();
        return server != null && STOPPING_SERVERS.contains(server);
    }

    // Mark the display targets dirty
    static void markDisplayTargetsDirty(Level level) {
        MinecraftServer server = level == null ? null : level.getServer();
        if (server == null || level.isClientSide) {
            return;
        }
        List<AdvancedContraptionControllerBlockEntity> controllers;
        synchronized (AccDisplayControllerRegistry.class) {
            Set<AdvancedContraptionControllerBlockEntity> registered = CONTROLLERS.get(server);
            controllers = registered == null ? List.of() : new ArrayList<>(registered);
        }
        for (AdvancedContraptionControllerBlockEntity controller : controllers) {
            if (controller != null && !controller.isRemoved()) {
                controller.requestAccDisplayTargetRefresh();
            }
        }
    }

    // Mark the display frames dirty
    static void markDisplayFramesDirty(Level level) {
        MinecraftServer server = level == null ? null : level.getServer();
        if (server == null || level.isClientSide) {
            return;
        }
        List<AdvancedContraptionControllerBlockEntity> controllers;
        synchronized (AccDisplayControllerRegistry.class) {
            Set<AdvancedContraptionControllerBlockEntity> registered = CONTROLLERS.get(server);
            controllers = registered == null ? List.of() : new ArrayList<>(registered);
        }
        for (AdvancedContraptionControllerBlockEntity controller : controllers) {
            if (controller != null && !controller.isRemoved()) {
                controller.requestAccDisplayFrameRefresh();
            }
        }
    }

    // Finish the shutdown
    static synchronized void finishShutdown(MinecraftServer server) {
        if (server == null) {
            return;
        }
        CONTROLLERS.remove(server);
        STOPPING_SERVERS.remove(server);
    }

    // Remove the ACC display controller
    static synchronized void unregister(AdvancedContraptionControllerBlockEntity controller) {
        Level level = controller == null ? null : controller.getLevel();
        MinecraftServer server = level == null ? null : level.getServer();
        if (server == null) {
            return;
        }
        Set<AdvancedContraptionControllerBlockEntity> controllers = CONTROLLERS.get(server);
        if (controllers == null) {
            return;
        }
        controllers.remove(controller);
        controllers.removeIf(candidate -> candidate == null || candidate.isRemoved()
                || candidate.getLevel() == null);
        if (controllers.isEmpty()) {
            CONTROLLERS.remove(server);
        }
    }

}

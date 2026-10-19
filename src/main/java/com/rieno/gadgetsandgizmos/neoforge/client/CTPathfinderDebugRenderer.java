package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.AutopilotDebugNameplateRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.SablePathfinderDebugRenderer;
import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import com.rieno.gadgetsandgizmos.lib.scm.AutopilotDebugSnapshot;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.List;

// Draw operator-published Sable pathfinder routes.
public final class CTPathfinderDebugRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Defaults
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether the operator route overlay is enabled.
    private static boolean routesEnabled;
    // Tracks whether the operator SCM brain overlay is enabled.
    private static boolean brainsEnabled;
    // Last server-published route snapshots.
    private static List<SablePathfinder.DebugRoute> routes = List.of();
    // Last server-published per-vehicle SCM brain snapshots.
    private static List<AutopilotDebugSnapshot> brains = List.of();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the pathfinder debug renderer.
    private CTPathfinderDebugRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply one server-authoritative debug snapshot.
    public static void setRoutes(boolean visible, List<SablePathfinder.DebugRoute> updatedRoutes) {
        routesEnabled = visible;
        routes = visible && updatedRoutes != null ? List.copyOf(updatedRoutes) : List.of();
    }

    // Apply one complete server-authoritative pathfinder and brain snapshot.
    public static void setSnapshot(
            boolean routesVisible,
            boolean brainsVisible,
            List<SablePathfinder.DebugRoute> updatedRoutes,
            List<AutopilotDebugSnapshot> updatedBrains
    ) {
        routesEnabled = routesVisible;
        brainsEnabled = brainsVisible;
        routes = routesVisible && updatedRoutes != null ? List.copyOf(updatedRoutes) : List.of();
        brains = brainsVisible && updatedBrains != null ? List.copyOf(updatedBrains) : List.of();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the world render event.
    public static void onRenderWorld(RenderLevelStageEvent evt) {
        if ((!routesEnabled || routes.isEmpty()) && (!brainsEnabled || brains.isEmpty())
                || evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Camera camera = evt.getCamera();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        if (routesEnabled && !routes.isEmpty()) {
            SablePathfinderDebugRenderer.render(
                    evt.getPoseStack(), camera.getPosition(), bufferSource, routes);
        }
        if (brainsEnabled && !brains.isEmpty()) {
            AutopilotDebugNameplateRenderer.render(
                    evt.getPoseStack(), camera.getPosition(),
                    new Quaternionf((Quaternionfc) minecraft.getEntityRenderDispatcher()
                            .cameraOrientation()),
                    bufferSource, minecraft.font, brains);
        }
    }
}

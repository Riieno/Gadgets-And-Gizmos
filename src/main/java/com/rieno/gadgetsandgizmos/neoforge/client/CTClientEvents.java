package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSnapshotPayload;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

// Handle common client events
public final class CTClientEvents {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT client events
    private CTClientEvents() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the client tick event
    public static void onClientTick(ClientTickEvent.Pre evt) {
        AnalogueContraptionControllerClientHandler.tick();
        AnalogueJoystickClientHandler.tick();
        HardwareControllerClient.tick();
        DoubleButtonClientHandler.tick();
        DoubleButtonSlotRenderer.tick();
        CTPhysicsGogglesClient.tick();
        EntityLauncherClientInputHandler.tick();
        ArmorStandPoseClientState.tick();
        PoweredZiplinePlacementHandler.clientTick();
        PoweredZiplineRidingHandler.clientTick();
        ShippingManifestClientHandler.tick();
        ThrusterSoundManager.tick();
    }

    // Handle the screen render event
    public static void onScreenRender(ScreenEvent.Render.Post evt) {
        ClawDepthDebugHud.onRenderGuiLayer(evt);
        ArmorStandPoseClientState.renderOverlayButton(evt);
    }

    // Handle the screen mouse pressed event
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre evt) {
        ArmorStandPoseClientState.handleOverlayClick(evt);
    }

    // Handle the client logout event
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        AdvancedControllerGraphSnapshotPayload.clearClientState();
        ContraptionNetworkLinkerSnapshotPayload.clearClientState();
        AdvancedHudImageClient.clearServerImages();
        AccDisplayGuiProjection.clear();
        DiagnosticTabletGuiProjection.clear();
        DiagnosticTabletClientAppData.clear();
        DoubleButtonClientHandler.reset();
        ArmorStandPoseClientState.reset();
        CTFeatureToggles.clearServerOverrides();
    }
}

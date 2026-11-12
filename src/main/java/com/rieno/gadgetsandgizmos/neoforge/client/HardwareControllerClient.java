package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.control.HardwareControllers;

// Route independent device snapshots to controller sessions on the client thread
public final class HardwareControllerClient{
    private HardwareControllerClient(){}

    // Publish automatic bindings for existing controls and scoped bindings for selected devices
    public static void tick(){
        if(!AnalogueContraptionControllerClientHandler.acceptsHardwareControllerInput()
                && !AnalogueJoystickClientHandler.acceptsHardwareControllerInput()) return;
        var snapshot = HardwareControllers.snapshot();
        AnalogueContraptionControllerClientHandler.onHardwareControllerInput(snapshot.values());
        AnalogueJoystickClientHandler.onHardwareControllerInput(snapshot.automatic());
    }
}

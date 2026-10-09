package com.rieno.gadgetsandgizmos.registry;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.CameraBlockEntity;
import com.rieno.gadgetsandgizmos.lib.display.AccDisplaySourceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// Expose addon cameras through the existing ACC display source API
@EventBusSubscriber(modid = CreateThrusters.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CTCameraSources{
    // Prevent construction of the source registration holder
    private CTCameraSources(){}
    // Register the camera frame provider after block registration completes
    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent evt){
        evt.enqueueWork(() -> AccDisplaySourceRegistry.register(
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "camera"),
                CTBlocks.CAMERA.get(), (src, width, height) -> ((CameraBlockEntity) src).displayFrame()));
    }
}

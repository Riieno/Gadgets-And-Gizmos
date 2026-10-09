package com.rieno.gadgetsandgizmos.registry;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.create.encasing.CreateCasingApi;
import com.simibubi.create.AllBlocks;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// Register Blackstone encasing through the shared library API
public final class CTCasings{
    public static final ResourceLocation BLACKSTONE = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "blackstone_casing");

    private CTCasings(){
    }

    public static void onCommonSetup(FMLCommonSetupEvent evt){
        evt.enqueueWork(() -> {
            CreateCasingApi.registerVariant(AllBlocks.SHAFT.get(), CTBlocks.BLACKSTONE_ENCASED_SHAFT.get());
            CreateCasingApi.registerVariant(AllBlocks.COGWHEEL.get(), CTBlocks.BLACKSTONE_ENCASED_COGWHEEL.get());
            CreateCasingApi.registerVariant(AllBlocks.LARGE_COGWHEEL.get(), CTBlocks.BLACKSTONE_ENCASED_LARGE_COGWHEEL.get());
            CreateCasingApi.registerBeltCasing(BLACKSTONE, CTBlocks.BLACKSTONE_CASING.get());
            CreateCasingApi.registerAlias(CTItems.BLACKSTONE_CASING.get(), AllBlocks.ANDESITE_CASING.asItem());
        });
    }
}

package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

// Register connected textures for Blackstone Casing
public final class BlackstoneCasingConnectedTextures {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final CTSpriteShiftEntry BLACKSTONE_CASING = CTSpriteShifter.getCT(
            AllCTTypes.OMNIDIRECTIONAL,
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/blackstone_casing"),
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/blackstone_casing_connected"));
    private static final ConnectedTextureBehaviour BEHAVIOUR = new EncasedCTBehaviour(BLACKSTONE_CASING);
    private static boolean registered;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Blackstone Casing connected textures
    private BlackstoneCasingConnectedTextures() {
    }

    // Register the Blackstone Casing connectivity
    public static void register() {
        if (registered || CTBlocks.BLACKSTONE_CASING == null) return;
        CreateClient.CASING_CONNECTIVITY.makeCasing(CTBlocks.BLACKSTONE_CASING.get(), BLACKSTONE_CASING);
        registered = true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Wrap the Blackstone Casing model with its connected texture behaviour
    public static BakedModel wrap(BakedModel model) {
        return model instanceof CTModel ? model : new CTModel(model, BEHAVIOUR);
    }
}

package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

// Load Player Mannequin skins
final class PlayerMannequinSkinResolver {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the player mannequin skin resolver
    private PlayerMannequinSkinResolver() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the skin texture
    static ResourceLocation texture(PlayerMannequinEntity mannequin) {
        if (mannequin == null || mannequin.usesSteveSkin()) return DefaultPlayerSkin.getDefaultTexture();
        String remoteSkin = mannequin.remoteSkinUrl();
        if (!remoteSkin.isBlank()) {
            ResourceLocation texture = AdvancedHudImageClient.resolveTexture(remoteSkin);
            return texture == null ? DefaultPlayerSkin.getDefaultTexture() : texture;
        }
        return mannequin.getVariant().skinTexture();
    }
}

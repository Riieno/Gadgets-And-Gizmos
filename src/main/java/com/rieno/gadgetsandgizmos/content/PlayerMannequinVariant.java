package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Store Player Mannequin Variant
public record PlayerMannequinVariant(String id, Component displayName, String thanksTranslationKey, String reasonTranslationKey,
                                     ResourceLocation skinTexture) {
}

package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ShippingScheduleItem;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Replace the Shipping Schedule GUI texture
@Mixin(value = AllGuiTextures.class, remap = false)
public abstract class ShippingScheduleGuiTextureMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the local shipping texture
    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At(value = "FIELD",
                    target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;location:Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation useLocalShippingTexture(AllGuiTextures texture) {
        return localTexture(texture.location);
    }

    // Handle the local shipping texture
    @Inject(method = "getLocation", at = @At("RETURN"), cancellable = true)
    private void useLocalShippingTexture(CallbackInfoReturnable<ResourceLocation> callback) {
        callback.setReturnValue(localTexture(callback.getReturnValue()));
    }

    // Get the local texture
    private static ResourceLocation localTexture(ResourceLocation original) {
        if (!(Minecraft.getInstance().screen instanceof ScheduleScreen screen)
                || !(screen.getMenu().contentHolder.getItem() instanceof ShippingScheduleItem)) {
            return original;
        }
        if (original.getPath().endsWith("/schedule.png")) {
            return ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
                    "textures/gui/shipping_schedule.png");
        }
        if (original.getPath().endsWith("/schedule_2.png")) {
            return ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
                    "textures/gui/shipping_schedule_2.png");
        }
        return original;
    }
}

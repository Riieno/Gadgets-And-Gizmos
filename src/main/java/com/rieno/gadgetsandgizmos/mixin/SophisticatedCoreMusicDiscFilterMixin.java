package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

// Hide streamed addon discs from Sophisticated Core jukeboxes
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.VanillaDiscHandler", remap = false)
public abstract class SophisticatedCoreMusicDiscFilterMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Set<ResourceLocation> CT_BLOCKED_DISCS = Set.of(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "music_disc_kinetic_currency"),
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "music_disc_twisted_alive"),
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "music_disc_unplug_the_earth")
    );

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Filter the entity jukebox discs
    @Inject(method = "getMusicDiscs", at = @At("RETURN"), cancellable = true)
    private void createthrusters$filterEntityJukeboxDiscs(CallbackInfoReturnable<List<Item>> cir) {
        List<Item> discs = cir.getReturnValue();
        if (discs == null || discs.isEmpty()) {
            return;
        }

        List<Item> filtered = discs.stream()
                .filter(disc -> !CT_BLOCKED_DISCS.contains(BuiltInRegistries.ITEM.getKey(disc)))
                .toList();
        if (filtered.size() != discs.size()) {
            cir.setReturnValue(filtered);
        }
    }
}

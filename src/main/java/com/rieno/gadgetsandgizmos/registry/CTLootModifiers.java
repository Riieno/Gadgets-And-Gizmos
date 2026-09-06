package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.serialization.MapCodec;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.LomensMusicDiscLootModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Register addon loot modifiers
public final class CTLootModifiers {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> REGISTRAR =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, CreateThrusters.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<LomensMusicDiscLootModifier>> LOMENS_MUSIC_DISCS =
            REGISTRAR.register("lomens_music_discs", () -> LomensMusicDiscLootModifier.CODEC);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT loot modifiers
    private CTLootModifiers() {
    }
}

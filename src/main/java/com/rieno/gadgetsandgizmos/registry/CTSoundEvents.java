package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Register addon sound events
public final class CTSoundEvents {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister<SoundEvent> REGISTRAR =
            DeferredRegister.create(Registries.SOUND_EVENT, CreateThrusters.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> THRUSTER_IDLE =
            register("block.thruster.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRUSTER_HEAT =
            register("block.thruster.heat");
    public static final DeferredHolder<SoundEvent, SoundEvent> RATCHET_GEAR_CLICK =
            register("block.ratchet_gear.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DISC_KINETIC_CURRENCY =
            register("music_disc.kinetic_currency");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DISC_TWISTED_ALIVE =
            register("music_disc.twisted_alive");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DISC_UNPLUG_THE_EARTH =
            register("music_disc.unplug_the_earth");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT sound events
    private CTSoundEvents() {
    }

    // Register the CT sound events
    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, path);
        return REGISTRAR.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }
}

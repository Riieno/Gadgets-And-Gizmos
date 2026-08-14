package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.JukeboxSong;

// Register addon jukebox songs
public final class CTJukeboxSongs {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ResourceKey<JukeboxSong> KINETIC_CURRENCY = register("kinetic_currency");
    public static final ResourceKey<JukeboxSong> TWISTED_ALIVE = register("twisted_alive");
    public static final ResourceKey<JukeboxSong> UNPLUG_THE_EARTH = register("unplug_the_earth");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT jukebox songs
    private CTJukeboxSongs() {
    }

    // Register the CT jukebox songs
    private static ResourceKey<JukeboxSong> register(String path) {
        return ResourceKey.create(Registries.JUKEBOX_SONG,
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, path));
    }
}

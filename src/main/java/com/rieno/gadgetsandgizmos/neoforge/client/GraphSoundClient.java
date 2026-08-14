package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.network.GraphSoundPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.Map;

// Play and stop graph-requested sounds by their runtime handle
public final class GraphSoundClient {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<String, GraphSoundInstance> ACTIVE = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph sound client
    private GraphSoundClient() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the graph sound client
    public static void handle(GraphSoundPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        GraphSoundInstance current = ACTIVE.get(payload.playbackId());
        if (payload.stop()) {
            if (current != null) {
                minecraft.getSoundManager().stop(current);
                ACTIVE.remove(payload.playbackId());
            }
            return;
        }
        if (current != null && minecraft.getSoundManager().isActive(current)) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(payload.soundId());
        SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (sound == null) {
            ACTIVE.remove(payload.playbackId());
            return;
        }
        GraphSoundInstance instance = new GraphSoundInstance(sound, payload);
        ACTIVE.put(payload.playbackId(), instance);
        minecraft.getSoundManager().play(instance);
    }

    // Handle the graph sound instance
    private static final class GraphSoundInstance extends AbstractTickableSoundInstance {
        // Initialize the graph sound instance
        private GraphSoundInstance(SoundEvent sound, GraphSoundPayload payload) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            x = payload.x();
            y = payload.y();
            z = payload.z();
            volume = payload.volume();
            pitch = payload.pitch();
            looping = payload.loop();
            delay = 0;
        }

        // Update the graph sound instance
        @Override
        public void tick() {
            if (Minecraft.getInstance().level == null) {
                stop();
            }
        }
    }
}

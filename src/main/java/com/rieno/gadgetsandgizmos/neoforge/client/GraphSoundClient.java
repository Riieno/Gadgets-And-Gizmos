package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.network.GraphSoundPayload;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.client.sound.FollowingSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;

// Play and stop graph-requested sounds by their runtime handle
@EventBusSubscriber(modid = CreateThrusters.MOD_ID, value = Dist.CLIENT)
public final class GraphSoundClient {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<String, Playback> ACTIVE = new LinkedHashMap<>();

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
        Playback current = ACTIVE.get(payload.playbackId());
        if (payload.stop()) {
            if (current != null) {
                minecraft.getSoundManager().stop(current.sound());
                ACTIVE.remove(payload.playbackId());
            }
            return;
        }
        ClientLevel level = minecraft.level;
        if(level == null) return;
        if (current != null && current.level() == level && minecraft.getSoundManager().isActive(current.sound())) {
            return;
        }
        if(current != null) minecraft.getSoundManager().stop(current.sound());
        ResourceLocation id = ResourceLocation.tryParse(payload.soundId());
        SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (sound == null) {
            ACTIVE.remove(payload.playbackId());
            return;
        }
        Vec3 pos = new Vec3(payload.x(), payload.y(), payload.z());
        FollowingSoundInstance instance = payload.subLevelId() == null
                ? new FollowingSoundInstance(sound, SoundSource.BLOCKS, payload.volume(), payload.pitch(),
                        payload.loop(), pos, () -> minecraft.level == level ? pos : null)
                : FollowingSoundInstance.onSubLevel(sound, SoundSource.BLOCKS, payload.volume(), payload.pitch(),
                        payload.loop(), level, payload.subLevelId(),
                        new Vec3(payload.localX(), payload.localY(), payload.localZ()), pos);
        ACTIVE.put(payload.playbackId(), new Playback(level, instance));
        minecraft.getSoundManager().play(instance);
    }

    // Release finished sounds and sources from an old client level
    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        Minecraft minecraft = Minecraft.getInstance();
        var iter = ACTIVE.values().iterator();
        while(iter.hasNext()){
            Playback playback = iter.next();
            if(playback.level() != minecraft.level || !minecraft.getSoundManager().isActive(playback.sound())){
                minecraft.getSoundManager().stop(playback.sound());
                iter.remove();
            }
        }
    }

    // Drop playback handles when leaving a server or world
    @SubscribeEvent public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut evt){
        Minecraft minecraft = Minecraft.getInstance();
        for(Playback playback : ACTIVE.values()) minecraft.getSoundManager().stop(playback.sound());
        ACTIVE.clear();
    }

    private record Playback(ClientLevel level, FollowingSoundInstance sound){}
}

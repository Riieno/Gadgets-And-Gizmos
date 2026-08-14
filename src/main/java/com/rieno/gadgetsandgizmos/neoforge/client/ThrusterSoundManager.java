package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

// Start, update and remove looping thruster sounds by block position
public final class ThrusterSoundManager {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ThrusterBlockEntity, ThrusterLoopSoundInstance> ACTIVE =
            new IdentityHashMap<>();
    private static final Map<ThrusterBlockEntity, Long> STARTUP_SOUND_UNTIL =
            new IdentityHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster sound manager
    private ThrusterSoundManager() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update a thruster sound instance
    public static void touch(ThrusterBlockEntity blockEntity) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        float maxVolume = Mth.clamp(CTConfigs.CLIENT.thrusterMaxVolume.get().floatValue(), 0.0f, 2.0f);
        if (level == null || minecraft.isPaused()
                || !blockEntity.isClientEffectSourceValid()
                || blockEntity.getLevel() != level
                || !blockEntity.isActive() || blockEntity.isSoundFiltered()
                || maxVolume <= 0.0f) {
            stop(blockEntity);
            return;
        }

        SoundManager soundManager = minecraft.getSoundManager();
        long gameTime = level.getGameTime();
        ThrusterLoopSoundInstance sound = ACTIVE.get(blockEntity);
        boolean focusedMode = blockEntity.isFocusedMode();
        if (sound != null && sound.isFocusedMode() != focusedMode) {

            sound.requestStop();
            ACTIVE.remove(blockEntity);
            sound = null;
        }

        if (sound != null) {
            return;
        }
        Long startupUntil = STARTUP_SOUND_UNTIL.get(blockEntity);
        if (startupUntil != null && gameTime < startupUntil) {
            return;
        }

        float throttle = Mth.clamp(blockEntity.getAppliedThrottle(), 0.0f, 1.0f);
        float heatVolume = (0.18f + throttle * 0.65f) * maxVolume;
        SoundEvent startupEvent = focusedMode ? SoundEvents.BEACON_ACTIVATE : CTSoundEvents.THRUSTER_HEAT.get();
        var origin = blockEntity.getWorldExhaustOrigin();
        level.playLocalSound(origin.x, origin.y, origin.z,
            startupEvent, SoundSource.BLOCKS,
                heatVolume, 0.92f, false);

        STARTUP_SOUND_UNTIL.put(blockEntity, gameTime + (focusedMode ? 90L : 35L));

        ThrusterLoopSoundInstance created = new ThrusterLoopSoundInstance(
                blockEntity, focusedMode, gameTime);
        ACTIVE.put(blockEntity, created);
        soundManager.queueTickingSound(created);
    }

    // Stop the thruster sound manager
    public static void stop(ThrusterBlockEntity blockEntity) {
        ThrusterLoopSoundInstance sound = ACTIVE.remove(blockEntity);
        if (sound != null) {
            sound.requestStop();
        }
        STARTUP_SOUND_UNTIL.remove(blockEntity);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the thruster sound manager
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.values().forEach(ThrusterLoopSoundInstance::requestStop);
            ACTIVE.clear();
            STARTUP_SOUND_UNTIL.clear();
            return;
        }

        SoundManager soundManager = minecraft.getSoundManager();
        long gameTime = minecraft.level.getGameTime();
        Iterator<Map.Entry<ThrusterBlockEntity, ThrusterLoopSoundInstance>> iterator =
                ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ThrusterBlockEntity, ThrusterLoopSoundInstance> entry = iterator.next();
            ThrusterBlockEntity thruster = entry.getKey();
            ThrusterLoopSoundInstance sound = entry.getValue();
            if (!thruster.isClientEffectSourceValid()) {
                sound.requestStop();
                iterator.remove();
                STARTUP_SOUND_UNTIL.remove(thruster);
            } else if (!soundManager.isActive(sound)
                    && !sound.isInStartupGrace(gameTime)) {
                iterator.remove();
                STARTUP_SOUND_UNTIL.remove(thruster);
            }
        }
        STARTUP_SOUND_UNTIL.entrySet().removeIf(entry -> entry.getValue() < gameTime - 20L);
    }
}

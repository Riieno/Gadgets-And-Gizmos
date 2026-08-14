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
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

// Keep one looping thruster sound positioned and faded with its live block
public class ThrusterLoopSoundInstance extends AbstractTickableSoundInstance {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Thruster
    private final ThrusterBlockEntity thruster;
    // Tracks whether mode is focused
    private final boolean focusedMode;
    // Created at tick
    private final long createdAtTick;
    // Current smoothed volume
    private float smoothedVolume;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float FIXED_PITCH = 0.92f;
    private static final long STARTUP_GRACE_TICKS = 35L;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster loop sound instance
    public ThrusterLoopSoundInstance(
            ThrusterBlockEntity thruster,
            boolean focusedMode,
            long createdAtTick
    ) {
        super(focusedMode ? SoundEvents.BEACON_AMBIENT : CTSoundEvents.THRUSTER_IDLE.get(), SoundSource.BLOCKS, RandomSource.create());
        this.thruster = thruster;
        this.focusedMode = focusedMode;
        this.createdAtTick = createdAtTick;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.pitch = FIXED_PITCH;
        this.smoothedVolume = 0.0f;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the thruster loop sound instance
    @Override
    public void tick() {
        boolean valid = Minecraft.getInstance().level != null
                && thruster.getLevel() == Minecraft.getInstance().level
                && thruster.isClientEffectSourceValid();
        float maxVolume = Mth.clamp(CTConfigs.CLIENT.thrusterMaxVolume.get().floatValue(), 0.0f, 2.0f);

        float targetVolume = 0.0f;
        if (!valid || thruster.isSoundFiltered()) {
            stop();
            return;
        }
        if (thruster.isActive() && maxVolume > 0.0f) {
            if (thruster.isFocusedMode() != focusedMode) {
                stop();
                return;
            }
            float throttle = Mth.clamp(thruster.getAppliedThrottle(), 0.0f, 1.0f);
            Vec3 origin = thruster.getWorldExhaustOrigin();
            this.x = origin.x;
            this.y = origin.y;
            this.z = origin.z;
            targetVolume = focusedMode
                    ? (0.03f + 0.45f * throttle) * maxVolume
                    : (0.06f + 1.45f * throttle * throttle * throttle) * maxVolume;
        }

        smoothedVolume += (targetVolume - smoothedVolume) * 0.18f;
        this.volume = Math.max(0.001f, smoothedVolume);
        this.pitch = FIXED_PITCH;

        if (!thruster.isActive() && smoothedVolume < 0.01f) {
            stop();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this can start silently
    @Override
    public boolean canStartSilent() {
        return true;
    }

    // Check if this can play sound
    @Override
    public boolean canPlaySound() {
        return Minecraft.getInstance().level != null
                && thruster.getLevel() == Minecraft.getInstance().level
                && thruster.isClientEffectSourceValid();
    }

    // Check if this is a focused mode
    public boolean isFocusedMode() {
        return focusedMode;
    }

    // Request the stop
    public void requestStop() {
        stop();
    }

    // Check if this is in the startup grace
    public boolean isInStartupGrace(long currentTick) {
        return currentTick - createdAtTick < STARTUP_GRACE_TICKS;
    }
}

package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Locale;

// Add direct control to Speed Controller
@Mixin(targets = "com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity")
public abstract class SpeedControllerDirectControlMixin extends SmartBlockEntity implements IDirectControlReceiver {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Target speed
    @Shadow
    public ScrollValueBehaviour targetSpeed;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the speed controller direct control
    protected SpeedControllerDirectControlMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        if (level == null || level.isClientSide || targetSpeed == null) {
            return;
        }

        String channel = channelId == null ? "" : channelId.toLowerCase(Locale.ROOT);
        float clamped = Mth.clamp(val, 0.0f, 1.0f);
        int maxSpeed = Math.max(1, AllConfigs.server().kinetics.maxRotationSpeed.get());

        if (channel.contains("stop") || channel.contains("zero") || channel.contains("off")) {
            targetSpeed.setValue(0);
        } else {
            int magnitude = Mth.clamp((int) Math.round(clamped * maxSpeed), 0, maxSpeed);
            if (channel.contains("reverse") || channel.contains("negative") || channel.contains("back")) {
                magnitude = -magnitude;
            }
            targetSpeed.setValue(magnitude);
        }

        setChanged();
        sendData();
    }
}
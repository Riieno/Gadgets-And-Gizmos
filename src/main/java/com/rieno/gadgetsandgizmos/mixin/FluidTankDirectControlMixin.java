package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Locale;

// Route Fluid Tank Direct Control through the addon's direct-control API
@Mixin(targets = "com.simibubi.create.content.fluids.tank.FluidTankBlockEntity")
public abstract class FluidTankDirectControlMixin extends SmartBlockEntity implements IDirectControlReceiver {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether window toggle is armed
    @Unique
    private boolean ct$windowToggleArmed;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the fluid tank direct control
    protected FluidTankDirectControlMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        if (level == null || level.isClientSide) {
            return;
        }

        String channel = channelId == null ? "" : channelId.toLowerCase(Locale.ROOT);
        float clamped = Mth.clamp(val, 0.0f, 1.0f);
        FluidTankBlockEntity tank = (FluidTankBlockEntity) (Object) this;

        if (channel.contains("toggle")) {
            boolean armed = clamped >= 0.5f;
            if (armed && !ct$windowToggleArmed) {
                tank.toggleWindows();
                setChanged();
                sendData();
            }
            ct$windowToggleArmed = armed;
            return;
        }

        boolean nextWindow = !channel.contains("plain") && !channel.contains("solid") && clamped >= 0.5f;
        if (channel.contains("close") || channel.contains("disable") || channel.contains("off")) {
            nextWindow = clamped < 0.5f;
        }

        tank.setWindows(nextWindow);
        setChanged();
        sendData();
    }
}

package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Locale;

// Route Sliding Door Direct Control through the addon's direct-control API
@Mixin(targets = "com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity")
public abstract class SlidingDoorDirectControlMixin extends SmartBlockEntity implements IDirectControlReceiver {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether toggle is armed
    @Unique
    private boolean ct$toggleArmed;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sliding door direct control
    protected SlidingDoorDirectControlMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        SlidingDoorBlockEntity door = (SlidingDoorBlockEntity) (Object) this;

        if (channel.contains("toggle")) {
            boolean armed = clamped >= 0.5f;
            if (armed && !ct$toggleArmed) {
                SlidingDoorBlock block = (SlidingDoorBlock) door.getBlockState().getBlock();
                block.setOpen(null, level, door.getBlockState(), door.getBlockPos(), !SlidingDoorBlockEntity.isOpen(door.getBlockState()));
                setChanged();
                sendData();
            }
            ct$toggleArmed = armed;
            return;
        }

        boolean nextOpen = clamped >= 0.5f;
        if (channel.contains("close") || channel.contains("shut") || channel.contains("collapse")) {
            nextOpen = clamped < 0.5f;
        } else if (channel.contains("open") || channel.contains("extend")) {
            nextOpen = clamped >= 0.5f;
        }

        SlidingDoorBlock block = (SlidingDoorBlock) door.getBlockState().getBlock();
        if (SlidingDoorBlockEntity.isOpen(door.getBlockState()) != nextOpen) {
            block.setOpen(null, level, door.getBlockState(), door.getBlockPos(), nextOpen);
            setChanged();
            sendData();
        }
    }
}

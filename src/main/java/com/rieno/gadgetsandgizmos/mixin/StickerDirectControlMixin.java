package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.content.contraptions.chassis.StickerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Locale;

// Route Sticker Direct Control through the addon's direct-control API
@Mixin(targets = "com.simibubi.create.content.contraptions.chassis.StickerBlockEntity")
public abstract class StickerDirectControlMixin extends SmartBlockEntity implements IDirectControlReceiver {
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

    // Initialize the sticker direct control
    protected StickerDirectControlMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        StickerBlockEntity sticker = (StickerBlockEntity) (Object) this;
        BlockState state = sticker.getBlockState();
        if (!(state.getBlock() instanceof StickerBlock)) {
            return;
        }

        if (channel.contains("toggle")) {
            boolean armed = clamped >= 0.5f;
            if (armed && !ct$toggleArmed) {
                level.setBlock(sticker.getBlockPos(), state.setValue(StickerBlock.EXTENDED, !state.getValue(StickerBlock.EXTENDED)), 2);
                setChanged();
                sendData();
            }
            ct$toggleArmed = armed;
            return;
        }

        boolean nextExtended = clamped >= 0.5f;
        if (channel.contains("retract") || channel.contains("close") || channel.contains("in")) {
            nextExtended = clamped < 0.5f;
        } else if (channel.contains("extend") || channel.contains("open") || channel.contains("out")) {
            nextExtended = clamped >= 0.5f;
        }

        if (state.getValue(StickerBlock.EXTENDED) != nextExtended) {
            level.setBlock(sticker.getBlockPos(), state.setValue(StickerBlock.EXTENDED, nextExtended), 2);
            setChanged();
            sendData();
        }
    }
}

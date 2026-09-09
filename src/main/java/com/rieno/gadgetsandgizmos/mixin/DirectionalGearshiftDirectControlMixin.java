package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Locale;

// Route Directional Gearshift Direct Control through the addon's direct-control API
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlockEntity")
public abstract class DirectionalGearshiftDirectControlMixin extends SplitShaftBlockEntity
    implements IDirectControlReceiver {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the directional gearshift direct control
    protected DirectionalGearshiftDirectControlMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
        boolean powered = Mth.clamp(val, 0.0f, 1.0f) > 0.5f;
        BlockState state = getBlockState();
        boolean changed = false;
        if (channel.contains("left")) {
            if (state.hasProperty(DirectionalGearshiftBlock.LEFT_POWERED) && state.getValue(DirectionalGearshiftBlock.LEFT_POWERED) != powered) {
                state = state.setValue(DirectionalGearshiftBlock.LEFT_POWERED, powered);
                changed = true;
            }
        } else if (channel.contains("right")) {
            if (state.hasProperty(DirectionalGearshiftBlock.RIGHT_POWERED) && state.getValue(DirectionalGearshiftBlock.RIGHT_POWERED) != powered) {
                state = state.setValue(DirectionalGearshiftBlock.RIGHT_POWERED, powered);
                changed = true;
            }
        } else {
            return;
        }

        if (changed) {
            // A scheduled block tick is not reliable while this block is in a moving
            // Sable sub-level. Rebuild the kinetic network synchronously instead,
            // matching Create's normal kinetic state-transition pattern.
            detachKinetics();
            level.setBlock(worldPosition, state, 2);
            if (!isRemoved()) {
                attachKinetics();
                setChanged();
                sendData();
            }
        }
    }

    // Select one gearshift input and clear its opposite in one block-state and
    // kinetic-network update. A directional gearshift cannot represent both
    // requested directions simultaneously.
    @Override
    public void applyExclusiveDirectControllerSignal(
            String activeChannelId,
            String inactiveChannelId,
            float val
    ) {
        if (level == null || level.isClientSide) {
            return;
        }
        String activeChannel = activeChannelId == null
                ? "" : activeChannelId.toLowerCase(Locale.ROOT);
        String inactiveChannel = inactiveChannelId == null
                ? "" : inactiveChannelId.toLowerCase(Locale.ROOT);
        boolean powered = Mth.clamp(val, 0.0F, 1.0F) > 0.5F;
        BlockState state = getBlockState();
        BlockState updated = setDirectionalInput(state, inactiveChannel, false);
        updated = setDirectionalInput(updated, activeChannel, powered);
        if (updated.equals(state)) {
            return;
        }
        detachKinetics();
        level.setBlock(worldPosition, updated, 2);
        if (!isRemoved()) {
            attachKinetics();
            setChanged();
            sendData();
        }
    }

    // Set one named directional input without publishing an intermediate state.
    private static BlockState setDirectionalInput(
            BlockState state,
            String channel,
            boolean powered
    ) {
        if (channel.contains("left")
                && state.hasProperty(DirectionalGearshiftBlock.LEFT_POWERED)) {
            return state.setValue(DirectionalGearshiftBlock.LEFT_POWERED, powered);
        }
        if (channel.contains("right")
                && state.hasProperty(DirectionalGearshiftBlock.RIGHT_POWERED)) {
            return state.setValue(DirectionalGearshiftBlock.RIGHT_POWERED, powered);
        }
        return state;
    }
}

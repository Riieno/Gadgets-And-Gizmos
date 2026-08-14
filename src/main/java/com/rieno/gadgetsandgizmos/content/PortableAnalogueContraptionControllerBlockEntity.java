package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

// Run analogue controller logic inside an item-backed portable container
public class PortableAnalogueContraptionControllerBlockEntity extends AnalogueContraptionControllerBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current changed callback
    private Runnable changedCallback = () -> {
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the portable analogue contraption controller
    public PortableAnalogueContraptionControllerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the changed callback
    public void setChangedCallback(Runnable changedCallback) {
        this.changedCallback = changedCallback == null ? () -> {
        } : changedCallback;
    }

    // Set the changed
    @Override
    public void setChanged() {
        changedCallback.run();
    }

    // Send the data
    @Override
    public void sendData() {
        changedCallback.run();
    }

    // Check if the controller runtime is loaded
    @Override
    protected boolean isControllerRuntimeLoaded() {
        return getLevel() != null && !getLevel().isClientSide && !isRemoved();
    }

    // Check if this should notify output neighbors
    @Override
    protected boolean shouldNotifyOutputNeighbors() {
        return false;
    }

    // Check if the player can use this
    @Override
    public boolean canPlayerUse(Player player) {
        return true;
    }
}

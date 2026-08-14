package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

// Store and synchronize the state shown by a placed Diagnostic Tablet
public class DiagnosticTabletBlockEntity extends SmartBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current diagnostic tablet state
    private DiagnosticTabletData.State state = DiagnosticTabletData.State.DEFAULT;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet
    public DiagnosticTabletBlockEntity(BlockPos pos, BlockState blockState) {
        super(CTBlockEntities.DIAGNOSTIC_TABLET.get(), pos, blockState);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // Get the state
    public DiagnosticTabletData.State state() {
        return state;
    }

    // Set the state
    public void setState(DiagnosticTabletData.State state) {
        this.state = state == null ? DiagnosticTabletData.State.DEFAULT : state;
        setChanged();
        sendData();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the diagnostic tablet
    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide
                && Math.floorMod(level.getGameTime() + worldPosition.asLong(), 20L) == 0L) {
            DiagnosticTabletRedstoneLinkRuntime.refreshPlaced(this);
        }
    }

    // Create the tablet update tag
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.put("Tablet", DiagnosticTabletData.toTag(state));
        return tag;
    }

    // Create the tablet update packet
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Write the diagnostic tablet safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.put("Tablet", DiagnosticTabletData.toTag(state));
    }

    // Write the diagnostic tablet
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.put("Tablet", DiagnosticTabletData.toTag(state));
    }

    // Read the diagnostic tablet
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        state = DiagnosticTabletData.fromTag(tag.getCompound("Tablet"));
    }
}

package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Keep the SCM placer's identity when the module moves into a sublevel
public final class ShipControlModuleBlockEntity extends BlockEntity {
    private @Nullable UUID placerId;

    public ShipControlModuleBlockEntity(BlockPos pos, BlockState state){
        super(CTBlockEntities.SHIP_CONTROL_MODULE.get(), pos, state);
    }

    public @Nullable UUID placerId(){ return placerId; }

    public void setPlacerId(@Nullable UUID id){
        placerId = id;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider){
        super.saveAdditional(tag, provider);
        if(placerId != null) tag.putUUID("Placer", placerId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider){
        super.loadAdditional(tag, provider);
        placerId = tag.hasUUID("Placer") ? tag.getUUID("Placer") : null;
    }
}

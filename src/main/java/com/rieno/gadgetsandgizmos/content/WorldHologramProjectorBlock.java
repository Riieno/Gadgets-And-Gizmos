package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Project the root-world surface above a low-profile table
public final class WorldHologramProjectorBlock extends Block implements IBE<WorldHologramProjectorBlockEntity>{
    private static final VoxelShape SHAPE = Shapes.or(box(0, 0, 0, 16, 2, 16), box(6, 2, 6, 10, 4, 10));

    public WorldHologramProjectorBlock(Properties props){ super(props); }
    @Override public Class<WorldHologramProjectorBlockEntity> getBlockEntityClass(){ return WorldHologramProjectorBlockEntity.class; }
    @Override public BlockEntityType<? extends WorldHologramProjectorBlockEntity> getBlockEntityType(){ return CTBlockEntities.WORLD_HOLOGRAM_PROJECTOR.get(); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx){ return SHAPE; }

    // Keep client screen loading behind the existing sided screen bridge
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit){
        if(level.isClientSide && level.getBlockEntity(pos) instanceof WorldHologramProjectorBlockEntity projector){
            try{
                Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.CTClientScreens")
                        .getMethod("openWorldHologramProjector", WorldHologramProjectorBlockEntity.class).invoke(null, projector);
            }catch(ReflectiveOperationException err){ throw new IllegalStateException("Projector screen is unavailable", err); }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

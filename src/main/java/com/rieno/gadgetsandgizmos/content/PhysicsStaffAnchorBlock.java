package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerTracker;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Place a Physics Staff in the world beside a Backtank
public class PhysicsStaffAnchorBlock extends Block implements IBE<PhysicsStaffAnchorBlockEntity>, IWrenchable {
    private static final VoxelShape SHAPE = box(6.0D, -16.0D, 6.0D, 10.0D, 18.0D, 10.0D);

    // Initialize the placed staff block
    public PhysicsStaffAnchorBlock(Properties properties) {
        super(properties);
    }

    // Get the block shape
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    // Get the full collision shape
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    // Get the stored Physics Staff as the block drop
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof PhysicsStaffAnchorBlockEntity anchor && !anchor.getStaff().isEmpty()) {
            return List.of(anchor.getStaff());
        }
        return List.of();
    }

    // Return the staff to the player without releasing its retained locks
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        if (player == null || !(level.getBlockEntity(ctx.getClickedPos()) instanceof PhysicsStaffAnchorBlockEntity anchor)) {
            return InteractionResult.FAIL;
        }

        ItemStack staff = anchor.getStaff();
        if (staff.isEmpty() || !player.getInventory().add(staff.copy())) return InteractionResult.FAIL;
        java.util.UUID staffId = PhysicsStaffPowerHooks.getStaffId(staff);
        if (staffId != null && level.getServer() != null) {
            PhysicsStaffPowerTracker.get(level.getServer()).moveToInventoryPower(level.getServer(), staffId, player.getUUID());
        }
        anchor.takeStaffForPickup();
        level.removeBlock(ctx.getClickedPos(), false);
        IWrenchable.playRemoveSound(level, ctx.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    // Release locks when the placed staff is destroyed
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof PhysicsStaffAnchorBlockEntity anchor) {
            anchor.releaseLocks();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    // Get the block entity class
    @Override
    public Class<PhysicsStaffAnchorBlockEntity> getBlockEntityClass() {
        return PhysicsStaffAnchorBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends PhysicsStaffAnchorBlockEntity> getBlockEntityType() {
        return CTBlockEntities.PHYSICS_STAFF_ANCHOR.get();
    }

    // Create the block entity
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhysicsStaffAnchorBlockEntity(pos, state);
    }

    // Get the server ticker
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (!level.isClientSide && blockEntityType == CTBlockEntities.PHYSICS_STAFF_ANCHOR.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<PhysicsStaffAnchorBlockEntity>) PhysicsStaffAnchorBlockEntity::tickServer;
        }
        return null;
    }
}

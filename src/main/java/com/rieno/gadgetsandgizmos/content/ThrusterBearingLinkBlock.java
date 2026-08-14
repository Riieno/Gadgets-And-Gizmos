package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// Keep the movable thruster bearing plate attached across Sable level moves
public class ThrusterBearingLinkBlock extends SwivelBearingPlateBlock {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster bearing link block
    public ThrusterBearingLinkBlock(Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the state before move
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(originLevel, oldPos, blockEntity -> {
            if (blockEntity instanceof ThrusterBearingLinkBlockEntity linkBlockEntity) {
                linkBlockEntity.beforeAssembly();
            }
        });
    }

    // Handle the state after move
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(resultingLevel, newPos, blockEntity -> {
            if (blockEntity instanceof ThrusterBearingLinkBlockEntity linkBlockEntity) {
                linkBlockEntity.fixParentLinkingWhenMoved();
            }
        });
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle thruster bearing link block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.mayBuild()) {
            return ItemInteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.FAIL;
        }
        if (player.getItemInHand(hand).isEmpty()) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            withBlockEntityDo(level, pos, blockEntity -> {
                if (blockEntity instanceof ThrusterBearingLinkBlockEntity linkBlockEntity) {
                    linkBlockEntity.setParentAssembleNextTick();
                }
            });
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    // Handle wrench use
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        return InteractionResult.PASS;
    }

    // Get the clone item stack
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return CTBlocks.THRUSTER_BEARING.get().asItem().getDefaultInstance();
    }

    // Get the block entity class
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<SwivelBearingPlateBlockEntity> getBlockEntityClass() {
        return (Class) ThrusterBearingLinkBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends SwivelBearingPlateBlockEntity> getBlockEntityType() {
        return CTBlockEntities.THRUSTER_BEARING_LINK.get();
    }
}

package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.create.ConnectedMultiblockPlacement;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

// Extend custom vault-shaped multiblocks using the placed cross-section
public class SmartVaultBlockItem extends ItemVaultItem {
    // Initialize the smart vault block item
    public SmartVaultBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    // Place one block and fill the matching connected cross-section
    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        InteractionResult result = super.place(ctx);
        if (!result.consumesAction()) return result;
        tryMultiPlace(ctx);
        return result;
    }

    // Fill a complete width-by-width layer when extending a formed vault
    private void tryMultiPlace(BlockPlaceContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || player.isShiftKeyDown() || SymmetryWandItem.presentInHotbar(player)) return;
        Direction face = ctx.getClickedFace();
        BlockPos previousPos = ctx.getClickedPos().relative(face.getOpposite());
        BlockState previousState = ctx.getLevel().getBlockState(previousPos);
        if (previousState.getBlock() != getBlock()) return;
        if (!(ctx.getLevel().getBlockEntity(previousPos) instanceof ItemVaultBlockEntity member)) return;
        ItemVaultBlockEntity controller = member.getControllerBE();
        if (controller == null || controller.getWidth() <= 1) return;
        List<BlockPos> positions = ConnectedMultiblockPlacement.vaultExtension(
                controller.getBlockPos(), controller.getMainConnectionAxis(), controller.getWidth(),
                controller.getHeight(), face);
        if (!positions.contains(ctx.getClickedPos())) return;
        placeMissing(ctx, player, positions);
    }

    // Validate and place every missing block in one extension plane
    private void placeMissing(BlockPlaceContext ctx, Player player, List<BlockPos> positions) {
        int missing = 0;
        for (BlockPos pos : positions) {
            BlockState state = ctx.getLevel().getBlockState(pos);
            if (state.getBlock() == getBlock()) continue;
            if (!state.canBeReplaced()) return;
            missing++;
        }
        if (!player.isCreative() && ctx.getItemInHand().getCount() < missing) return;
        for (BlockPos pos : positions) {
            if (ctx.getLevel().getBlockState(pos).getBlock() == getBlock()) continue;
            BlockPlaceContext extra = BlockPlaceContext.at(ctx, pos, ctx.getClickedFace());
            player.getPersistentData().putBoolean("SilenceVaultSound", true);
            try {
                super.place(extra);
            } finally {
                player.getPersistentData().remove("SilenceVaultSound");
            }
        }
    }
}

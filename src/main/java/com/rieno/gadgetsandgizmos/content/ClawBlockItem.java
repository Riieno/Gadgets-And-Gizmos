package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedRopeCompat;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

// Handle Claw Block
public class ClawBlockItem extends CTTooltipBlockItem {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw block item
    public ClawBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle claw block item use on the target
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) {
            return super.useOn(ctx);
        }

        BlockPos clickedPos = ctx.getClickedPos();
        if (!(level.getBlockEntity(clickedPos) instanceof RopeWinchBlockEntity winch)) {
            return super.useOn(ctx);
        }

        RopeStrandHolderBehavior winchHolder = winch.getRopeHolder();
        if (winchHolder == null || winchHolder.isAttached()) {
            return super.useOn(ctx);
        }

        ItemStack ropeStack = findRopeStack(player, ctx.getHand());
        InteractionResult placeResult = super.useOn(ctx);
        if (!placeResult.consumesAction()) {
            return placeResult;
        }

        if (level.isClientSide) {
            return placeResult;
        }

        if (ropeStack.isEmpty() && !player.isCreative()) {
            return placeResult;
        }

        Level placementLevel = resolvePlacementLevel(level);
        BlockPos expectedPos = projectPlacementPos(level, clickedPos, ctx.getClickedFace());

        BlockPos localPlacedPos = clickedPos.relative(ctx.getClickedFace());
        ClawBlockEntity placedClaw = findPlacedClawAtExactPositions(level, placementLevel, expectedPos, localPlacedPos);

        if (placedClaw == null) {
            return placeResult;
        }

        RopeStrandHolderBehavior placedHolder = placedClaw.getRopeHolder();
        if (placedHolder == null
                || !SimulatedRopeCompat.createRope(winchHolder, placedHolder, !player.hasInfiniteMaterials())) {
            return placeResult;
        }

        placedClaw.ensureAssembledForRopeAttach();

        if (!player.isCreative() && !ropeStack.isEmpty()) {
            ropeStack.shrink(1);
        }

        placementLevel.playSound(null, expectedPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
        return InteractionResult.CONSUME;
    }

    // Resolve the placement level
    private static Level resolvePlacementLevel(Level level) {
        Level rootLevel = SableLevelApi.serverLevel(level);
        return rootLevel == null ? level : rootLevel;
    }

    // Get the project placement pos
    private static BlockPos projectPlacementPos(Level level, BlockPos clickedPos, Direction clickedFace) {
        Vec3 projected = projectOutOfSubLevel(level, Vec3.atCenterOf(clickedPos.relative(clickedFace)));
        return BlockPos.containing(projected);
    }

    // Find the placed claw at exact positions
    private static ClawBlockEntity findPlacedClawAtExactPositions(Level interactionLevel,
                                                                   Level placementLevel,
                                                                   BlockPos expectedPos,
                                                                   BlockPos localPlacedPos) {
        ClawBlockEntity direct = SimulatedHelper.findBlockEntityIncludingSubLevels(placementLevel, expectedPos, ClawBlockEntity.class);
        if (direct != null) {
            return direct;
        }
        direct = SimulatedHelper.findBlockEntityIncludingSubLevels(placementLevel, localPlacedPos, ClawBlockEntity.class);
        if (direct != null) {
            return direct;
        }
        if (interactionLevel != placementLevel) {
            direct = SimulatedHelper.findBlockEntityIncludingSubLevels(interactionLevel, expectedPos, ClawBlockEntity.class);
            if (direct != null) {
                return direct;
            }
            direct = SimulatedHelper.findBlockEntityIncludingSubLevels(interactionLevel, localPlacedPos, ClawBlockEntity.class);
            if (direct != null) {
                return direct;
            }
        }
        return null;
    }

    // Get the project out of sublevel
    private static Vec3 projectOutOfSubLevel(Level level, Vec3 pos) {
        return Sable.HELPER.projectOutOfSubLevel(level, pos);
    }

    // Find the rope stack
    private static ItemStack findRopeStack(Player player, InteractionHand usedHand) {

        if (usedHand != InteractionHand.MAIN_HAND) {
            ItemStack main = player.getMainHandItem();
            if (main.getItem() instanceof RopeItem) {
                return main;
            }
        }

        if (usedHand != InteractionHand.OFF_HAND) {
            ItemStack off = player.getOffhandItem();
            if (off.getItem() instanceof RopeItem) {
                return off;
            }
        }

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof RopeItem) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }
}

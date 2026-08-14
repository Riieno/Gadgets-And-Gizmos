package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlockEntity;
import dev.simulated_team.simulated.content.items.merging_glue.MergingGlueItemHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Stop glue from joining two Physics Gantry Carriages
@Mixin(MergingGlueItemHandler.class)
public class MergingGlueItemHandlerMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Component CARRIAGE_TO_CARRIAGE_MESSAGE = Component.literal(
            "You cannot attach a Physics Gantry Carriage to a Physics Gantry Carriage, Selection Cleared.");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current first pos
    @Shadow
    public BlockPos firstPos;

    // Current first direction
    @Shadow
    public Direction firstDirection;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Skip the physics gantry carriage glue
    @Inject(method = "onItemUseBlock", at = @At("HEAD"), cancellable = true)
    private void createthrusters$skipPhysicsGantryCarriageGlue(Level level, Player player, ItemStack itemStack,
                                                               InteractionHand hand,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (itemStack.isEmpty() || !itemStack.is(Items.SLIME_BALL)) {
            return;
        }

        HitResult hitResult = Minecraft.getInstance().hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit) || blockHit.getType() == HitResult.Type.MISS) {
            return;
        }

        BlockPos clickedPos = blockHit.getBlockPos();
        Direction clickedDirection = blockHit.getDirection();
        boolean clickedCarriage = isPhysicsGantryCarriageEndpoint(level, clickedPos);
        boolean selectedCarriage = firstPos != null && isPhysicsGantryCarriageEndpoint(level, firstPos);
        if (!clickedCarriage && !selectedCarriage) {
            return;
        }

        if (firstPos == null && clickedCarriage) {
            firstPos = clickedPos.immutable();
            firstDirection = clickedDirection;
            cir.setReturnValue(true);
            return;
        }

        if (selectedCarriage) {
            boolean clickedDifferentCarriage = clickedCarriage && !firstPos.equals(clickedPos);
            firstPos = null;
            firstDirection = null;
            if (clickedDifferentCarriage) {
                player.displayClientMessage(CARRIAGE_TO_CARRIAGE_MESSAGE, true);
            }
            cir.setReturnValue(clickedDifferentCarriage);
            return;
        }

        cir.setReturnValue(false);
    }

    // Check if this is a physics gantry carriage endpoint
    private static boolean isPhysicsGantryCarriageEndpoint(Level level, BlockPos pos) {
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, pos);
        if (blockEntity instanceof PhysicsGantryCarriageBlockEntity) {
            return true;
        }

        BlockState state = blockEntity == null ? level.getBlockState(pos) : blockEntity.getBlockState();
        return state.getBlock() instanceof PhysicsGantryCarriageBlock;
    }
}

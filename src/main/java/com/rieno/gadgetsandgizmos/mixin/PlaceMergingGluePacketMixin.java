package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlockEntity;
import dev.simulated_team.simulated.network.packets.PlaceMergingGluePacket;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Stop network glue placement between Physics Gantry Carriages
@Mixin(PlaceMergingGluePacket.class)
public class PlaceMergingGluePacketMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Component CARRIAGE_TO_CARRIAGE_MESSAGE = Component.literal(
            "You cannot attach a Physics Gantry Carriage to a Physics Gantry Carriage, Selection Cleared.");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the block physics gantry carriage glue
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void createthrusters$blockPhysicsGantryCarriageGlue(ServerPacketContext context, CallbackInfo ci) {
        PlaceMergingGluePacket packet = (PlaceMergingGluePacket) (Object) this;
        ServerPlayer player = context.player();
        Level level = context.level();
        if (player == null || level == null) {
            return;
        }

        if (!isPhysicsGantryCarriageEndpoint(level, packet.parentPos())
                && !isPhysicsGantryCarriageEndpoint(level, packet.childPos())) {
            return;
        }

        player.displayClientMessage(CARRIAGE_TO_CARRIAGE_MESSAGE, true);
        ci.cancel();
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

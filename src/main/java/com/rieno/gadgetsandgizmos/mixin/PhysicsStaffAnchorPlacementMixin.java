package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.content.PhysicsStaffItem;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Return sneaking staff clicks beside Backtanks to the normal placement handler
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler$PhysicsStaffMouseHandler")
public class PhysicsStaffAnchorPlacementMixin {
    // Allow the staff to place before Simulated starts a drag
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void ct$allowSneakPlacement(int button, int action, KeyMapping keyMapping,
                                        CallbackInfoReturnable<InteractCallback.Result> cir) {
        if (action != 1) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof PhysicsStaffItem)
                || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return;
        }

        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        if (PhysicsStaffItem.canPlaceAnchor(player, player.level(), target)) {
            cir.setReturnValue(InteractCallback.Result.empty());
        }
    }
}
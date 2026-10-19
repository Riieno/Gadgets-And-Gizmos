package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerSignalBus;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticBlockStateControl;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Apply face signals immediately so moving sub-levels do not rely on scheduled block ticks
@Mixin(DirectionalGearshiftBlock.class)
public abstract class DirectionalGearshiftBlockMixin{
    @Inject(method = "getPoweredState", at = @At("RETURN"), cancellable = true)
    private void ct$readFaceSignals(Level level, BlockState state, BlockPos pos,
                                   CallbackInfoReturnable<BlockState> cir){
        var block = (DirectionalGearshiftBlock)(Object)this;
        Direction left = block.getLeftDirection(state);
        Direction right = block.getRightDirection(state);
        BlockState updated = cir.getReturnValue();
        if(ContraptionNetworkLinkerSignalBus.getInjectedSignal(level, pos.relative(left), left) > 0)
            updated = updated.setValue(DirectionalGearshiftBlock.LEFT_POWERED, true);
        if(ContraptionNetworkLinkerSignalBus.getInjectedSignal(level, pos.relative(right), right) > 0)
            updated = updated.setValue(DirectionalGearshiftBlock.RIGHT_POWERED, true);
        cir.setReturnValue(updated);
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void ct$updateKinetics(BlockState state, Level level, BlockPos pos, Block source,
                                  BlockPos fromPos, boolean moving, CallbackInfo ci){
        if(level.isClientSide || !(level.getBlockEntity(pos) instanceof KineticBlockEntity target)) return;
        BlockState updated = ((DirectionalGearshiftBlock)(Object)this).getPoweredState(level, state, pos);
        KineticBlockStateControl.apply(target, updated);
        ci.cancel();
    }
}

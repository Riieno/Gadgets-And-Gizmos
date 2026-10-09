package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Retain native navigation animation without placing its detached block in the world
@Pseudo
@Mixin(targets = "ace.flight.block.NavigationTerminalBlockEntity", remap = false)
public abstract class FlightControlHostedStateMixin{
    @Redirect(method = "refreshVisualState", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean createThrusters$updateHostedState(Level level, BlockPos pos, BlockState state, int flags){
        return HostedBlockEntities.updateState(level, pos, state) || level.setBlock(pos, state, flags);
    }
}

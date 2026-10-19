package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Keep Flight Control's native mouse lease and rebase handshake for hosted controllers
@Pseudo
@Mixin(targets = "ace.flight.neoforge.FlightNetworkingNeoForge", remap = false)
public abstract class FlightControlHostedMouseNetworkingMixin{
    @Redirect(method = "lambda$handleSetMouseFlightTarget$0", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private static BlockEntity createThrusters$mouseTarget(Level level, BlockPos pos){
        BlockEntity component = HostedBlockEntities.resolve(level, pos);
        return component == null ? level.getBlockEntity(pos) : component;
    }
}

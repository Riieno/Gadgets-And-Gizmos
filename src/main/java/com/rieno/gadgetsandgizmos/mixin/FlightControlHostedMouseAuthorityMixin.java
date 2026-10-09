package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Preserve native lease expiry when the controlled component has no world block
@Pseudo
@Mixin(targets = "ace.flight.neoforge.MouseFlightControlAuthority", remap = false)
public abstract class FlightControlHostedMouseAuthorityMixin{
    @Redirect(method = "prune", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private static BlockEntity createThrusters$mouseLease(ServerLevel level, BlockPos pos){
        BlockEntity component = HostedBlockEntities.resolve(level, pos);
        return component == null ? level.getBlockEntity(pos) : component;
    }
}

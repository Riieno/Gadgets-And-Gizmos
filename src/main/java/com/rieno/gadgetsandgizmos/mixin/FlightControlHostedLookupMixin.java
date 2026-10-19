package com.rieno.gadgetsandgizmos.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Resolve ACC-hosted components only inside Flight Control's native linking logic
@Pseudo
@Mixin(targets = {
        "ace.flight.block.FlightControlComputerBlockEntity",
        "ace.flight.block.CreativeFlightControlComputerBlockEntity",
        "ace.flight.block.SmartVectorThrusterBlockEntity",
        "ace.flight.block.SmartVectorIonThrusterBlockEntity",
        "ace.flight.block.AttitudeHoldBlockEntity",
        "ace.flight.block.AltitudeHoldBlockEntity",
        "ace.flight.block.VelocityCouplerBlockEntity",
        "ace.flight.block.AngularVelocityCouplerBlockEntity",
        "ace.flight.block.DistanceCouplerBlockEntity",
        "ace.flight.block.MouseFlightControllerBlockEntity",
        "ace.flight.block.NavigationTerminalBlockEntity",
        "ace.flight.block.ForwardIndicatorBlockEntity",
        "ace.flight.block.HudProjectorBlockEntity",
        "ace.flight.block.FlightControlRedstoneProxyBlockEntity",
        "dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity"
}, remap = false, priority = 900)
public abstract class FlightControlHostedLookupMixin{
    @Redirect(method = "*", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"), require = 0)
    private static BlockEntity createThrusters$hostedLookup(Level level, BlockPos pos){
        BlockEntity component = HostedBlockEntities.resolve(level, pos);
        return component == null ? level.getBlockEntity(pos) : component;
    }
}

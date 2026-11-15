package com.rieno.gadgetsandgizmos.compat.computercraft.rdp;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

// Isolate the version-specific CC menu implementation behind stable addon types
public interface ComputerCraftRemoteDesktopBackend {
    default boolean supports(BlockEntity target){ return isAvailable(target); }

    boolean isAvailable(BlockEntity target);

    boolean open(ServerPlayer player, BlockEntity target);
}

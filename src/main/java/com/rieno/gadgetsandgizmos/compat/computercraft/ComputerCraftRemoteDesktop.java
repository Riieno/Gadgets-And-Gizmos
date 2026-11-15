package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.rdp.ComputerCraftRemoteDesktopBackend;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

// Connect CC computers to tablet RDP without exposing CC internals to callers
public final class ComputerCraftRemoteDesktop {
    private static final ComputerCraftRemoteDesktopBackend BACKEND = loadBackend();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the computer craft remote desktop
    private ComputerCraftRemoteDesktop() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Recognize computers independently of their wireless connection
    public static boolean supports(BlockEntity target){ return BACKEND.supports(target); }

    // Check if this is available
    public static boolean isAvailable(BlockEntity target) {
        return BACKEND.isAvailable(target);
    }

    // Open the computer craft remote desktop
    public static boolean open(ServerPlayer player, BlockEntity target) {
        return BACKEND.open(player, target);
    }

    // Load the native menu bridge for compatible CC installations
    private static ComputerCraftRemoteDesktopBackend loadBackend() {
        if(!ModList.get().isLoaded("computercraft")) return new UnavailableBackend();
        try {
            Class<?> type = Class.forName(
                    "com.rieno.gadgetsandgizmos.compat.computercraft.rdp."
                            + "ComputerCraftRemoteDesktop1180Backend");
            return (ComputerCraftRemoteDesktopBackend)
                    type.getConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException | LinkageError failure) {
            return new UnavailableBackend();
        }
    }

    // Keep remote desktop unavailable when its native bridge cannot load
    private static final class UnavailableBackend
            implements ComputerCraftRemoteDesktopBackend {
        @Override
        public boolean isAvailable(BlockEntity target) {
            return false;
        }

        @Override
        public boolean open(ServerPlayer player, BlockEntity target) {
            return false;
        }
    }
}

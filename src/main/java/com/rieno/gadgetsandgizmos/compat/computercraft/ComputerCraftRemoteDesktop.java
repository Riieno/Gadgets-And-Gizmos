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

// Connect wireless CC computers to tablet RDP without exposing CC internals to callers
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

    // Check if this is available
    public static boolean isAvailable(BlockEntity target) {
        return BACKEND.isAvailable(target);
    }

    // Open the computer craft remote desktop
    public static boolean open(ServerPlayer player, BlockEntity target) {
        return BACKEND.open(player, target);
    }

    // Load the backend for the supported computer craft version
    private static ComputerCraftRemoteDesktopBackend loadBackend() {
        String version = ModList.get().getModContainerById("computercraft")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
        if (!version.startsWith("1.118.")) {
            return new UnavailableBackend();
        }
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

    // Keep remote desktop unavailable for unsupported computer craft versions
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

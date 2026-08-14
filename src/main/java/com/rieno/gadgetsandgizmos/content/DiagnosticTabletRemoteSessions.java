package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Keep the short server permission granted after the RDP app finds its linked controller
public final class DiagnosticTabletRemoteSessions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long INTERACTION_LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(2L);
    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet remote sessions
    private DiagnosticTabletRemoteSessions() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Authorize the remote session
    static void authorize(ServerPlayer player, AdvancedContraptionControllerBlockEntity controller) {
        if (player == null || controller == null) return;
        SESSIONS.put(player.getUUID(), new Session(player.server, controller.getBlockPos(),
                com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper
                        .getContainingSubLevelId(controller),
                System.nanoTime() + INTERACTION_LIFETIME_NANOS, true));
    }

    // Authorize the remote session
    public static void authorize(ServerPlayer player, net.minecraft.world.level.block.entity.BlockEntity target) {
        if (player == null || target == null) return;
        SESSIONS.put(player.getUUID(), new Session(player.server, target.getBlockPos(),
                com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper
                        .getContainingSubLevelId(target),
                System.nanoTime() + INTERACTION_LIFETIME_NANOS, true));
    }

    // Consume the menu authorization
    static boolean consumeMenuAuthorization(net.minecraft.world.entity.player.Player player,
                                            BlockPos pos, UUID subLevelId) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        Session session = validSession(serverPlayer, pos, subLevelId);
        if (session == null || !session.menuPermit()) return false;
        SESSIONS.put(serverPlayer.getUUID(), session.withoutMenuPermit());
        return true;
    }

    // Check if this is interaction authorized
    public static boolean isInteractionAuthorized(ServerPlayer player, BlockPos pos, UUID subLevelId) {
        Session session = validSession(player, pos, subLevelId);
        if (session == null) return false;
        SESSIONS.put(player.getUUID(), session.renewed());
        return true;
    }

    // Get the valid session
    private static Session validSession(ServerPlayer player, BlockPos pos, UUID subLevelId) {
        if (player == null || pos == null) return null;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return null;
        if (session.server() != player.server || System.nanoTime() > session.expiresAtNanos()) {
            SESSIONS.remove(player.getUUID(), session);
            return null;
        }
        return session.pos().equals(pos) && Objects.equals(session.subLevelId(), subLevelId)
                ? session : null;
    }

    // Store the session
    private record Session(MinecraftServer server, BlockPos pos, UUID subLevelId, long expiresAtNanos,
                           boolean menuPermit) {
        // Initialize the session
        private Session {
            pos = pos.immutable();
        }

    // Copy the session without the menu permit
        private Session withoutMenuPermit() {
            return new Session(server, pos, subLevelId, expiresAtNanos, false);
        }

        // Get the renewed
        private Session renewed() {
            return new Session(server, pos, subLevelId,
                    System.nanoTime() + INTERACTION_LIFETIME_NANOS, menuPermit);
        }
    }
}

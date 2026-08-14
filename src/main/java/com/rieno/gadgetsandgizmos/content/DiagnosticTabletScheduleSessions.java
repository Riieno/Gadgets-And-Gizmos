package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.simibubi.create.content.trains.schedule.Schedule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Keep temporary schedule edits tied to the player that opened them
public final class DiagnosticTabletScheduleSessions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet schedule sessions
    private DiagnosticTabletScheduleSessions() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Open the diagnostic tablet schedule sessions
    public static boolean open(ServerPlayer viewer,
                               AdvancedContraptionControllerBlockEntity controller) {
        UUID pilotId = controller.getShippingSchedulePilotId();
        if (pilotId == null || viewer.server.getPlayerList().getPlayer(pilotId) == null) return false;
        ItemStack schedule = controller.copyShippingSchedule();
        if (schedule.isEmpty()) return false;
        SESSIONS.put(viewer.getUUID(), new Session(controller.getBlockPos(),
                SimulatedHelper.getContainingSubLevelId(controller), pilotId,
                viewer.server.getTickCount() + 20 * 60));
        viewer.openMenu(new MenuProvider() {
            // Get the display name
            @Override
            public Component getDisplayName() {
                return schedule.getHoverName();
            }

            // Create the menu
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                ItemStack original = player.getMainHandItem();
                player.setItemInHand(InteractionHand.MAIN_HAND, schedule.copy());
                try {
                    return ((MenuProvider) schedule.getItem()).createMenu(id, inventory, player);
                } finally {
                    player.setItemInHand(InteractionHand.MAIN_HAND, original);
                }
            }
        }, buffer -> ItemStack.STREAM_CODEC.encode(buffer, schedule));
        return true;
    }

    // Save the diagnostic tablet schedule sessions
    public static boolean save(ServerPlayer player, Schedule schedule) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null || player.server.getTickCount() > session.expiresAt()) return false;
        Object found = SimulatedHelper.findLoadedBlockEntityExact(player.level(),
                session.subLevelId(), session.controllerPos());
        if (!(found instanceof AdvancedContraptionControllerBlockEntity controller)) return false;
        if (schedule == null || schedule.entries.isEmpty()) {
            controller.removeShippingSchedule();
            return true;
        }
        return controller.installShippingSchedule(schedule, session.pilotId());
    }

    // Store the session
    private record Session(BlockPos controllerPos, UUID subLevelId, UUID pilotId, int expiresAt) {
        // Initialize the session
        private Session {
            controllerPos = controllerPos.immutable();
        }
    }
}

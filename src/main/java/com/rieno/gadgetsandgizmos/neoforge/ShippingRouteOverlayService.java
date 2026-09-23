package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShipControlModuleRuntime;
import com.rieno.gadgetsandgizmos.content.ShippingRouteSplineSnapshot;
import com.rieno.gadgetsandgizmos.content.ShippingScheduleItem;
import com.rieno.gadgetsandgizmos.content.ShippingScheduleRouteData;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingRouteSplinePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Synchronize independently visible and editable SCM schedule route splines
public final class ShippingRouteOverlayService {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int SYNC_INTERVAL_TICKS = 5;
    private static final double MAXIMUM_EDIT_DISTANCE = 128.0D;
    private static final Map<UUID, Subscription> SUBSCRIPTIONS = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Shipping Schedule route overlay service
    private ShippingRouteOverlayService() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Toggle one player's route overlay from its held Shipping Schedule
    public static void setVisible(ServerPlayer player, InteractionHand hand, boolean visible) {
        if (player == null || hand == null) return;
        ItemStack stack = player.getItemInHand(hand);
        if (!visible) {
            if (stack.getItem() instanceof ShippingScheduleItem) {
                ShippingScheduleRouteData.setVisible(stack, false);
            }
            clear(player);
            return;
        }
        if (!(stack.getItem() instanceof ShippingScheduleItem)) return;
        UUID ownerId = ShippingScheduleRouteData.owner(stack);
        ShippingRouteSplineSnapshot snapshot = ownerId == null ? null
                : ShipControlModuleRuntime.shippingRouteSplineSnapshot(
                player.serverLevel(), ownerId);
        if (snapshot == null || snapshot.routes().isEmpty()) {
            ShippingScheduleRouteData.setVisible(stack, false);
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.show_route.unavailable")
                    .withStyle(ChatFormatting.RED), true);
            sendClear(player);
            return;
        }
        ShippingScheduleRouteData.setVisible(stack, true);
        SUBSCRIPTIONS.put(player.getUUID(), new Subscription(ownerId, snapshot.revision()));
        send(player, snapshot);
    }

    // Toggle one player's route overlay directly from its SCM schedule graph.
    public static void setVisible(ServerPlayer player, UUID ownerId, boolean visible) {
        if (player == null || ownerId == null) return;
        if (!visible) {
            Subscription current = SUBSCRIPTIONS.get(player.getUUID());
            if (current == null || ownerId.equals(current.ownerId())) clear(player);
            return;
        }
        ShippingRouteSplineSnapshot snapshot = ShipControlModuleRuntime.shippingRouteSplineSnapshot(
                player.serverLevel(), ownerId);
        if (snapshot == null || snapshot.routes().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.show_route.unavailable")
                    .withStyle(ChatFormatting.RED), true);
            sendClear(player);
            return;
        }
        SUBSCRIPTIONS.put(player.getUUID(), new Subscription(ownerId, snapshot.revision()));
        send(player, snapshot);
    }

    // Apply one wrench edit to the player's subscribed SCM route
    public static void edit(ServerPlayer player, UUID ownerId, UUID routeId,
                            ShippingRouteSplineSnapshot.EditAction action,
                            int index, Vec3 position) {
        if (player == null || ownerId == null || routeId == null || action == null
                || !hasWrench(player) || !finite(position)
                || player.position().distanceToSqr(position)
                > MAXIMUM_EDIT_DISTANCE * MAXIMUM_EDIT_DISTANCE) return;
        Subscription subscription = SUBSCRIPTIONS.get(player.getUUID());
        if (subscription == null || !ownerId.equals(subscription.ownerId())) return;
        if (!ShipControlModuleRuntime.editShippingRouteSpline(
                player.serverLevel(), ownerId, routeId, action, index, position)) return;
        ShippingRouteSplineSnapshot snapshot = ShipControlModuleRuntime.shippingRouteSplineSnapshot(
                player.serverLevel(), ownerId);
        if (snapshot == null) return;
        SUBSCRIPTIONS.put(player.getUUID(), new Subscription(ownerId, snapshot.revision()));
        send(player, snapshot);
    }

    // Get the visible route owner ids whose schedule routes debug rendering must omit
    public static Set<UUID> visibleRouteOwners(UUID playerId) {
        Subscription subscription = playerId == null ? null : SUBSCRIPTIONS.get(playerId);
        return subscription == null ? Set.of() : Set.of(subscription.ownerId());
    }

    // Clear one player subscription and its client snapshot
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        SUBSCRIPTIONS.remove(player.getUUID());
        sendClear(player);
    }

    // Forget one disconnected player without attempting another network send
    public static void forget(UUID playerId) {
        if (playerId != null) SUBSCRIPTIONS.remove(playerId);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Refresh route snapshots whose prepared control points changed
    public static void onServerTick(ServerTickEvent.Post evt) {
        MinecraftServer server = evt.getServer();
        ShipControlModuleRuntime.tickScheduledRouteHazards(server);
        if (SUBSCRIPTIONS.isEmpty()
                || server.getTickCount() % SYNC_INTERVAL_TICKS != 0) return;
        Iterator<Map.Entry<UUID, Subscription>> iterator = SUBSCRIPTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Subscription> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            Subscription subscription = entry.getValue();
            ShippingRouteSplineSnapshot snapshot = ShipControlModuleRuntime.shippingRouteSplineSnapshot(
                    player.serverLevel(), subscription.ownerId());
            if (snapshot == null) {
                if (subscription.lastRevision() != Long.MIN_VALUE) {
                    sendClear(player);
                    entry.setValue(new Subscription(subscription.ownerId(), Long.MIN_VALUE));
                }
                continue;
            }
            if (snapshot.revision() == subscription.lastRevision()) continue;
            send(player, snapshot);
            entry.setValue(new Subscription(subscription.ownerId(), snapshot.revision()));
        }
    }

    // Clear every route subscription when the server stops
    public static void onServerStopped(ServerStoppedEvent evt) {
        SUBSCRIPTIONS.clear();
        ShipControlModuleRuntime.clearScheduledRouteHazardJobs();
    }

    // Send one visible route snapshot
    private static void send(ServerPlayer player, ShippingRouteSplineSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player, new ShippingRouteSplinePayload(
                true, snapshot.ownerId(), snapshot.revision(), snapshot.routes()));
    }

    // Clear one client's visible route snapshot
    private static void sendClear(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ShippingRouteSplinePayload(
                false, null, 0L, java.util.List.of()));
    }

    // Check whether the player holds a wrench in either hand
    private static boolean hasWrench(ServerPlayer player) {
        return player.getMainHandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)
                || player.getOffhandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH);
    }

    // Check whether one world position contains only finite coordinates
    private static boolean finite(Vec3 position) {
        return position != null && Double.isFinite(position.x)
                && Double.isFinite(position.y) && Double.isFinite(position.z);
    }

    // Retain one viewer's selected SCM route revision
    private record Subscription(UUID ownerId, long lastRevision) {
    }
}

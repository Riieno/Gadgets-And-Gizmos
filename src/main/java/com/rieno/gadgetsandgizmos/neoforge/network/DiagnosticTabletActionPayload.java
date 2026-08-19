package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletApps;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletDatabase;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;

// Carry one validated tablet app action from the client to its server context
public record DiagnosticTabletActionPayload(boolean placed, InteractionHand hand,
                                            BlockPos tabletPos, UUID tabletSubLevelId,
                                            UUID sourceTabletId,
                                            ResourceLocation appId, String tabId,
                                            String actionId, String value)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<DiagnosticTabletActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "diagnostic_tablet_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiagnosticTabletActionPayload> STREAM_CODEC =
            StreamCodec.of(DiagnosticTabletActionPayload::encode, DiagnosticTabletActionPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet action
    public DiagnosticTabletActionPayload(boolean placed, InteractionHand hand,
                                         BlockPos tabletPos, UUID tabletSubLevelId,
                                         ResourceLocation appId, String tabId,
                                         String actionId, String val) {
        this(placed, hand, tabletPos, tabletSubLevelId, null,
                appId, tabId, actionId, val);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the diagnostic tablet action
    public static void handle(DiagnosticTabletActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!CTFeatureToggles.isItemEnabled("diagnostic_tablet")) return;
            ItemStack stack = payload.placed() ? ItemStack.EMPTY : player.getItemInHand(payload.hand());
            DiagnosticTabletBlockEntity blockTablet = payload.placed()
                    && SimulatedHelper.findLoadedBlockEntityExact(player.level(),
                    payload.tabletSubLevelId(), payload.tabletPos()) instanceof DiagnosticTabletBlockEntity tablet
                    ? tablet : null;
            if (blockTablet == null && !(stack.getItem() instanceof DiagnosticTabletItem)) return;
            if (blockTablet != null) {
                var tabletCenter = SimulatedHelper.toGlobalWorldPosition(
                        blockTablet, blockTablet.getBlockPos().getCenter());
                if (tabletCenter == null || player.distanceToSqr(tabletCenter) > 256.0D) return;
            }

            DiagnosticTabletData.State state = blockTablet == null
                    ? DiagnosticTabletData.read(stack) : blockTablet.state();
            if (payload.sourceTabletId() != null && state.tabletId() != null
                    && !payload.sourceTabletId().equals(state.tabletId())) {
                return;
            }
            if (state.tabletId() == null) {
                state = state.withTabletId(payload.sourceTabletId() == null
                        ? UUID.randomUUID() : payload.sourceTabletId());
            }
            DiagnosticTabletDatabase database = DiagnosticTabletDatabase.forServer(player.server);
            database.importLegacy(state.tabletId(), state);
            state = state.withoutLegacyAppData();
            ResourceLocation settingsId = DiagnosticTabletData.appId("settings");
            ResourceLocation homeId = DiagnosticTabletData.appId("home");
            if (!homeId.equals(payload.appId()) && !settingsId.equals(payload.appId())
                    && !TabletStorageApi.storage().installedApps(state.tabletId()).contains(payload.appId())) {
                return;
            }
            boolean backgroundRefresh = "background_refresh".equals(payload.actionId());
            if (!backgroundRefresh) state = state.withApp(payload.appId(), payload.tabId());
            boolean stateOnlyAction = true;
            switch (payload.actionId()) {
                case "mode_cycle" -> state = state.withMode(
                        TabletInteractionMode.fromId(payload.value()), "");
                case "begin_reader" -> state = state.withMode(
                        TabletInteractionMode.READER, payload.value());
                case "begin_push" -> state = state.withMode(
                        TabletInteractionMode.PUSH, payload.value());
                case "begin_logistics_run", "edit_logistics_run" -> {
                    state = state.withMode(TabletInteractionMode.READER, "configure_run");
                    stateOnlyAction = false;
                }
                case "clear_selections" -> DiagnosticTabletAppStorage.clearSelections(
                        player.server, state.tabletId(), payload.appId());
                case "select" -> {
                }
                default -> stateOnlyAction = false;
            }
            if (blockTablet == null) DiagnosticTabletData.write(stack, state);
            else blockTablet.setState(state);

            if (!stateOnlyAction) {
                TabletAction action = new TabletAction(payload.appId(), payload.tabId(),
                        backgroundRefresh ? "refresh" : payload.actionId(),
                        Map.of("value", payload.value()));
                var res = DiagnosticTabletApps.dispatch(player, stack, state, action,
                        blockTablet != null, state.tabletId(),
                        blockTablet == null ? null : payload.tabletSubLevelId(),
                        blockTablet == null ? null : payload.tabletPos());
            }
            if ("select".equals(payload.actionId()) || homeId.equals(payload.appId())) {
                DiagnosticTabletApps.dispatch(player, stack, state,
                        new TabletAction(settingsId, "tablet", "refresh", Map.of()),
                        blockTablet != null, state.tabletId(),
                        blockTablet == null ? null : payload.tabletSubLevelId(),
                        blockTablet == null ? null : payload.tabletPos());
            }
        });
    }

    // Encode the diagnostic tablet action
    private static void encode(RegistryFriendlyByteBuf buffer, DiagnosticTabletActionPayload payload) {
        buffer.writeBoolean(payload.placed());
        buffer.writeEnum(payload.hand());
        buffer.writeBlockPos(payload.tabletPos());
        buffer.writeBoolean(payload.tabletSubLevelId() != null);
        if (payload.tabletSubLevelId() != null) buffer.writeUUID(payload.tabletSubLevelId());
        buffer.writeBoolean(payload.sourceTabletId() != null);
        if (payload.sourceTabletId() != null) buffer.writeUUID(payload.sourceTabletId());
        buffer.writeResourceLocation(payload.appId());
        buffer.writeUtf(payload.tabId(), 64);
        buffer.writeUtf(payload.actionId(), 160);
        buffer.writeUtf(payload.value(), 256);
    }

    // Decode the diagnostic tablet action
    private static DiagnosticTabletActionPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean placed = buffer.readBoolean();
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        BlockPos pos = buffer.readBlockPos();
        UUID subLevel = buffer.readBoolean() ? buffer.readUUID() : null;
        UUID sourceTabletId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new DiagnosticTabletActionPayload(placed, hand, pos, subLevel, sourceTabletId,
                buffer.readResourceLocation(), buffer.readUtf(64), buffer.readUtf(160), buffer.readUtf(256));
    }
}

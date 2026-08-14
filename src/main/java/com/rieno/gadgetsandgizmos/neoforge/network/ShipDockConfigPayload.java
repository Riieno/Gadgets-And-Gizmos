package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Send Ship Dock Config
public record ShipDockConfigPayload(
        BlockPos pos,
        UUID subLevelId,
        String name,
        boolean refuel,
        boolean restock,
        boolean packages
)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ShipDockConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "ship_dock_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipDockConfigPayload> STREAM_CODEC =
            StreamCodec.of(ShipDockConfigPayload::encode, ShipDockConfigPayload::decode);

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

    // Handle the ship dock config
    public static void handle(ShipDockConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(SimulatedHelper.findLoadedBlockEntityExact(
                    player.level(), payload.subLevelId(), payload.pos())
                    instanceof ShipDockBlockEntity dock)) {
                return;
            }
            var dockWorldPosition = SimulatedHelper.toGlobalWorldPosition(
                    dock, dock.getBlockPos().getCenter());
            if (player.position().distanceToSqr(dockWorldPosition) > 100.0D) {
                return;
            }
            dock.configure(payload.name(), payload.refuel(), payload.restock(), payload.packages());
        });
    }

    // Encode the ship dock config
    private static void encode(RegistryFriendlyByteBuf buffer, ShipDockConfigPayload payload) {
        buffer.writeBlockPos(payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeUtf(payload.name(), 64);
        buffer.writeBoolean(payload.refuel());
        buffer.writeBoolean(payload.restock());
        buffer.writeBoolean(payload.packages());
    }

    // Decode the ship dock config
    private static ShipDockConfigPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ShipDockConfigPayload(pos, subLevelId, buffer.readUtf(64),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }
}

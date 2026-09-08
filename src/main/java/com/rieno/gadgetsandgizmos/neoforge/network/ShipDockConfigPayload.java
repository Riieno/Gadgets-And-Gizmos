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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Send Ship Dock Config
public record ShipDockConfigPayload(
        BlockPos pos,
        UUID subLevelId,
        String name,
        boolean refuel,
        boolean restock,
        boolean packages,
        boolean doorControlEnabled,
        int doorControlMask,
        List<ShipDockBlockEntity.ConnectorReference> refuelConnectors,
        List<ShipDockBlockEntity.ConnectorReference> restockConnectors,
        List<ShipDockBlockEntity.ConnectorReference> packageConnectors
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
    private static final int MAX_CONNECTOR_REFERENCES = 64;

    // Initialize the ship dock config payload
    public ShipDockConfigPayload {
        refuelConnectors = connectorReferences(refuelConnectors);
        restockConnectors = connectorReferences(restockConnectors);
        packageConnectors = connectorReferences(packageConnectors);
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
            dock.configure(payload.name(), payload.refuel(), payload.restock(), payload.packages(),
                    payload.doorControlEnabled(), payload.doorControlMask(),
                    payload.refuelConnectors(), payload.restockConnectors(),
                    payload.packageConnectors());
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
        buffer.writeBoolean(payload.doorControlEnabled());
        buffer.writeVarInt(payload.doorControlMask());
        writeConnectorReferences(buffer, payload.refuelConnectors());
        writeConnectorReferences(buffer, payload.restockConnectors());
        writeConnectorReferences(buffer, payload.packageConnectors());
    }

    // Decode the ship dock config
    private static ShipDockConfigPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ShipDockConfigPayload(pos, subLevelId, buffer.readUtf(64),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readVarInt(),
                readConnectorReferences(buffer), readConnectorReferences(buffer),
                readConnectorReferences(buffer));
    }

    // Write connector references
    private static void writeConnectorReferences(
            RegistryFriendlyByteBuf buffer,
            List<ShipDockBlockEntity.ConnectorReference> references
    ) {
        buffer.writeVarInt(references.size());
        for (ShipDockBlockEntity.ConnectorReference reference : references) {
            buffer.writeBoolean(reference.subLevelId() != null);
            if (reference.subLevelId() != null) {
                buffer.writeUUID(reference.subLevelId());
            }
            buffer.writeBlockPos(reference.blockPosition());
        }
    }

    // Read connector references
    private static List<ShipDockBlockEntity.ConnectorReference> readConnectorReferences(
            RegistryFriendlyByteBuf buffer
    ) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_CONNECTOR_REFERENCES) {
            throw new IllegalArgumentException("Invalid ship dock connector selection size: " + count);
        }
        List<ShipDockBlockEntity.ConnectorReference> references = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
            references.add(new ShipDockBlockEntity.ConnectorReference(
                    subLevelId, buffer.readBlockPos()));
        }
        return references;
    }

    // Normalize connector references before encoding
    private static List<ShipDockBlockEntity.ConnectorReference> connectorReferences(
            List<ShipDockBlockEntity.ConnectorReference> references
    ) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        List<ShipDockBlockEntity.ConnectorReference> normalized = new ArrayList<>();
        for (ShipDockBlockEntity.ConnectorReference reference : references) {
            if (reference == null || normalized.contains(reference)) {
                continue;
            }
            normalized.add(reference);
            if (normalized.size() >= MAX_CONNECTOR_REFERENCES) {
                break;
            }
        }
        return List.copyOf(normalized);
    }
}

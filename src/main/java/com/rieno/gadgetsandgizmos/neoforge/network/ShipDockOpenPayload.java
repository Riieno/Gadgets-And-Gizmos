package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Open Ship Dock
public record ShipDockOpenPayload(
        BlockPos pos,
        UUID subLevelId,
        String name,
        boolean refuel,
        boolean restock,
        boolean packages,
        boolean doorControlEnabled,
        int doorControlMask,
        List<ShipDockBlockEntity.ConnectorReference> availableConnectors,
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

    public static final Type<ShipDockOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "ship_dock_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipDockOpenPayload> STREAM_CODEC =
            StreamCodec.of(ShipDockOpenPayload::encode, ShipDockOpenPayload::decode);
    private static final int MAX_CONNECTOR_REFERENCES = 64;

    // Initialize the ship dock open payload
    public ShipDockOpenPayload {
        availableConnectors = connectorReferences(availableConnectors);
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

    // Handle the ship dock open
    public static void handle(ShipDockOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> screens = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.CTClientScreens");
                screens.getMethod("openShipDock", BlockPos.class, UUID.class, String.class,
                                boolean.class, boolean.class, boolean.class, boolean.class,
                                int.class, List.class, List.class, List.class, List.class)
                        .invoke(null, payload.pos(), payload.subLevelId(), payload.name(), payload.refuel(),
                                payload.restock(), payload.packages(), payload.doorControlEnabled(),
                                payload.doorControlMask(), payload.availableConnectors(),
                                payload.refuelConnectors(), payload.restockConnectors(),
                                payload.packageConnectors());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the ship dock open
    private static void encode(RegistryFriendlyByteBuf buffer, ShipDockOpenPayload payload) {
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
        writeConnectorReferences(buffer, payload.availableConnectors());
        writeConnectorReferences(buffer, payload.refuelConnectors());
        writeConnectorReferences(buffer, payload.restockConnectors());
        writeConnectorReferences(buffer, payload.packageConnectors());
    }

    // Decode the ship dock open
    private static ShipDockOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ShipDockOpenPayload(pos, subLevelId, buffer.readUtf(64),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readVarInt(),
                readConnectorReferences(buffer), readConnectorReferences(buffer),
                readConnectorReferences(buffer), readConnectorReferences(buffer));
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

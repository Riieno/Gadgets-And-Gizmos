package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Open Ship Dock
public record ShipDockOpenPayload(
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

    public static final Type<ShipDockOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "ship_dock_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipDockOpenPayload> STREAM_CODEC =
            StreamCodec.of(ShipDockOpenPayload::encode, ShipDockOpenPayload::decode);

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
                                boolean.class, boolean.class, boolean.class)
                        .invoke(null, payload.pos(), payload.subLevelId(), payload.name(), payload.refuel(),
                                payload.restock(), payload.packages());
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
    }

    // Decode the ship dock open
    private static ShipDockOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ShipDockOpenPayload(pos, subLevelId, buffer.readUtf(64),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }
}

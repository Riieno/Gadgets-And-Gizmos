package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;
import java.util.UUID;

// Sync Controller Runtime
public record ControllerRuntimeSyncPayload(BlockPos pos, UUID subLevelId,
                                           int outputSignal) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ControllerRuntimeSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "controller_runtime_sync"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ControllerRuntimeSyncPayload>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
            ControllerRuntimeSyncPayload::encode,
            ControllerRuntimeSyncPayload::decode);

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

    // Handle the controller runtime sync
    public static void handle(ControllerRuntimeSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler");
                Method method = handlerClass.getMethod("applyRuntimeSignal", ControllerRuntimeSyncPayload.class);
                method.invoke(null, payload);
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the controller runtime sync
    private static void encode(RegistryFriendlyByteBuf buffer, ControllerRuntimeSyncPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeVarInt(payload.outputSignal());
    }

    // Decode the controller runtime sync
    private static ControllerRuntimeSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ControllerRuntimeSyncPayload(pos, subLevelId, buffer.readVarInt());
    }
}

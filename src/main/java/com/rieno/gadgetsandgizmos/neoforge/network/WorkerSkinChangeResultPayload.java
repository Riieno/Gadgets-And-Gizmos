package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Return one Worker Graph mannequin skin lookup result to the requesting client
public record WorkerSkinChangeResultPayload(UUID workerId, boolean success,
                                            String playerName, String message) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<WorkerSkinChangeResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "worker_skin_change_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerSkinChangeResultPayload> STREAM_CODEC =
            StreamCodec.of(WorkerSkinChangeResultPayload::encode, WorkerSkinChangeResultPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Normalize the result text
    public WorkerSkinChangeResultPayload {
        playerName = playerName == null ? "" : playerName;
        message = message == null ? "" : message;
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

    // Forward the result to the open client-side ACC screen
    public static void handle(WorkerSkinChangeResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> screenClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen");
                screenClass.getMethod("applyWorkerSkinResult", UUID.class, boolean.class,
                        String.class, String.class).invoke(null, payload.workerId(), payload.success(),
                        payload.playerName(), payload.message());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode one skin lookup result
    private static void encode(RegistryFriendlyByteBuf buffer, WorkerSkinChangeResultPayload payload) {
        buffer.writeUUID(payload.workerId());
        buffer.writeBoolean(payload.success());
        buffer.writeUtf(payload.playerName(), 64);
        buffer.writeUtf(payload.message(), 128);
    }

    // Decode one skin lookup result
    private static WorkerSkinChangeResultPayload decode(RegistryFriendlyByteBuf buffer) {
        return new WorkerSkinChangeResultPayload(buffer.readUUID(), buffer.readBoolean(),
                buffer.readUtf(64), buffer.readUtf(128));
    }
}

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

// Return one Player Mannequin skin application result to the requesting client
public record MannequinSkinChangeResultPayload(int entityId, Result result) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<MannequinSkinChangeResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "mannequin_skin_change_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MannequinSkinChangeResultPayload> STREAM_CODEC =
            StreamCodec.of(MannequinSkinChangeResultPayload::encode, MannequinSkinChangeResultPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep malformed packet values harmless
    public MannequinSkinChangeResultPayload {
        result = result == null ? Result.UNABLE_TO_SET_SKIN : result;
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

    // Forward the result to the client-only mannequin pose screen
    public static void handle(MannequinSkinChangeResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> screenClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.ArmorStandPoseScreen");
                screenClass.getMethod("applyMannequinSkinResult", int.class, Result.class)
                        .invoke(null, payload.entityId(), payload.result());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode one skin application result
    private static void encode(RegistryFriendlyByteBuf buffer, MannequinSkinChangeResultPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeVarInt(payload.result().ordinal());
    }

    // Decode one skin application result
    private static MannequinSkinChangeResultPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MannequinSkinChangeResultPayload(buffer.readVarInt(), Result.byId(buffer.readVarInt()));
    }

    // Represent every user-visible skin request outcome
    public enum Result {
        APPLIED,
        PLAYER_DOES_NOT_EXIST,
        UNABLE_TO_SET_SKIN,
        UNAVAILABLE;

        // Resolve a packet value safely
        private static Result byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : UNABLE_TO_SET_SKIN;
        }
    }
}
package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Open Armor Stand Pose
public record ArmorStandPoseOpenPayload(int entityId) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ArmorStandPoseOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "armor_stand_pose_open"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ArmorStandPoseOpenPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(ArmorStandPoseOpenPayload::encode, ArmorStandPoseOpenPayload::decode);

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

    // Handle the armor stand pose open
    public static void handle(ArmorStandPoseOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> screenClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.ArmorStandPoseScreen");
                screenClass.getMethod("open", int.class).invoke(null, payload.entityId());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the armor stand pose open
    private static void encode(RegistryFriendlyByteBuf buffer, ArmorStandPoseOpenPayload payload) {
        buffer.writeVarInt(payload.entityId());
    }

    // Decode the armor stand pose open
    private static ArmorStandPoseOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ArmorStandPoseOpenPayload(buffer.readVarInt());
    }
}

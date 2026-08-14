package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.neoforge.CTPlayerEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Armor Stand Pose Preference
public record ArmorStandPosePreferencePayload(boolean enabled, boolean preferStrawStatuesGui) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ArmorStandPosePreferencePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "armor_stand_pose_preference"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ArmorStandPosePreferencePayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(ArmorStandPosePreferencePayload::encode, ArmorStandPosePreferencePayload::decode);

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

    // Handle the armor stand pose preference
    public static void handle(ArmorStandPosePreferencePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                CTPlayerEvents.setArmorStandPoseGuiPreference(serverPlayer, payload.enabled(), payload.preferStrawStatuesGui());
            }
        });
    }

    // Encode the armor stand pose preference
    private static void encode(RegistryFriendlyByteBuf buffer, ArmorStandPosePreferencePayload payload) {
        buffer.writeBoolean(payload.enabled());
        buffer.writeBoolean(payload.preferStrawStatuesGui());
    }

    // Decode the armor stand pose preference
    private static ArmorStandPosePreferencePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ArmorStandPosePreferencePayload(buffer.readBoolean(), buffer.readBoolean());
    }
}

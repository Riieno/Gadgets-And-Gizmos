package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.pose.ArmorStandPoseData;
import com.rieno.gadgetsandgizmos.neoforge.CTPlayerEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Sync Armor Stand Pose
public record ArmorStandPoseSyncPayload(int entityId, CompoundTag poseTag) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ArmorStandPoseSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "armor_stand_pose_sync"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ArmorStandPoseSyncPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(ArmorStandPoseSyncPayload::encode, ArmorStandPoseSyncPayload::decode);

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

    // Handle the armor stand pose sync
    public static void handle(ArmorStandPoseSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel serverLevel = player.serverLevel();
            Entity entity = serverLevel.getEntity(payload.entityId());
            if (!(entity instanceof ArmorStand armorStand)
                    || !armorStand.isAlive()
                    || !CTPlayerEvents.canUseArmorStandPoseGui(armorStand)) {
                return;
            }
            if (player.distanceToSqr(armorStand) > 64.0D) {
                return;
            }
            ArmorStandPoseData.applyAllowedTag(armorStand, payload.poseTag());
        });
    }

    // Encode the armor stand pose sync
    private static void encode(RegistryFriendlyByteBuf buffer, ArmorStandPoseSyncPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeNbt(payload.poseTag() == null ? new CompoundTag() : payload.poseTag());
    }

    // Decode the armor stand pose sync
    private static ArmorStandPoseSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        CompoundTag tag = buffer.readNbt();
        return new ArmorStandPoseSyncPayload(entityId, tag == null ? new CompoundTag() : tag);
    }
}

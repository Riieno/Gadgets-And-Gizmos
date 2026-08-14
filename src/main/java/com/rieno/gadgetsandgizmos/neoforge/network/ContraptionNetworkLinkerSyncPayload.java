package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerTracker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Sync Contraption Network Linker
public record ContraptionNetworkLinkerSyncPayload(
        InteractionHand hand,
        CompoundTag linkerRoot
) implements CustomPacketPayload {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ContraptionNetworkLinkerSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "contraption_network_linker_sync"));

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ContraptionNetworkLinkerSyncPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    ContraptionNetworkLinkerSyncPayload::encode,
                    ContraptionNetworkLinkerSyncPayload::decode);

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

    // Handle the contraption network linker sync
    public static void handle(ContraptionNetworkLinkerSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            ItemStack stack = player.getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof ContraptionNetworkLinkerItem)) {
                return;
            }
            CompoundTag incomingRoot = payload.linkerRoot() == null ? new CompoundTag() : payload.linkerRoot();
            CompoundTag root = ContraptionNetworkLinkerData.mergeClientEditIfCurrent(stack, incomingRoot);
            if (root != null) {
                if (!ContraptionNetworkLinkerTracker.get(serverPlayer.getServer())
                        .canMutateLinker(serverPlayer.serverLevel(), stack)) {
                    return;
                }
                ContraptionNetworkLinkerData.writeRoot(stack, root);
                ContraptionNetworkLinkerTracker.get(serverPlayer.getServer())
                        .observeLinker(serverPlayer.serverLevel(), stack);
                ContraptionNetworkLinkerData.ensureClientSnapshot(stack);
            }
        });
    }

    // Encode the contraption network linker sync
    private static void encode(RegistryFriendlyByteBuf buffer, ContraptionNetworkLinkerSyncPayload payload) {
        buffer.writeEnum(payload.hand());
        buffer.writeNbt(payload.linkerRoot() == null ? new CompoundTag() : payload.linkerRoot());
    }

    // Decode the contraption network linker sync
    private static ContraptionNetworkLinkerSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        CompoundTag root = buffer.readNbt();
        return new ContraptionNetworkLinkerSyncPayload(hand, root == null ? new CompoundTag() : root);
    }
}

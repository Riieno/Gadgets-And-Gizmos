package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

// Update the item lock configured on a Shipping Manifest's attached container
public record ShippingManifestLockPayload(
        BlockPos pos,
        boolean locked,
        List<ItemStack> filters
) implements CustomPacketPayload {
    public static final Type<ShippingManifestLockPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "shipping_manifest_lock"));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> FILTERS_CODEC =
            ByteBufCodecs.collection(ArrayList::new, ItemStack.OPTIONAL_STREAM_CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, ShippingManifestLockPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, ShippingManifestLockPayload::pos,
                    ByteBufCodecs.BOOL, ShippingManifestLockPayload::locked,
                    FILTERS_CODEC, ShippingManifestLockPayload::filters,
                    ShippingManifestLockPayload::new);

    // Get the type
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Apply the container lock on the server
    public static void handle(ShippingManifestLockPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)
                    || !player.level().hasChunkAt(payload.pos())
                    || player.blockPosition().distSqr(payload.pos()) > 100.0D
                    || CTBlocks.SHIPPING_MANIFEST == null
                    || !player.level().getBlockState(payload.pos()).is(CTBlocks.SHIPPING_MANIFEST.get())) return;
            BlockEntity blockEntity = player.level().getChunkAt(payload.pos())
                    .getBlockEntity(payload.pos(), LevelChunk.EntityCreationType.IMMEDIATE);
            if (blockEntity instanceof ShippingManifestBlockEntity manifest) {
                manifest.setContainerLock(payload.locked(), payload.filters());
            }
        });
    }
}

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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Shipping Manifest Refresh
public record ShippingManifestRefreshPayload(BlockPos pos) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ShippingManifestRefreshPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "shipping_manifest_refresh"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ShippingManifestRefreshPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.composite(BlockPos.STREAM_CODEC,
                    ShippingManifestRefreshPayload::pos, ShippingManifestRefreshPayload::new);

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

    // Handle the shipping manifest refresh
    public static void handle(ShippingManifestRefreshPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)
                    || !player.level().hasChunkAt(payload.pos())
                    || player.blockPosition().distSqr(payload.pos()) > 100.0D
                    || CTBlocks.SHIPPING_MANIFEST == null
                    || !player.level().getBlockState(payload.pos()).is(CTBlocks.SHIPPING_MANIFEST.get())) {
                return;
            }

            BlockEntity blockEntity = player.level().getChunkAt(payload.pos())
                    .getBlockEntity(payload.pos(), LevelChunk.EntityCreationType.IMMEDIATE);
            if (blockEntity instanceof ShippingManifestBlockEntity manifest) {
                manifest.refreshNow();
            }
        });
    }
}

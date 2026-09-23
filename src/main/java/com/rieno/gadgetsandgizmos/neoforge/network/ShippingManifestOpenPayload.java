package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Open Shipping Manifest
public record ShippingManifestOpenPayload(
        BlockPos pos,
        int resourceUses,
        int availableResourceUses,
        ClipboardContent content
) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ShippingManifestOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "shipping_manifest_open"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ShippingManifestOpenPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ShippingManifestOpenPayload::pos,
                    ByteBufCodecs.VAR_INT, ShippingManifestOpenPayload::resourceUses,
                    ByteBufCodecs.VAR_INT, ShippingManifestOpenPayload::availableResourceUses,
                    ClipboardContent.STREAM_CODEC, ShippingManifestOpenPayload::content,
                    ShippingManifestOpenPayload::new);

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

    // Handle the shipping manifest open
    public static void handle(ShippingManifestOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> clientScreens = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.CTClientScreens");
                clientScreens.getMethod("openShippingManifest", BlockPos.class, ClipboardContent.class,
                        int.class, int.class).invoke(null, payload.pos(), payload.content(),
                        payload.resourceUses(), payload.availableResourceUses());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

}

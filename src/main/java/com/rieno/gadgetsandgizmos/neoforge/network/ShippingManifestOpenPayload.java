package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Open Shipping Manifest
public record ShippingManifestOpenPayload(ClipboardContent content) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ShippingManifestOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "shipping_manifest_open"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ShippingManifestOpenPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(ShippingManifestOpenPayload::encode, ShippingManifestOpenPayload::decode);

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
                clientScreens.getMethod("openShippingManifest", ClipboardContent.class).invoke(null, payload.content());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the shipping manifest open
    private static void encode(RegistryFriendlyByteBuf buffer, ShippingManifestOpenPayload payload) {
        ClipboardContent.STREAM_CODEC.encode(buffer, payload.content());
    }

    // Decode the shipping manifest open
    private static ShippingManifestOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ShippingManifestOpenPayload(ClipboardContent.STREAM_CODEC.decode(buffer));
    }
}

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

import java.util.ArrayList;
import java.util.List;

// Send Advanced HUD Image Catalog
public record AdvancedHudImageCatalogPayload(List<String> images, String uploadedName,
                                             String message, boolean uploadResult)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedHudImageCatalogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_hud_image_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedHudImageCatalogPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedHudImageCatalogPayload::encode, AdvancedHudImageCatalogPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD image catalog
    public AdvancedHudImageCatalogPayload {
        images = images == null ? List.of() : List.copyOf(images);
        uploadedName = uploadedName == null ? "" : uploadedName;
        message = message == null ? "" : message;
    }

    // Initialize the advanced HUD image catalog
    public AdvancedHudImageCatalogPayload(List<String> images, String uploadedName, String msg) {
        this(images, uploadedName, msg, false);
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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the advanced HUD image catalog
    public static void handle(AdvancedHudImageCatalogPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> screen = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen");
                screen.getMethod("applyHudImageCatalog",
                                List.class, String.class, String.class, boolean.class)
                        .invoke(null, payload.images(), payload.uploadedName(),
                                payload.message(), payload.uploadResult());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the advanced HUD image catalog
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedHudImageCatalogPayload payload) {
        buffer.writeVarInt(payload.images().size());
        for (String image : payload.images()) {
            buffer.writeUtf(image, 96);
        }
        buffer.writeUtf(payload.uploadedName(), 96);
        buffer.writeUtf(payload.message(), 256);
        buffer.writeBoolean(payload.uploadResult());
    }

    // Decode the advanced HUD image catalog
    private static AdvancedHudImageCatalogPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 512) {
            throw new IllegalArgumentException("Invalid HUD image catalog size: " + count);
        }
        List<String> images = new ArrayList<>(count);
        for (int idx = 0; idx < count; idx++) {
            images.add(buffer.readUtf(96));
        }
        return new AdvancedHudImageCatalogPayload(
                images, buffer.readUtf(96), buffer.readUtf(256), buffer.readBoolean());
    }
}

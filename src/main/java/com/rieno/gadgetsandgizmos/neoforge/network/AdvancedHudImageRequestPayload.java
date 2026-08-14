package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudImageStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Request Advanced HUD Image
public record AdvancedHudImageRequestPayload(String filename) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedHudImageRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_hud_image_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedHudImageRequestPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedHudImageRequestPayload::encode, AdvancedHudImageRequestPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD image request
    public AdvancedHudImageRequestPayload {
        filename = filename == null ? "" : filename;
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

    // Handle the advanced HUD image request
    public static void handle(AdvancedHudImageRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.filename().isBlank()) {
                PacketDistributor.sendToPlayer(player, new AdvancedHudImageCatalogPayload(
                        AdvancedHudImageStore.list(player.getServer()), "", ""));
                return;
            }
            AdvancedHudImageStore.read(player.getServer(), payload.filename()).ifPresentOrElse(
                    data -> AdvancedHudImageDataPayload.send(player, payload.filename(), data),
                    () -> PacketDistributor.sendToPlayer(player, new AdvancedHudImageCatalogPayload(
                            AdvancedHudImageStore.list(player.getServer()), "",
                            "Uploaded image not found: " + payload.filename())));
        });
    }

    // Encode the advanced HUD image request
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedHudImageRequestPayload payload) {
        buffer.writeUtf(payload.filename(), 96);
    }

    // Decode the advanced HUD image request
    private static AdvancedHudImageRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AdvancedHudImageRequestPayload(buffer.readUtf(96));
    }
}

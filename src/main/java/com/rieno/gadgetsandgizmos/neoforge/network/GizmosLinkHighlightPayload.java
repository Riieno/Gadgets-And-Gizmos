package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Gizmos Link Highlight
public record GizmosLinkHighlightPayload(boolean visible, CompoundTag root) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<GizmosLinkHighlightPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "gizmos_link_highlights"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, GizmosLinkHighlightPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    GizmosLinkHighlightPayload::encode,
                    GizmosLinkHighlightPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the gizmos link highlight
    public GizmosLinkHighlightPayload {
        root = root == null ? new CompoundTag() : root.copy();
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

    // Handle the gizmos link highlight
    public static void handle(GizmosLinkHighlightPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> rendererClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.ContraptionNetworkLinkerFaceRenderer");
                rendererClass
                        .getMethod("setCommandHighlights", boolean.class, CompoundTag.class)
                        .invoke(null, payload.visible(), payload.root());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the gizmos link highlight
    private static void encode(RegistryFriendlyByteBuf buffer, GizmosLinkHighlightPayload payload) {
        buffer.writeBoolean(payload.visible());
        buffer.writeNbt(payload.root());
    }

    // Decode the gizmos link highlight
    private static GizmosLinkHighlightPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        CompoundTag root = buffer.readNbt();
        return new GizmosLinkHighlightPayload(visible, root == null ? new CompoundTag() : root);
    }
}

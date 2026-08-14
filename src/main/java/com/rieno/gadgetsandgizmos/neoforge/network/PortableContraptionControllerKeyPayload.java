package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerItem;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerRuntime;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Portable Contraption Controller Key
public record PortableContraptionControllerKeyPayload(InteractionHand hand, boolean advanced, String channelId,
                                                      boolean pressed) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<PortableContraptionControllerKeyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "portable_contraption_controller_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortableContraptionControllerKeyPayload> STREAM_CODEC = StreamCodec.of(
            PortableContraptionControllerKeyPayload::encode,
            PortableContraptionControllerKeyPayload::decode);

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

    // Handle the portable contraption controller key
    public static void handle(PortableContraptionControllerKeyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ItemStack stack = ctx.player().getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof PortableContraptionControllerItem portable)
                    || portable.isAdvanced() != payload.advanced()) {
                return;
            }
            if (ctx.player() instanceof ServerPlayer player) {
                PortableContraptionControllerRuntime.handleKey(
                        player, payload.hand(), payload.advanced(), payload.channelId(), payload.pressed());
            }
        });
    }

    // Encode the portable contraption controller key
    private static void encode(RegistryFriendlyByteBuf buffer, PortableContraptionControllerKeyPayload payload) {
        buffer.writeEnum(payload.hand());
        buffer.writeBoolean(payload.advanced());
        buffer.writeUtf(payload.channelId());
        buffer.writeBoolean(payload.pressed());
    }

    // Decode the portable contraption controller key
    private static PortableContraptionControllerKeyPayload decode(RegistryFriendlyByteBuf buffer) {
        return new PortableContraptionControllerKeyPayload(
                buffer.readEnum(InteractionHand.class),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readBoolean());
    }
}

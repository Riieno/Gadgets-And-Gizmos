package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Open ACC Display Text Input
public record AccDisplayTextInputOpenPayload(
        MenuConfigTarget target,
        String nodeId,
        String interactionId,
        String prompt,
        String value
) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AccDisplayTextInputOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "acc_display_text_input_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccDisplayTextInputOpenPayload> STREAM_CODEC =
            StreamCodec.of(AccDisplayTextInputOpenPayload::encode,
                    AccDisplayTextInputOpenPayload::decode);

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

    // Handle the ACC display text input open
    public static void handle(AccDisplayTextInputOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> screens = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AccDisplayClientScreens");
                screens.getMethod("openTextInput", AccDisplayTextInputOpenPayload.class)
                        .invoke(null, payload);
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the ACC display text input open
    private static void encode(RegistryFriendlyByteBuf buffer,
                               AccDisplayTextInputOpenPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUtf(payload.nodeId(), 128);
        buffer.writeUtf(payload.interactionId(), 128);
        buffer.writeUtf(payload.prompt(), 128);
        buffer.writeUtf(payload.value(), 64);
    }

    // Decode the ACC display text input open
    private static AccDisplayTextInputOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AccDisplayTextInputOpenPayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readUtf(128), buffer.readUtf(128),
                buffer.readUtf(128), buffer.readUtf(64));
    }
}

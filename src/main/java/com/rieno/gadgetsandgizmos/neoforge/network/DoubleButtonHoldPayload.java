package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Double Button Hold
public record DoubleButtonHoldPayload(MenuConfigTarget target, DoubleButtonBlockEntity.ButtonHalf button,
                                      boolean held) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<DoubleButtonHoldPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "double_button_hold"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DoubleButtonHoldPayload> STREAM_CODEC = StreamCodec.of(
            DoubleButtonHoldPayload::encode,
            DoubleButtonHoldPayload::decode);

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

    // Handle the double button hold
    public static void handle(DoubleButtonHoldPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            DoubleButtonBlockEntity doubleButton = SimulatedHelper.findBlockEntity(
                    ctx.player().level(), payload.target().subLevelId(), payload.target().pos(),
                    DoubleButtonBlockEntity.class);
            if (doubleButton == null || !doubleButton.canPlayerUse(ctx.player())) {
                return;
            }
            doubleButton.handleHold(payload.button(), payload.held());
        });
    }

    // Encode the double button hold
    private static void encode(RegistryFriendlyByteBuf buffer, DoubleButtonHoldPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeEnum(payload.button());
        buffer.writeBoolean(payload.held());
    }

    // Decode the double button hold
    private static DoubleButtonHoldPayload decode(RegistryFriendlyByteBuf buffer) {
        return new DoubleButtonHoldPayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readEnum(DoubleButtonBlockEntity.ButtonHalf.class),
                buffer.readBoolean());
    }
}

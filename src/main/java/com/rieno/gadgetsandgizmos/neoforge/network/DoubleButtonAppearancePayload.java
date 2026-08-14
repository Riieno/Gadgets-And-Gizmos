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

// Send Double Button Appearance
public record DoubleButtonAppearancePayload(MenuConfigTarget target,
                                            boolean linkHardwareVisible) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<DoubleButtonAppearancePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "double_button_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DoubleButtonAppearancePayload> STREAM_CODEC = StreamCodec.of(
            DoubleButtonAppearancePayload::encode,
            DoubleButtonAppearancePayload::decode);

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

    // Handle the double button appearance
    public static void handle(DoubleButtonAppearancePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            DoubleButtonBlockEntity doubleButton = SimulatedHelper.findBlockEntity(
                    ctx.player().level(), payload.target().subLevelId(), payload.target().pos(),
                    DoubleButtonBlockEntity.class);
            if (doubleButton == null || !doubleButton.canPlayerUse(ctx.player())) {
                return;
            }
            doubleButton.setLinkHardwareVisible(payload.linkHardwareVisible());
        });
    }

    // Encode the double button appearance
    private static void encode(RegistryFriendlyByteBuf buffer, DoubleButtonAppearancePayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeBoolean(payload.linkHardwareVisible());
    }

    // Decode the double button appearance
    private static DoubleButtonAppearancePayload decode(RegistryFriendlyByteBuf buffer) {
        return new DoubleButtonAppearancePayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readBoolean());
    }
}

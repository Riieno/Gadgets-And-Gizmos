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

// Send Double Button Config
public record DoubleButtonConfigPayload(MenuConfigTarget target,
                                        DoubleButtonBlockEntity.ButtonHalf button,
                                        DoubleButtonBlockEntity.ResponseMode mode) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<DoubleButtonConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "double_button_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DoubleButtonConfigPayload> STREAM_CODEC = StreamCodec.of(
            DoubleButtonConfigPayload::encode,
            DoubleButtonConfigPayload::decode);

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

    // Handle the double button config
    public static void handle(DoubleButtonConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            DoubleButtonBlockEntity doubleButton = SimulatedHelper.findBlockEntity(
                    ctx.player().level(), payload.target().subLevelId(), payload.target().pos(),
                    DoubleButtonBlockEntity.class);
            if (doubleButton == null || !doubleButton.canPlayerUse(ctx.player())) {
                return;
            }
            doubleButton.setResponseMode(payload.button(), payload.mode());
        });
    }

    // Encode the double button config
    private static void encode(RegistryFriendlyByteBuf buffer, DoubleButtonConfigPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeEnum(payload.button());
        buffer.writeEnum(payload.mode());
    }

    // Decode the double button config
    private static DoubleButtonConfigPayload decode(RegistryFriendlyByteBuf buffer) {
        return new DoubleButtonConfigPayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readEnum(DoubleButtonBlockEntity.ButtonHalf.class),
                buffer.readEnum(DoubleButtonBlockEntity.ResponseMode.class));
    }
}

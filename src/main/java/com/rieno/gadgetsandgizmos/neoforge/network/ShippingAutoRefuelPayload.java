package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ShippingAutoRefuelSettings;
import com.rieno.gadgetsandgizmos.content.ShippingScheduleItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Shipping Auto Refuel
public record ShippingAutoRefuelPayload(InteractionHand hand, boolean enabled,
                                       int thresholdPercent, String dockFilter)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ShippingAutoRefuelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "shipping_auto_refuel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShippingAutoRefuelPayload> STREAM_CODEC =
            StreamCodec.of(ShippingAutoRefuelPayload::encode, ShippingAutoRefuelPayload::decode);

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

    // Handle the shipping auto refuel
    public static void handle(ShippingAutoRefuelPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof ShippingScheduleItem)) return;
            ShippingAutoRefuelSettings.write(stack, new ShippingAutoRefuelSettings(
                    payload.enabled(), payload.thresholdPercent(), payload.dockFilter()));
        });
    }

    // Encode the shipping auto refuel
    private static void encode(RegistryFriendlyByteBuf buffer, ShippingAutoRefuelPayload payload) {
        buffer.writeEnum(payload.hand());
        buffer.writeBoolean(payload.enabled());
        buffer.writeVarInt(payload.thresholdPercent());
        buffer.writeUtf(payload.dockFilter(), 64);
    }

    // Decode the shipping auto refuel
    private static ShippingAutoRefuelPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ShippingAutoRefuelPayload(buffer.readEnum(InteractionHand.class),
                buffer.readBoolean(), buffer.readVarInt(), buffer.readUtf(64));
    }
}

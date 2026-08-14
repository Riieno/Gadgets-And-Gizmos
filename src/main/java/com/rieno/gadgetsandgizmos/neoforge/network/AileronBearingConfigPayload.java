package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.AileronBearingMenu;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Aileron Bearing Config
public record AileronBearingConfigPayload(
        MenuConfigTarget target,
        double primaryMinAngle,
        double primaryMaxAngle,
        double secondaryMinAngle,
        double secondaryMaxAngle,
        ItemStack primaryCwFirst,
        ItemStack primaryCwSecond,
        ItemStack primaryCcwFirst,
        ItemStack primaryCcwSecond,
        ItemStack secondaryCwFirst,
        ItemStack secondaryCwSecond,
        ItemStack secondaryCcwFirst,
        ItemStack secondaryCcwSecond) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AileronBearingConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "aileron_bearing_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AileronBearingConfigPayload> STREAM_CODEC =
            StreamCodec.of(AileronBearingConfigPayload::encode, AileronBearingConfigPayload::decode);

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

    // Handle the aileron bearing config
    public static void handle(AileronBearingConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AileronBearingBlockEntity bearing = MenuBackedBlockEntityResolver.resolve(
                    ctx,
                    payload.target(),
                    AileronBearingMenu.class,
                    AileronBearingBlockEntity.class);
            if (bearing == null) {
                return;
            }
            bearing.setAngleRange(BearingHead.PRIMARY,
                    payload.primaryMinAngle(), payload.primaryMaxAngle());
            bearing.setAngleRange(BearingHead.SECONDARY,
                    payload.secondaryMinAngle(), payload.secondaryMaxAngle());
            bearing.setFrequency(BearingHead.PRIMARY,
                    AileronBearingBlockEntity.ControlDirection.CW,
                    payload.primaryCwFirst(), payload.primaryCwSecond());
            bearing.setFrequency(BearingHead.PRIMARY,
                    AileronBearingBlockEntity.ControlDirection.CCW,
                    payload.primaryCcwFirst(), payload.primaryCcwSecond());
            bearing.setFrequency(BearingHead.SECONDARY,
                    AileronBearingBlockEntity.ControlDirection.CW,
                    payload.secondaryCwFirst(), payload.secondaryCwSecond());
            bearing.setFrequency(BearingHead.SECONDARY,
                    AileronBearingBlockEntity.ControlDirection.CCW,
                    payload.secondaryCcwFirst(), payload.secondaryCcwSecond());
        });
    }

    // Encode the aileron bearing config
    private static void encode(RegistryFriendlyByteBuf buffer, AileronBearingConfigPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeDouble(payload.primaryMinAngle());
        buffer.writeDouble(payload.primaryMaxAngle());
        buffer.writeDouble(payload.secondaryMinAngle());
        buffer.writeDouble(payload.secondaryMaxAngle());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.primaryCwFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.primaryCwSecond());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.primaryCcwFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.primaryCcwSecond());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.secondaryCwFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.secondaryCwSecond());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.secondaryCcwFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.secondaryCcwSecond());
    }

    // Decode the aileron bearing config
    private static AileronBearingConfigPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AileronBearingConfigPayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }
}

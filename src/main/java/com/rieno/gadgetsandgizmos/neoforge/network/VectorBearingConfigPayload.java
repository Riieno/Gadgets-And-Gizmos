package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingMenu;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Vector Bearing Config
public record VectorBearingConfigPayload(
        MenuConfigTarget target,
        VectorBearingBlockEntity.ControlMode controlMode,
        double maxTiltDegrees,
        ItemStack northFirst,
        ItemStack northSecond,
        ItemStack southFirst,
        ItemStack southSecond,
        ItemStack eastFirst,
        ItemStack eastSecond,
        ItemStack westFirst,
        ItemStack westSecond) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<VectorBearingConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "vector_bearing_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VectorBearingConfigPayload> STREAM_CODEC =
            StreamCodec.of(VectorBearingConfigPayload::encode, VectorBearingConfigPayload::decode);

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

    // Handle the vector bearing config
    public static void handle(VectorBearingConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            VectorBearingBlockEntity bearing = MenuBackedBlockEntityResolver.resolve(
                    ctx,
                    payload.target(),
                    VectorBearingMenu.class,
                    VectorBearingBlockEntity.class);
            if (bearing == null) {
                return;
            }
            bearing.setControlMode(payload.controlMode());
            bearing.setMaxTiltDegrees(payload.maxTiltDegrees());
            bearing.setFrequency(Direction.NORTH, payload.northFirst(), payload.northSecond());
            bearing.setFrequency(Direction.SOUTH, payload.southFirst(), payload.southSecond());
            bearing.setFrequency(Direction.EAST, payload.eastFirst(), payload.eastSecond());
            bearing.setFrequency(Direction.WEST, payload.westFirst(), payload.westSecond());
        });
    }

    // Encode the vector bearing config
    private static void encode(RegistryFriendlyByteBuf buffer, VectorBearingConfigPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeEnum(payload.controlMode());
        buffer.writeDouble(payload.maxTiltDegrees());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.northFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.northSecond());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.southFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.southSecond());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.eastFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.eastSecond());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.westFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.westSecond());
    }

    // Decode the vector bearing config
    private static VectorBearingConfigPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VectorBearingConfigPayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readEnum(VectorBearingBlockEntity.ControlMode.class),
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

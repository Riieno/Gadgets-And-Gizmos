package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkMenu;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;

// Send Gyroscope Link Config
public record GyroscopeLinkConfigPayload(MenuConfigTarget target, String trackingMode,
    ItemStack northFirst, ItemStack northSecond, ItemStack southFirst, ItemStack southSecond,
    ItemStack eastFirst, ItemStack eastSecond, ItemStack westFirst, ItemStack westSecond,
    String configDirection, boolean enabled, boolean redstoneEnabled,
    double sourceMinDegrees, double sourceMaxDegrees,
    double outputMin, double outputMax,
    double clampMin, double clampMax)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<GyroscopeLinkConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "gyroscope_link_config"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, GyroscopeLinkConfigPayload> STREAM_CODEC =
            new StreamCodec<RegistryFriendlyByteBuf, GyroscopeLinkConfigPayload>() {
                // Decode the gyroscope link config
                @Override
                public GyroscopeLinkConfigPayload decode(RegistryFriendlyByteBuf buf) {
                    MenuConfigTarget target = MenuConfigTarget.STREAM_CODEC.decode(buf);
                    String trackingMode = ByteBufCodecs.STRING_UTF8.decode(buf);
                    ItemStack northFirst = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    ItemStack northSecond = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    ItemStack southFirst = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    ItemStack southSecond = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    ItemStack eastFirst = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    ItemStack eastSecond = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    ItemStack westFirst = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    ItemStack westSecond = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    String configDirection = ByteBufCodecs.STRING_UTF8.decode(buf);
                    boolean enabled = buf.readBoolean();
                    boolean redstoneEnabled = buf.readBoolean();
                    double sourceMinDegrees = buf.readDouble();
                    double sourceMaxDegrees = buf.readDouble();
                    double outputMin = buf.readDouble();
                    double outputMax = buf.readDouble();
                    double clampMin = buf.readDouble();
                    double clampMax = buf.readDouble();
                    return new GyroscopeLinkConfigPayload(target, trackingMode,
                            northFirst, northSecond, southFirst, southSecond, eastFirst, eastSecond, westFirst, westSecond,
                            configDirection, enabled, redstoneEnabled,
                            sourceMinDegrees, sourceMaxDegrees, outputMin, outputMax, clampMin, clampMax);
                }

                // Encode the gyroscope link config
                @Override
                public void encode(RegistryFriendlyByteBuf buf, GyroscopeLinkConfigPayload payload) {
                    MenuConfigTarget.STREAM_CODEC.encode(buf, payload.target());
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.trackingMode());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.northFirst());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.northSecond());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.southFirst());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.southSecond());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.eastFirst());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.eastSecond());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.westFirst());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.westSecond());
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.configDirection());
                    buf.writeBoolean(payload.enabled());
                    buf.writeBoolean(payload.redstoneEnabled());
                    buf.writeDouble(payload.sourceMinDegrees());
                    buf.writeDouble(payload.sourceMaxDegrees());
                    buf.writeDouble(payload.outputMin());
                    buf.writeDouble(payload.outputMax());
                    buf.writeDouble(payload.clampMin());
                    buf.writeDouble(payload.clampMax());
                }
            };

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

    // Handle the gyroscope link config
    public static void handle(GyroscopeLinkConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            GyroscopeLinkBlockEntity gyro = MenuBackedBlockEntityResolver.resolve(
                    ctx,
                    payload.target(),
                    GyroscopeLinkMenu.class,
                    GyroscopeLinkBlockEntity.class);
            if (gyro == null) {
                return;
            }

            GyroscopeLinkBlockEntity.TrackingMode parsedMode;
            try {
                parsedMode = GyroscopeLinkBlockEntity.TrackingMode.valueOf(payload.trackingMode().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                parsedMode = GyroscopeLinkBlockEntity.TrackingMode.LIVE;
            }

            gyro.setTrackingMode(parsedMode);
            gyro.setCardinalFrequency(Direction.NORTH, payload.northFirst(), payload.northSecond());
            gyro.setCardinalFrequency(Direction.SOUTH, payload.southFirst(), payload.southSecond());
            gyro.setCardinalFrequency(Direction.EAST, payload.eastFirst(), payload.eastSecond());
            gyro.setCardinalFrequency(Direction.WEST, payload.westFirst(), payload.westSecond());
            Direction dir;
            try {
                dir = Direction.valueOf(payload.configDirection().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                dir = Direction.NORTH;
            }
            GyroscopeLinkBlockEntity.CardinalOutputConfig config = new GyroscopeLinkBlockEntity.CardinalOutputConfig();
            config.enabled = payload.enabled();
            config.redstoneEnabled = payload.redstoneEnabled();
            config.sourceMinDegrees = payload.sourceMinDegrees();
            config.sourceMaxDegrees = payload.sourceMaxDegrees();
            config.outputMin = payload.outputMin();
            config.outputMax = payload.outputMax();
            config.clampMin = payload.clampMin();
            config.clampMax = payload.clampMax();
            gyro.setCardinalOutputConfig(dir, config);
        });
    }
}

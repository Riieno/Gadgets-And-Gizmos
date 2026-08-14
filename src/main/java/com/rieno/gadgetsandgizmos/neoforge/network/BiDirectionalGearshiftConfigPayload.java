package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearshiftBlockEntity;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearshiftMenu;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Bidirectional Gearshift Config
public record BiDirectionalGearshiftConfigPayload(
        MenuConfigTarget target,
        BiDirectionalGearshiftBlockEntity.AxisControlMode primaryMode,
        BiDirectionalGearshiftBlockEntity.AxisControlMode secondaryMode,
        BiDirectionalGearshiftBlockEntity.LocalControlMode localMode) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<BiDirectionalGearshiftConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "bi_directional_gearshift_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BiDirectionalGearshiftConfigPayload> STREAM_CODEC =
            StreamCodec.of(BiDirectionalGearshiftConfigPayload::encode, BiDirectionalGearshiftConfigPayload::decode);

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

    // Handle the bi directional gearshift config
    public static void handle(BiDirectionalGearshiftConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            BiDirectionalGearshiftBlockEntity gearshift = MenuBackedBlockEntityResolver.resolve(
                    ctx,
                    payload.target(),
                    BiDirectionalGearshiftMenu.class,
                    BiDirectionalGearshiftBlockEntity.class);
            if (gearshift == null) {
                return;
            }
            gearshift.setAxisMode(BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY, payload.primaryMode());
            gearshift.setAxisMode(BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY, payload.secondaryMode());
            gearshift.setLocalMode(payload.localMode());
        });
    }

    // Encode the bi directional gearshift config
    private static void encode(RegistryFriendlyByteBuf buffer, BiDirectionalGearshiftConfigPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeEnum(payload.primaryMode());
        buffer.writeEnum(payload.secondaryMode());
        buffer.writeEnum(payload.localMode());
    }

    // Decode the bi directional gearshift config
    private static BiDirectionalGearshiftConfigPayload decode(RegistryFriendlyByteBuf buffer) {
        return new BiDirectionalGearshiftConfigPayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readEnum(BiDirectionalGearshiftBlockEntity.AxisControlMode.class),
                buffer.readEnum(BiDirectionalGearshiftBlockEntity.AxisControlMode.class),
                buffer.readEnum(BiDirectionalGearshiftBlockEntity.LocalControlMode.class));
    }
}

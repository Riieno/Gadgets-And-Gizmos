package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Advanced Controller Profiler
public record AdvancedControllerProfilerPayload(MenuConfigTarget target, int fps, long frameTimeNanos)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerProfilerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_profiler"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerProfilerPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerProfilerPayload::encode, AdvancedControllerProfilerPayload::decode);

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

    // Handle the advanced controller profiler
    public static void handle(AdvancedControllerProfilerPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AnalogueContraptionControllerBlockEntity resolved = MenuBackedBlockEntityResolver.resolve(
                    ctx, payload.target(), AdvancedContraptionControllerMenu.class,
                    AnalogueContraptionControllerBlockEntity.class);
            AdvancedContraptionControllerBlockEntity controller = payload.target().subLevelId() == null
                    && resolved instanceof AdvancedContraptionControllerBlockEntity advanced ? advanced
                    : SimulatedHelper.findBlockEntity(ctx.player().level(),
                    payload.target().subLevelId(), payload.target().pos(),
                    AdvancedContraptionControllerBlockEntity.class);
            if (controller != null) {
                controller.updateClientProfilerSample(payload.fps(), payload.frameTimeNanos());
            }
        });
    }

    // Encode the advanced controller profiler
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerProfilerPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeVarInt(payload.fps());
        buffer.writeVarLong(payload.frameTimeNanos());
    }

    // Decode the advanced controller profiler
    private static AdvancedControllerProfilerPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AdvancedControllerProfilerPayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarLong());
    }
}

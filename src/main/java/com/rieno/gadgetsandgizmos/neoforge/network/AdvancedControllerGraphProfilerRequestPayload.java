package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Watch timings only through the player's current graph editor session
public record AdvancedControllerGraphProfilerRequestPayload(int containerId, UUID sessionId,
        boolean enabled, boolean simulated) implements CustomPacketPayload{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerGraphProfilerRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_graph_profiler_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerGraphProfilerRequestPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerGraphProfilerRequestPayload::encode, AdvancedControllerGraphProfilerRequestPayload::decode);

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(AdvancedControllerGraphProfilerRequestPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof AdvancedContraptionControllerMenu menu)
                    || menu.containerId != payload.containerId()) return;
            menu.watchGraphProfiler(player, payload.sessionId(), payload.enabled(), payload.simulated());
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerGraphProfilerRequestPayload payload){
        buffer.writeVarInt(payload.containerId());
        buffer.writeUUID(payload.sessionId());
        buffer.writeBoolean(payload.enabled());
        buffer.writeBoolean(payload.simulated());
    }

    private static AdvancedControllerGraphProfilerRequestPayload decode(RegistryFriendlyByteBuf buffer){
        return new AdvancedControllerGraphProfilerRequestPayload(buffer.readVarInt(), buffer.readUUID(),
                buffer.readBoolean(), buffer.readBoolean());
    }
}

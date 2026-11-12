package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Deliver one selected profiling source to its matching editor revision
public record AdvancedControllerGraphProfilePayload(int containerId, UUID sessionId, long revision,
        boolean simulated, CompoundTag profile) implements CustomPacketPayload{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerGraphProfilePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_graph_profile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerGraphProfilePayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerGraphProfilePayload::encode, AdvancedControllerGraphProfilePayload::decode);

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public AdvancedControllerGraphProfilePayload{ profile = profile.copy(); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(AdvancedControllerGraphProfilePayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen.applyGraphProfile(payload));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerGraphProfilePayload payload){
        buffer.writeVarInt(payload.containerId());
        buffer.writeUUID(payload.sessionId());
        buffer.writeLong(payload.revision());
        buffer.writeBoolean(payload.simulated());
        GraphNbtPayloadCodec.write(buffer, payload.profile());
    }

    private static AdvancedControllerGraphProfilePayload decode(RegistryFriendlyByteBuf buffer){
        return new AdvancedControllerGraphProfilePayload(buffer.readVarInt(), buffer.readUUID(), buffer.readLong(),
                buffer.readBoolean(), GraphNbtPayloadCodec.read(buffer));
    }
}

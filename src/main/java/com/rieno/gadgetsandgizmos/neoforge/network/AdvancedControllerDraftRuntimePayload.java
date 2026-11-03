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

// Deliver one draft session's ordered changes only to its editor
public record AdvancedControllerDraftRuntimePayload(int containerId, UUID sessionId, long revision,
        boolean accepted, CompoundTag changes) implements CustomPacketPayload{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerDraftRuntimePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_draft_runtime"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerDraftRuntimePayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerDraftRuntimePayload::encode, AdvancedControllerDraftRuntimePayload::decode);

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public AdvancedControllerDraftRuntimePayload{ changes = changes.copy(); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(AdvancedControllerDraftRuntimePayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen
                .applyDraftRuntime(payload));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerDraftRuntimePayload payload){
        buffer.writeVarInt(payload.containerId());
        buffer.writeUUID(payload.sessionId());
        buffer.writeLong(payload.revision());
        buffer.writeBoolean(payload.accepted());
        GraphNbtPayloadCodec.write(buffer, payload.changes());
    }

    private static AdvancedControllerDraftRuntimePayload decode(RegistryFriendlyByteBuf buffer){
        return new AdvancedControllerDraftRuntimePayload(buffer.readVarInt(), buffer.readUUID(),
                buffer.readLong(), buffer.readBoolean(), GraphNbtPayloadCodec.read(buffer));
    }
}

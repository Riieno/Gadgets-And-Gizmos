package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// Send ordered HUD changes through an optional channel with periodic complete baselines
public record AdvancedControllerRuntimeDeltaPayload(AdvancedControllerRuntimePayload update,
        UUID streamId, long baseSequence, long sequence, boolean full,
        Set<String> removedInputs, Set<String> removedOutputs) implements CustomPacketPayload{
    public static final Type<AdvancedControllerRuntimeDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_runtime_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerRuntimeDeltaPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerRuntimeDeltaPayload::encode, AdvancedControllerRuntimeDeltaPayload::decode);

    // Detach removal keys before queuing network encoding
    public AdvancedControllerRuntimeDeltaPayload{
        removedInputs = Set.copyOf(removedInputs);
        removedOutputs = Set.copyOf(removedOutputs);
    }

    // Identify the optional HUD update channel
    @Override
    public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    // Apply HUD changes on the client game thread
    public static void handle(AdvancedControllerRuntimeDeltaPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler
                .applyAdvancedRuntimeDelta(payload));
    }

    // Preserve the full runtime payload codec inside the optional update envelope
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerRuntimeDeltaPayload payload){
        AdvancedControllerRuntimePayload.STREAM_CODEC.encode(buffer, payload.update());
        buffer.writeUUID(payload.streamId());
        buffer.writeLong(payload.baseSequence());
        buffer.writeLong(payload.sequence());
        buffer.writeBoolean(payload.full());
        writeKeys(buffer, payload.removedInputs());
        writeKeys(buffer, payload.removedOutputs());
    }

    // Read the update envelope without changing the existing full snapshot format
    private static AdvancedControllerRuntimeDeltaPayload decode(RegistryFriendlyByteBuf buffer){
        var update = AdvancedControllerRuntimePayload.STREAM_CODEC.decode(buffer);
        return new AdvancedControllerRuntimeDeltaPayload(update, buffer.readUUID(), buffer.readLong(),
                buffer.readLong(), buffer.readBoolean(), readKeys(buffer), readKeys(buffer));
    }

    // Write removed presentation ports
    private static void writeKeys(RegistryFriendlyByteBuf buffer, Set<String> keys){
        buffer.writeVarInt(keys.size());
        keys.forEach(buffer::writeUtf);
    }

    // Bound removed ports before allocating their decoded collection
    private static Set<String> readKeys(RegistryFriendlyByteBuf buffer){
        int count = buffer.readVarInt();
        if(count < 0 || count > 65536) throw new IllegalArgumentException("Invalid HUD removal count");
        Set<String> keys = new LinkedHashSet<>();
        for(int idx = 0; idx < count; idx++) keys.add(buffer.readUtf());
        return keys;
    }
}

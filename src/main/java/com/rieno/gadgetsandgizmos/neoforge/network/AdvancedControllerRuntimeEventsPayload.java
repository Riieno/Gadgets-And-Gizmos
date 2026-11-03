package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Deliver ordered runtime events and changed observer metadata
public record AdvancedControllerRuntimeEventsPayload(MenuConfigTarget target, int containerId, long base,
        CompoundTag pairs, CompoundTag changes, CompoundTag metadata) implements CustomPacketPayload{
    public static final Type<AdvancedControllerRuntimeEventsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_runtime_events"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerRuntimeEventsPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerRuntimeEventsPayload::encode, AdvancedControllerRuntimeEventsPayload::decode);

    public AdvancedControllerRuntimeEventsPayload{ pairs = pairs.copy(); changes = changes.copy(); metadata = metadata.copy(); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(AdvancedControllerRuntimeEventsPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler
                .applyAdvancedRuntimeEvents(payload));
    }
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerRuntimeEventsPayload payload){
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeVarInt(payload.containerId());
        buffer.writeLong(payload.base());
        GraphNbtPayloadCodec.write(buffer, payload.pairs());
        GraphNbtPayloadCodec.write(buffer, payload.changes());
        GraphNbtPayloadCodec.write(buffer, payload.metadata());
    }
    private static AdvancedControllerRuntimeEventsPayload decode(RegistryFriendlyByteBuf buffer){
        return new AdvancedControllerRuntimeEventsPayload(MenuConfigTarget.STREAM_CODEC.decode(buffer), buffer.readVarInt(),
                buffer.readLong(), GraphNbtPayloadCodec.read(buffer), GraphNbtPayloadCodec.read(buffer), GraphNbtPayloadCodec.read(buffer));
    }
}

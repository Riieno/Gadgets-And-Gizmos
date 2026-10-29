package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.control.InputCaptureState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;

// Send the server's evaluated capture requests to the matching controller interaction
public record AdvancedControllerMouseCapturePayload(AdvancedControllerMouseInputPayload.Target target,
        BlockPos pos, UUID subLevelId, InteractionHand hand, Map<String, String> requests) implements CustomPacketPayload{
    public static final Type<AdvancedControllerMouseCapturePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_mouse_capture"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerMouseCapturePayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerMouseCapturePayload::encode, AdvancedControllerMouseCapturePayload::decode);

    public AdvancedControllerMouseCapturePayload{
        pos = pos.immutable();
        requests = Map.copyOf(requests);
    }

    public AdvancedControllerMouseCapturePayload withRequests(Map<String, String> val){
        return new AdvancedControllerMouseCapturePayload(target, pos, subLevelId, hand, val);
    }

    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(AdvancedControllerMouseCapturePayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler
                .applyMouseCapture(payload));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerMouseCapturePayload payload){
        buffer.writeEnum(payload.target());
        buffer.writeBlockPos(payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if(payload.subLevelId() != null) buffer.writeUUID(payload.subLevelId());
        buffer.writeEnum(payload.hand());
        var state = new InputCaptureState();
        state.replace(payload.requests());
        GraphNbtPayloadCodec.write(buffer, state.write());
    }

    private static AdvancedControllerMouseCapturePayload decode(RegistryFriendlyByteBuf buffer){
        var target = buffer.readEnum(AdvancedControllerMouseInputPayload.Target.class);
        var pos = buffer.readBlockPos();
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        var hand = buffer.readEnum(InteractionHand.class);
        var state = new InputCaptureState();
        state.read(GraphNbtPayloadCodec.read(buffer));
        return new AdvancedControllerMouseCapturePayload(target, pos, subLevelId, hand, state.requests());
    }
}

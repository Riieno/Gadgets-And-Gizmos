package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send control identity only; the server resolves the ray and the control value
public record AccDisplayWidgetInputPayload(MenuConfigTarget target, String nodeId, String interactionId,
                                            boolean drag) implements CustomPacketPayload{
    public static final Type<AccDisplayWidgetInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "acc_display_widget_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccDisplayWidgetInputPayload> STREAM_CODEC =
            StreamCodec.of(AccDisplayWidgetInputPayload::encode, AccDisplayWidgetInputPayload::decode);
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(AccDisplayWidgetInputPayload payload, IPayloadContext context){
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            var display = SimulatedHelper.findBlockEntity(player.level(), payload.target().subLevelId(),
                    payload.target().pos(), AccDisplayBlockEntity.class);
            if(display != null) display.interactHologram(player, payload.nodeId(), payload.interactionId(), payload.drag());
        });
    }
    private static void encode(RegistryFriendlyByteBuf buffer, AccDisplayWidgetInputPayload payload){
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUtf(payload.nodeId(), 128); buffer.writeUtf(payload.interactionId(), 128); buffer.writeBoolean(payload.drag());
    }
    private static AccDisplayWidgetInputPayload decode(RegistryFriendlyByteBuf buffer){
        return new AccDisplayWidgetInputPayload(MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readUtf(128), buffer.readUtf(128), buffer.readBoolean());
    }
}

package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.lib.display.DisplayTextScale;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Change linked text size without selecting a different display source
public record AccDisplayTextScalePayload(MenuConfigTarget target, double scale) implements CustomPacketPayload{
    public static final Type<AccDisplayTextScalePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "acc_display_text_scale"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccDisplayTextScalePayload> STREAM_CODEC =
            StreamCodec.of(AccDisplayTextScalePayload::encode, AccDisplayTextScalePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type(){
        return TYPE;
    }

    public static void handle(AccDisplayTextScalePayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player) || payload.target() == null) return;
            AccDisplayBlockEntity display = SimulatedHelper.findBlockEntity(
                    player.level(), payload.target().subLevelId(), payload.target().pos(), AccDisplayBlockEntity.class);
            if(display != null) display.setLinkedTextScale(player, payload.scale());
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AccDisplayTextScalePayload payload){
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeDouble(DisplayTextScale.normalize(payload.scale()));
    }

    private static AccDisplayTextScalePayload decode(RegistryFriendlyByteBuf buffer){
        return new AccDisplayTextScalePayload(MenuConfigTarget.STREAM_CODEC.decode(buffer),
                DisplayTextScale.normalize(buffer.readDouble()));
    }
}

package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Request takeover through the nearby display's live camera selection and access checks
public record AccDisplayCameraTakeoverPayload(MenuConfigTarget target, CompoundTag source) implements CustomPacketPayload{
    public static final Type<AccDisplayCameraTakeoverPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "acc_display_camera_takeover"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccDisplayCameraTakeoverPayload> STREAM_CODEC =
            StreamCodec.of(AccDisplayCameraTakeoverPayload::encode, AccDisplayCameraTakeoverPayload::decode);

    public AccDisplayCameraTakeoverPayload{ source = source.copy(); }
    @Override public CompoundTag source(){ return source.copy(); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(AccDisplayCameraTakeoverPayload msg, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player) || msg.target() == null) return;
            var be = SimulatedHelper.findLoadedBlockEntityExact(player.level(), msg.target().subLevelId(), msg.target().pos());
            if(be instanceof AccDisplayBlockEntity display) display.takeCameraControl(player, ViewReference.fromTag(msg.source));
        });
    }
    private static void encode(RegistryFriendlyByteBuf buf, AccDisplayCameraTakeoverPayload msg){
        MenuConfigTarget.STREAM_CODEC.encode(buf, msg.target); buf.writeNbt(msg.source);
    }
    private static AccDisplayCameraTakeoverPayload decode(RegistryFriendlyByteBuf buf){
        MenuConfigTarget target = MenuConfigTarget.STREAM_CODEC.decode(buf);
        CompoundTag source = buf.readNbt();
        return new AccDisplayCameraTakeoverPayload(target, source == null ? new CompoundTag() : source);
    }
}

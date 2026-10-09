package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlInput;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send projected controls through the display that authorizes the selected camera
public record AccDisplayCameraControlPayload(MenuConfigTarget target, CompoundTag source, CompoundTag input)
        implements CustomPacketPayload{
    public static final Type<AccDisplayCameraControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "acc_display_camera_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccDisplayCameraControlPayload> STREAM_CODEC =
            StreamCodec.of(AccDisplayCameraControlPayload::encode, AccDisplayCameraControlPayload::decode);
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(AccDisplayCameraControlPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player) || payload.target() == null
                    || payload.source() == null || payload.input() == null) return;
            var be = SimulatedHelper.findLoadedBlockEntityExact(player.level(), payload.target().subLevelId(), payload.target().pos());
            if(be instanceof AccDisplayBlockEntity display){
                display.submitCameraControls(player, ViewReference.fromTag(payload.source()), ViewControlInput.fromTag(payload.input()));
            }
        });
    }
    private static void encode(RegistryFriendlyByteBuf buffer, AccDisplayCameraControlPayload payload){
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeNbt(payload.source());
        buffer.writeNbt(payload.input());
    }
    private static AccDisplayCameraControlPayload decode(RegistryFriendlyByteBuf buffer){
        return new AccDisplayCameraControlPayload(MenuConfigTarget.STREAM_CODEC.decode(buffer), buffer.readNbt(), buffer.readNbt());
    }
}

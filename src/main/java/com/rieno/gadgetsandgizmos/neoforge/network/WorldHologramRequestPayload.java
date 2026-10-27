package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi;
import com.rieno.gadgetsandgizmos.content.WorldHologramProjectorBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Request terrain only for a nearby physical projector
public record WorldHologramRequestPayload(MenuConfigTarget target) implements CustomPacketPayload{
    public static final Type<WorldHologramRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "world_hologram_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldHologramRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> MenuConfigTarget.STREAM_CODEC.encode(buf, val.target),
            buf -> new WorldHologramRequestPayload(MenuConfigTarget.STREAM_CODEC.decode(buf)));
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(WorldHologramRequestPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            var blockEntity = BlockEntityLookupApi.findLoadedExact(ctx.player().level(), payload.target.subLevelId(),
                    payload.target.pos());
            if(blockEntity instanceof WorldHologramProjectorBlockEntity projector && ctx.player() instanceof ServerPlayer player) projector.watch(player);
        });
    }
}

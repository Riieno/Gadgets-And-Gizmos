package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi;
import com.rieno.gadgetsandgizmos.content.WorldHologramProjectorBlockEntity;
import com.rieno.gadgetsandgizmos.content.WorldHologramSettings;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Apply bounded projector controls on the owning server thread
public record WorldHologramConfigPayload(MenuConfigTarget target, WorldHologramSettings settings) implements CustomPacketPayload{
    public static final Type<WorldHologramConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "world_hologram_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldHologramConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> { MenuConfigTarget.STREAM_CODEC.encode(buf, val.target); val.settings.encode(buf); },
            buf -> new WorldHologramConfigPayload(MenuConfigTarget.STREAM_CODEC.decode(buf), WorldHologramSettings.decode(buf)));
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(WorldHologramConfigPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            var blockEntity = BlockEntityLookupApi.findLoadedExact(ctx.player().level(), payload.target.subLevelId(),
                    payload.target.pos());
            if(blockEntity instanceof WorldHologramProjectorBlockEntity projector) projector.configure(ctx.player(), payload.settings);
        });
    }
}

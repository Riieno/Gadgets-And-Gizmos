package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Return one storage result without replacing graph state
public record AdvancedControllerFilesPayload(MenuConfigTarget target, CompoundTag result) implements CustomPacketPayload{
    public AdvancedControllerFilesPayload{ result = result.copy(); }
    public static final Type<AdvancedControllerFilesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_files"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerFilesPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> { MenuConfigTarget.STREAM_CODEC.encode(buf, payload.target()); GraphNbtPayloadCodec.write(buf, payload.result()); },
            buf -> new AdvancedControllerFilesPayload(MenuConfigTarget.STREAM_CODEC.decode(buf), GraphNbtPayloadCodec.read(buf)));
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(AdvancedControllerFilesPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen.applyFiles(payload));
    }
}

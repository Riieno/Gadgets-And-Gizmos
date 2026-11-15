package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Return requested target frames without changing graph source or topology
public record AdvancedControllerTargetPreviewPayload(MenuConfigTarget target, CompoundTag previews) implements CustomPacketPayload{
    public static final Type<AdvancedControllerTargetPreviewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_target_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerTargetPreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> { MenuConfigTarget.STREAM_CODEC.encode(buf, payload.target()); GraphNbtPayloadCodec.write(buf, payload.previews()); },
            buf -> new AdvancedControllerTargetPreviewPayload(MenuConfigTarget.STREAM_CODEC.decode(buf), GraphNbtPayloadCodec.read(buf)));
    public AdvancedControllerTargetPreviewPayload{ previews = previews.copy(); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(AdvancedControllerTargetPreviewPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen.applyTargetPreviews(payload));
    }
}

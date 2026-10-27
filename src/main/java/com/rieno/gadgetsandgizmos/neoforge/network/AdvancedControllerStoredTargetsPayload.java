package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Return the authoritative controller collection before opening or refreshing its target editor
public record AdvancedControllerStoredTargetsPayload(MenuConfigTarget target, CompoundTag snapshot) implements CustomPacketPayload{
    public static final Type<AdvancedControllerStoredTargetsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_stored_targets"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerStoredTargetsPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerStoredTargetsPayload::encode, AdvancedControllerStoredTargetsPayload::decode);

    public AdvancedControllerStoredTargetsPayload{
        snapshot = snapshot == null ? new CompoundTag() : snapshot.copy();
    }

    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(AdvancedControllerStoredTargetsPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            try{
                Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen")
                        .getMethod("applyStoredTargets", MenuConfigTarget.class, CompoundTag.class)
                        .invoke(null, payload.target(), payload.snapshot());
            }catch(ReflectiveOperationException ignored){}
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerStoredTargetsPayload payload){
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        GraphNbtPayloadCodec.write(buffer, payload.snapshot());
    }

    private static AdvancedControllerStoredTargetsPayload decode(RegistryFriendlyByteBuf buffer){
        return new AdvancedControllerStoredTargetsPayload(MenuConfigTarget.STREAM_CODEC.decode(buffer),
                GraphNbtPayloadCodec.read(buffer));
    }
}

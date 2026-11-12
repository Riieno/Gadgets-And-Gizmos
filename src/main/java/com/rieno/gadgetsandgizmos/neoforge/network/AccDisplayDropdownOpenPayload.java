package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Open the selected display control using its resolved style and option labels
public record AccDisplayDropdownOpenPayload(MenuConfigTarget target, String nodeId, String interactionId,
        CompoundTag element) implements CustomPacketPayload{
    public static final Type<AccDisplayDropdownOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "acc_display_dropdown_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccDisplayDropdownOpenPayload> STREAM_CODEC =
            StreamCodec.of(AccDisplayDropdownOpenPayload::encode, AccDisplayDropdownOpenPayload::decode);
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(AccDisplayDropdownOpenPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            try{
                Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.AccDisplayClientScreens")
                        .getMethod("openDropdown", AccDisplayDropdownOpenPayload.class).invoke(null, payload);
            }catch(ReflectiveOperationException err){ throw new IllegalStateException("Cannot open display dropdown", err); }
        });
    }

    private static void encode(RegistryFriendlyByteBuf buf, AccDisplayDropdownOpenPayload val){
        MenuConfigTarget.STREAM_CODEC.encode(buf, val.target());
        buf.writeUtf(val.nodeId(), 128); buf.writeUtf(val.interactionId(), 128); buf.writeNbt(val.element());
    }
    private static AccDisplayDropdownOpenPayload decode(RegistryFriendlyByteBuf buf){
        return new AccDisplayDropdownOpenPayload(MenuConfigTarget.STREAM_CODEC.decode(buf), buf.readUtf(128), buf.readUtf(128), buf.readNbt());
    }
}

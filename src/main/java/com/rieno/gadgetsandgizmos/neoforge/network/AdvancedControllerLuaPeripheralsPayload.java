package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Supply loaded target aliases and their actual CC methods to the controller editor
public record AdvancedControllerLuaPeripheralsPayload(MenuConfigTarget target, CompoundTag catalog) implements CustomPacketPayload{
    public AdvancedControllerLuaPeripheralsPayload{ catalog = catalog.copy(); }
    public static final Type<AdvancedControllerLuaPeripheralsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_lua_peripherals"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerLuaPeripheralsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> { MenuConfigTarget.STREAM_CODEC.encode(buf, payload.target()); GraphNbtPayloadCodec.write(buf, payload.catalog()); },
            buf -> new AdvancedControllerLuaPeripheralsPayload(MenuConfigTarget.STREAM_CODEC.decode(buf), GraphNbtPayloadCodec.read(buf)));
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(AdvancedControllerLuaPeripheralsPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen.applyLuaPeripherals(payload));
    }
}

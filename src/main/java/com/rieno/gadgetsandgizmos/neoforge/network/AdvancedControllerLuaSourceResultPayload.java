package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.script.LuaScripts;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Acknowledge one source write without returning or replacing its graph
public record AdvancedControllerLuaSourceResultPayload(MenuConfigTarget target, long requestId, boolean success, String message, String source)
        implements CustomPacketPayload{
    public static final Type<AdvancedControllerLuaSourceResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_lua_source_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerLuaSourceResultPayload> STREAM_CODEC = StreamCodec.of(
            AdvancedControllerLuaSourceResultPayload::encode, AdvancedControllerLuaSourceResultPayload::decode);
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(AdvancedControllerLuaSourceResultPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen.applyLuaSourceResult(payload));
    }
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerLuaSourceResultPayload payload){
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target()); buffer.writeLong(payload.requestId()); buffer.writeBoolean(payload.success());
        buffer.writeUtf(payload.message(), 1024); buffer.writeBoolean(payload.source() != null);
        if(payload.source() != null) buffer.writeUtf(payload.source(), LuaScripts.MAX_SOURCE_LENGTH);
    }
    private static AdvancedControllerLuaSourceResultPayload decode(RegistryFriendlyByteBuf buffer){
        return new AdvancedControllerLuaSourceResultPayload(MenuConfigTarget.STREAM_CODEC.decode(buffer), buffer.readLong(), buffer.readBoolean(),
                buffer.readUtf(1024), buffer.readBoolean() ? buffer.readUtf(LuaScripts.MAX_SOURCE_LENGTH) : null);
    }
}

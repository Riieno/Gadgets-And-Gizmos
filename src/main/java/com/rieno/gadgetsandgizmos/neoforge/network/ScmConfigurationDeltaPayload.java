package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send only changed SCM configuration, telemetry and permission fields to the owning menu
public record ScmConfigurationDeltaPayload(MenuConfigTarget target, int containerId, CompoundTag update) implements CustomPacketPayload{
    public static final Type<ScmConfigurationDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "scm_configuration_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScmConfigurationDeltaPayload> STREAM_CODEC =
            StreamCodec.of(ScmConfigurationDeltaPayload::encode, ScmConfigurationDeltaPayload::decode);

    public ScmConfigurationDeltaPayload{ update = update.copy(); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(ScmConfigurationDeltaPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen.applyScmConfigurationDelta(payload));
    }
    private static void encode(RegistryFriendlyByteBuf buffer, ScmConfigurationDeltaPayload payload){
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeVarInt(payload.containerId());
        GraphNbtPayloadCodec.write(buffer, payload.update());
    }
    private static ScmConfigurationDeltaPayload decode(RegistryFriendlyByteBuf buffer){
        return new ScmConfigurationDeltaPayload(MenuConfigTarget.STREAM_CODEC.decode(buffer), buffer.readVarInt(), GraphNbtPayloadCodec.read(buffer));
    }
}

package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.content.ThrusterDyeMenu;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ThrusterDyePresetPayload(int menuId, ColorEffectPreset preset) implements CustomPacketPayload{
    public static final Type<ThrusterDyePresetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createthrusters", "thruster_dye_preset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThrusterDyePresetPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> { buffer.writeVarInt(packet.menuId); buffer.writeNbt(packet.preset.write()); },
            buffer -> new ThrusterDyePresetPayload(buffer.readVarInt(), ColorEffectPreset.read(buffer.readNbt())));
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(ThrusterDyePresetPayload packet, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(ctx.player().containerMenu instanceof ThrusterDyeMenu menu && menu.containerId == packet.menuId) menu.confirm(packet.preset);
        });
    }
}

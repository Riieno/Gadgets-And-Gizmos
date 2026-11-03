package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppIconStyle;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppIconStyles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

// Synchronize configured app icons independently of installed apps and open tablet screens
public record DiagnosticTabletAppIconsPayload(Map<ResourceLocation, TabletAppIconStyle> icons) implements CustomPacketPayload{
    public static final Type<DiagnosticTabletAppIconsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "diagnostic_tablet_app_icons"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiagnosticTabletAppIconsPayload> STREAM_CODEC =
            StreamCodec.of(DiagnosticTabletAppIconsPayload::encode, DiagnosticTabletAppIconsPayload::decode);

    public DiagnosticTabletAppIconsPayload{ icons = Map.copyOf(icons); }

    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(DiagnosticTabletAppIconsPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> TabletAppIconStyles.apply(payload.icons()));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, DiagnosticTabletAppIconsPayload payload){
        CompoundTag tag = new CompoundTag();
        payload.icons().forEach((id, style) -> tag.put(id.toString(), style.toTag()));
        buffer.writeNbt(tag);
    }

    private static DiagnosticTabletAppIconsPayload decode(RegistryFriendlyByteBuf buffer){
        CompoundTag tag = buffer.readNbt();
        Map<ResourceLocation, TabletAppIconStyle> res = new HashMap<>();
        if(tag != null) for(String key : tag.getAllKeys()){
            ResourceLocation id = ResourceLocation.tryParse(key);
            if(id != null) res.put(id, TabletAppIconStyle.fromTag(tag.getCompound(key)));
        }
        return new DiagnosticTabletAppIconsPayload(res);
    }
}

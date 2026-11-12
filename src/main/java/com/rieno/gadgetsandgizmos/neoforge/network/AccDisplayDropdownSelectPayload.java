package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudDropdown;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send the chosen identity so the server resolves its current bound value
public record AccDisplayDropdownSelectPayload(MenuConfigTarget target, String nodeId, String interactionId,
        String key) implements CustomPacketPayload{
    public static final Type<AccDisplayDropdownSelectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "acc_display_dropdown_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccDisplayDropdownSelectPayload> STREAM_CODEC =
            StreamCodec.of(AccDisplayDropdownSelectPayload::encode, AccDisplayDropdownSelectPayload::decode);
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(AccDisplayDropdownSelectPayload val, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player)) return;
            var display = SimulatedHelper.findBlockEntity(player.level(), val.target().subLevelId(), val.target().pos(), AccDisplayBlockEntity.class);
            if(display != null) display.submitDropdownInput(player, val.nodeId(), val.interactionId(), val.key());
        });
    }
    private static void encode(RegistryFriendlyByteBuf buf, AccDisplayDropdownSelectPayload val){
        MenuConfigTarget.STREAM_CODEC.encode(buf, val.target());
        buf.writeUtf(val.nodeId(), 128); buf.writeUtf(val.interactionId(), 128); buf.writeUtf(val.key(), AdvancedHudDropdown.MAX_KEY_LENGTH);
    }
    private static AccDisplayDropdownSelectPayload decode(RegistryFriendlyByteBuf buf){
        return new AccDisplayDropdownSelectPayload(MenuConfigTarget.STREAM_CODEC.decode(buf), buf.readUtf(128), buf.readUtf(128),
                buf.readUtf(AdvancedHudDropdown.MAX_KEY_LENGTH));
    }
}

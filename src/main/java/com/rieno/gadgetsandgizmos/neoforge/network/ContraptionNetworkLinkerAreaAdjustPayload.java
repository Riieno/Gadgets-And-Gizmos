package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerTracker;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Apply one scroll step to a saved worker area on the authoritative linker
public record ContraptionNetworkLinkerAreaAdjustPayload(InteractionHand hand, UUID areaId,
                                                        Direction face, int blocks) implements CustomPacketPayload{
    public static final Type<ContraptionNetworkLinkerAreaAdjustPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "contraption_network_linker_area_adjust"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionNetworkLinkerAreaAdjustPayload> STREAM_CODEC =
            StreamCodec.of(ContraptionNetworkLinkerAreaAdjustPayload::encode,
                    ContraptionNetworkLinkerAreaAdjustPayload::decode);

    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(ContraptionNetworkLinkerAreaAdjustPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player) || Math.abs(payload.blocks()) != 1) return;
            ItemStack stack = player.getItemInHand(payload.hand());
            if(!(stack.getItem() instanceof ContraptionNetworkLinkerItem)
                    || ContraptionNetworkLinkerData.getEditMode(stack) != ContraptionNetworkLinkerData.LinkMode.SCM
                    || ContraptionNetworkLinkerData.getTargetMode(stack) != ContraptionNetworkLinkerData.TargetMode.AREA) return;
            var tracker = ContraptionNetworkLinkerTracker.get(player.getServer());
            if(!tracker.canMutateLinker(player.serverLevel(), stack)) return;
            if(ContraptionNetworkLinkerData.moveAreaFace(stack, payload.areaId(), payload.face(), payload.blocks())){
                tracker.observeLinker(player.serverLevel(), stack);
                ContraptionNetworkLinkerData.ensureClientSnapshot(stack);
            }
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ContraptionNetworkLinkerAreaAdjustPayload payload){
        buffer.writeEnum(payload.hand());
        buffer.writeUUID(payload.areaId());
        buffer.writeEnum(payload.face());
        buffer.writeInt(payload.blocks());
    }

    private static ContraptionNetworkLinkerAreaAdjustPayload decode(RegistryFriendlyByteBuf buffer){
        return new ContraptionNetworkLinkerAreaAdjustPayload(buffer.readEnum(InteractionHand.class),
                buffer.readUUID(), buffer.readEnum(Direction.class), buffer.readInt());
    }
}

package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerTracker;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Open or save a completed SCM area configuration
public record ContraptionNetworkLinkerAreaConfigPayload(InteractionHand hand, UUID areaId,
                                                         String label, String recipeId, boolean open)
        implements CustomPacketPayload{
    public static final Type<ContraptionNetworkLinkerAreaConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "linker_area_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionNetworkLinkerAreaConfigPayload> STREAM_CODEC =
            StreamCodec.of(ContraptionNetworkLinkerAreaConfigPayload::encode,
                    ContraptionNetworkLinkerAreaConfigPayload::decode);

    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(ContraptionNetworkLinkerAreaConfigPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(ctx.player() instanceof ServerPlayer player){
                if(payload.open()) return;
                var stack = player.getItemInHand(payload.hand());
                if(!(stack.getItem() instanceof ContraptionNetworkLinkerItem)
                        || !ContraptionNetworkLinkerTracker.get(player.getServer())
                        .canMutateLinker(player.serverLevel(), stack)) return;
                if(ContraptionNetworkLinkerData.configureArea(stack, payload.areaId(),
                        payload.label(), payload.recipeId())){
                    ContraptionNetworkLinkerTracker.get(player.getServer())
                            .observeLinker(player.serverLevel(), stack);
                    ContraptionNetworkLinkerData.ensureClientSnapshot(stack);
                }
                return;
            }
            if(!payload.open()) return;
            try{
                Class<?> screens = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.ContraptionNetworkLinkerClient");
                screens.getMethod("openAreaConfiguration", ContraptionNetworkLinkerAreaConfigPayload.class)
                        .invoke(null, payload);
            }catch(ReflectiveOperationException ignored){}
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ContraptionNetworkLinkerAreaConfigPayload payload){
        buffer.writeEnum(payload.hand());
        buffer.writeUUID(payload.areaId());
        buffer.writeUtf(payload.label(), 64);
        buffer.writeUtf(payload.recipeId(), 128);
        buffer.writeBoolean(payload.open());
    }

    private static ContraptionNetworkLinkerAreaConfigPayload decode(RegistryFriendlyByteBuf buffer){
        return new ContraptionNetworkLinkerAreaConfigPayload(buffer.readEnum(InteractionHand.class),
                buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(128), buffer.readBoolean());
    }
}

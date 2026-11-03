package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.probe.LoadedBlockAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Recover a recreated client cache only while its player remains an authorized observer
public record AdvancedControllerRuntimeResyncPayload(MenuConfigTarget target, int containerId) implements CustomPacketPayload{
    public static final Type<AdvancedControllerRuntimeResyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_runtime_resync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerRuntimeResyncPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerRuntimeResyncPayload::encode, AdvancedControllerRuntimeResyncPayload::decode);

    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
    public static void handle(AdvancedControllerRuntimeResyncPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player)) return;
            AdvancedContraptionControllerBlockEntity controller;
            if(payload.containerId() >= 0){
                if(!(player.containerMenu instanceof AdvancedContraptionControllerMenu menu)
                        || menu.containerId != payload.containerId() || !menu.stillValid(player)
                        || !java.util.Objects.equals(menu.getContentPos(), payload.target().pos())
                        || !java.util.Objects.equals(menu.getContentSubLevelId(), payload.target().subLevelId())) return;
                controller = menu.getMenuConfigTargetBlockEntity();
            }else{
                controller = LoadedBlockAccess.blockEntity(player.level(), payload.target().subLevelId(),
                        payload.target().pos(), AdvancedContraptionControllerBlockEntity.class);
            }
            if(controller != null) controller.resyncGraphRuntime(player, payload.containerId());
        });
    }
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerRuntimeResyncPayload payload){
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeVarInt(payload.containerId());
    }
    private static AdvancedControllerRuntimeResyncPayload decode(RegistryFriendlyByteBuf buffer){
        return new AdvancedControllerRuntimeResyncPayload(MenuConfigTarget.STREAM_CODEC.decode(buffer), buffer.readVarInt());
    }
}

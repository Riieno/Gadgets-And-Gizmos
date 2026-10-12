package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.PhysicsGogglesItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Save one wheel adjustment on the sender's equipped goggles
public record PhysicsGogglesHeightPayload(int containerId, int direction) implements CustomPacketPayload{
    public static final Type<PhysicsGogglesHeightPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "physics_goggles_height"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhysicsGogglesHeightPayload> STREAM_CODEC =
            StreamCodec.of(PhysicsGogglesHeightPayload::encode, PhysicsGogglesHeightPayload::decode);

    // Identify the serverbound adjustment
    @Override
    public Type<? extends CustomPacketPayload> type(){
        return TYPE;
    }

    // Validate the current menu and item before changing saved equipment data
    public static void handle(PhysicsGogglesHeightPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player)) return;
            if(payload.direction != -1 && payload.direction != 1) return;
            if(player.containerMenu.containerId != payload.containerId) return;
            ItemStack goggles = player.getItemBySlot(EquipmentSlot.HEAD);
            if(!PhysicsGogglesItem.canAdjustHeight(goggles)) return;
            if(!PhysicsGogglesItem.HEIGHT_OFFSET.adjustSteps(goggles, payload.direction)) return;
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if(player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            // Normal equipment updates replicate the changed custom NBT to tracking players
        });
    }

    // Encode the menu and signed wheel step
    private static void encode(RegistryFriendlyByteBuf buffer, PhysicsGogglesHeightPayload payload){
        buffer.writeVarInt(payload.containerId);
        buffer.writeByte(payload.direction);
    }

    // Decode the wheel request without accepting client item data
    private static PhysicsGogglesHeightPayload decode(RegistryFriendlyByteBuf buffer){
        return new PhysicsGogglesHeightPayload(buffer.readVarInt(), buffer.readByte());
    }
}

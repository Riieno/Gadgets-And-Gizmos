package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Thruster Slot
public record ThrusterSlotPayload(BlockPos pos, int slot) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ThrusterSlotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "thruster_slot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThrusterSlotPayload> STREAM_CODEC = StreamCodec.of(
            ThrusterSlotPayload::encode,
            ThrusterSlotPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the thruster slot
    public static void handle(ThrusterSlotPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player.level().getBlockEntity(payload.pos()) instanceof ThrusterBlockEntity thruster)) {
                return;
            }
            int slot = payload.slot();
            if (slot < 0 || slot >= 4) return;

            ItemStack current = thruster.getItemInventory().getStackInSlot(slot);
            if (!current.isEmpty()) {

                thruster.extractInventorySlot(slot, player);
            } else {

                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack candidate = player.getInventory().getItem(i);
                    if (!candidate.isEmpty() && thruster.getItemInventory().isItemValid(slot, candidate)) {
                        if (thruster.insertInventorySlot(slot, candidate)) {
                            if (!player.getAbilities().instabuild) {
                                candidate.shrink(1);
                            }
                        }
                        break;
                    }
                }
            }
        });
    }

    // Encode the thruster slot
    private static void encode(RegistryFriendlyByteBuf buffer, ThrusterSlotPayload payload) {
        buffer.writeBlockPos(payload.pos());
        buffer.writeInt(payload.slot());
    }

    // Decode the thruster slot
    private static ThrusterSlotPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ThrusterSlotPayload(buffer.readBlockPos(), buffer.readInt());
    }
}

package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Analogue Contraption Controller Ghost Slots
public record AnalogueContraptionControllerGhostSlotsPayload(String channelId, ItemStack first, ItemStack second,
                                                            ItemStack inputFirst, ItemStack inputSecond)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AnalogueContraptionControllerGhostSlotsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "analogue_contraption_controller_ghost_slots"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnalogueContraptionControllerGhostSlotsPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, AnalogueContraptionControllerGhostSlotsPayload::channelId,
            ItemStack.OPTIONAL_STREAM_CODEC, AnalogueContraptionControllerGhostSlotsPayload::first,
            ItemStack.OPTIONAL_STREAM_CODEC, AnalogueContraptionControllerGhostSlotsPayload::second,
            ItemStack.OPTIONAL_STREAM_CODEC, AnalogueContraptionControllerGhostSlotsPayload::inputFirst,
            ItemStack.OPTIONAL_STREAM_CODEC, AnalogueContraptionControllerGhostSlotsPayload::inputSecond,
            AnalogueContraptionControllerGhostSlotsPayload::new);

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

    // Handle the analogue contraption controller ghost slots
    public static void handle(AnalogueContraptionControllerGhostSlotsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof AnalogueContraptionControllerMenu menu) {
                menu.setCurrentChannelId(payload.channelId());
                menu.ghostInventory.setStackInSlot(0, payload.first());
                menu.ghostInventory.setStackInSlot(1, payload.second());
                menu.ghostInventory.setStackInSlot(2, payload.inputFirst());
                menu.ghostInventory.setStackInSlot(3, payload.inputSecond());
                for (int slot = 36; slot < 40 && slot < menu.slots.size(); slot++) {
                    menu.getSlot(slot).setChanged();
                }
            }
        });
    }
}

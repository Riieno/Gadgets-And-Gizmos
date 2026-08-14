package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Analogue Joystick Ghost Slots
public record AnalogueJoystickGhostSlotsPayload(ItemStack first, ItemStack second) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AnalogueJoystickGhostSlotsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "analogue_joystick_ghost_slots"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnalogueJoystickGhostSlotsPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, AnalogueJoystickGhostSlotsPayload::first,
            ItemStack.OPTIONAL_STREAM_CODEC, AnalogueJoystickGhostSlotsPayload::second,
            AnalogueJoystickGhostSlotsPayload::new);

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

    // Handle the analogue joystick ghost slots
    public static void handle(AnalogueJoystickGhostSlotsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof AnalogueJoystickMenu menu) {
                menu.ghostInventory.setStackInSlot(0, payload.first());
                menu.ghostInventory.setStackInSlot(1, payload.second());
                menu.getSlot(AnalogueJoystickMenu.GHOST_SLOT_START_INDEX).setChanged();
                menu.getSlot(AnalogueJoystickMenu.GHOST_SLOT_START_INDEX + 1).setChanged();
            }
        });
    }
}
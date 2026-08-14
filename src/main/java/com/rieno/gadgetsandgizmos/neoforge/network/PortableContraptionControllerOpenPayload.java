package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerItem;
import com.rieno.gadgetsandgizmos.content.PortableAdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.PortableAnalogueContraptionControllerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Open Portable Contraption Controller
public record PortableContraptionControllerOpenPayload(InteractionHand hand,
                                                       boolean advanced) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<PortableContraptionControllerOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "portable_contraption_controller_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortableContraptionControllerOpenPayload> STREAM_CODEC =
            StreamCodec.of(PortableContraptionControllerOpenPayload::encode, PortableContraptionControllerOpenPayload::decode);

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

    // Handle the portable contraption controller open
    public static void handle(PortableContraptionControllerOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ItemStack stack = ctx.player().getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof PortableContraptionControllerItem portable)
                    || portable.isAdvanced() != payload.advanced()) {
                return;
            }
            if (isMatchingPortableMenuOpen(ctx.player().containerMenu, payload.advanced())) {
                return;
            }
            portable.openPortableMenu(ctx.player().level(), ctx.player(), payload.hand());
        });
    }

    // Check if the matching portable menu is open
    private static boolean isMatchingPortableMenuOpen(net.minecraft.world.inventory.AbstractContainerMenu menu,
                                                      boolean advanced) {
        return advanced
                ? menu instanceof PortableAdvancedContraptionControllerMenu
                : menu instanceof PortableAnalogueContraptionControllerMenu;
    }

    // Encode the portable contraption controller open
    private static void encode(RegistryFriendlyByteBuf buffer, PortableContraptionControllerOpenPayload payload) {
        buffer.writeEnum(payload.hand());
        buffer.writeBoolean(payload.advanced());
    }

    // Decode the portable contraption controller open
    private static PortableContraptionControllerOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        return new PortableContraptionControllerOpenPayload(
                buffer.readEnum(InteractionHand.class),
                buffer.readBoolean());
    }
}

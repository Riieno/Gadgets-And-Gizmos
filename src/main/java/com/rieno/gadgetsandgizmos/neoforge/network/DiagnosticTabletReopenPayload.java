package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Diagnostic Tablet Reopen
public record DiagnosticTabletReopenPayload(InteractionHand hand) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<DiagnosticTabletReopenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "diagnostic_tablet_reopen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiagnosticTabletReopenPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> buffer.writeEnum(payload.hand()),
                    buffer -> new DiagnosticTabletReopenPayload(buffer.readEnum(InteractionHand.class)));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet reopen
    public DiagnosticTabletReopenPayload {
        hand = hand == null ? InteractionHand.MAIN_HAND : hand;
    }

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

    // Handle the diagnostic tablet reopen
    public static void handle(DiagnosticTabletReopenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> minecraftType = Class.forName("net.minecraft.client.Minecraft");
                Object minecraft = minecraftType.getMethod("getInstance").invoke(null);
                Object player = minecraftType.getField("player").get(minecraft);
                if (player == null) return;
                Object stack = player.getClass().getMethod("getItemInHand", InteractionHand.class)
                        .invoke(player, payload.hand());
                Class<?> screen = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletScreen");
                screen.getMethod("openItem", InteractionHand.class,
                                Class.forName("net.minecraft.world.item.ItemStack"))
                        .invoke(null, payload.hand(), stack);
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        });
    }
}

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
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Send Diagnostic Tablet Reopen
public record DiagnosticTabletReopenPayload(InteractionHand hand, @Nullable UUID sourceTabletId,
                                             @Nullable UUID schematicSourceId) implements CustomPacketPayload{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<DiagnosticTabletReopenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "diagnostic_tablet_reopen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiagnosticTabletReopenPayload> STREAM_CODEC =
            StreamCodec.of(DiagnosticTabletReopenPayload::encode, DiagnosticTabletReopenPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet reopen
    public DiagnosticTabletReopenPayload{
        hand = hand == null ? InteractionHand.MAIN_HAND : hand;
    }

    // Preserve ordinary reopen requests without a selected schematic source
    public DiagnosticTabletReopenPayload(InteractionHand hand){
        this(hand, null, null);
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
                if(payload.schematicSourceId() != null){
                    screen.getMethod("openSchematicSave", InteractionHand.class,
                                    Class.forName("net.minecraft.world.item.ItemStack"), UUID.class, UUID.class)
                            .invoke(null, payload.hand(), stack, payload.sourceTabletId(), payload.schematicSourceId());
                }else screen.getMethod("openItem", InteractionHand.class,
                                Class.forName("net.minecraft.world.item.ItemStack"))
                        .invoke(null, payload.hand(), stack);
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        });
    }

    // Carry the selected source together with the tablet that initiated Reader mode
    private static void encode(RegistryFriendlyByteBuf buffer, DiagnosticTabletReopenPayload payload){
        buffer.writeEnum(payload.hand());
        buffer.writeBoolean(payload.sourceTabletId() != null);
        if(payload.sourceTabletId() != null) buffer.writeUUID(payload.sourceTabletId());
        buffer.writeBoolean(payload.schematicSourceId() != null);
        if(payload.schematicSourceId() != null) buffer.writeUUID(payload.schematicSourceId());
    }

    // Read ordinary reopen requests and schematic naming requests through the same channel
    private static DiagnosticTabletReopenPayload decode(RegistryFriendlyByteBuf buffer){
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        UUID tabletId = buffer.readBoolean() ? buffer.readUUID() : null;
        UUID sourceId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new DiagnosticTabletReopenPayload(hand, tabletId, sourceId);
    }
}

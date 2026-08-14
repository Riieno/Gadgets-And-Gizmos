package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletRemoteSessions;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.UUID;

// Send Advanced Controller Mouse Input
public record AdvancedControllerMouseInputPayload(Target target, BlockPos pos, UUID subLevelId,
                                                  InteractionHand hand, String input, double value,
                                                  boolean active) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerMouseInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_mouse_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerMouseInputPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerMouseInputPayload::encode,
                    AdvancedControllerMouseInputPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the block
    public static AdvancedControllerMouseInputPayload block(BlockPos pos, UUID subLevelId, String input,
                                                             double val, boolean active) {
        return new AdvancedControllerMouseInputPayload(
                Target.BLOCK, pos, subLevelId, InteractionHand.MAIN_HAND, input, val, active);
    }

    // Get the portable
    public static AdvancedControllerMouseInputPayload portable(InteractionHand hand, String input,
                                                                double val, boolean active) {
        return new AdvancedControllerMouseInputPayload(
                Target.PORTABLE, BlockPos.ZERO, null, hand, input, val, active);
    }

    // Get the lectern
    public static AdvancedControllerMouseInputPayload lectern(BlockPos pos, String input,
                                                               double val, boolean active) {
        return new AdvancedControllerMouseInputPayload(
                Target.LECTERN, pos, null, InteractionHand.MAIN_HAND, input, val, active);
    }

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

    // Handle the advanced controller mouse input
    public static void handle(AdvancedControllerMouseInputPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            switch (payload.target()) {
                case BLOCK -> {
                    AdvancedContraptionControllerBlockEntity controller = controllerFromOpenMenu(player, payload);
                    boolean openMenuTarget = controller != null;
                    if (controller == null) {
                        controller = SimulatedHelper.findBlockEntity(player.level(), payload.subLevelId(),
                                payload.pos(), AdvancedContraptionControllerBlockEntity.class);
                    }
                    if (controller != null && (openMenuTarget || controller.canPlayerUse(player)
                            || DiagnosticTabletRemoteSessions.isInteractionAuthorized(
                            player, payload.pos(), payload.subLevelId()))) {
                        controller.handleMouseInput(payload.input(), payload.value(), payload.active());
                    }
                }
                case PORTABLE -> PortableContraptionControllerRuntime.handleMouseInput(
                        player, payload.hand(), payload.input(), payload.value(), payload.active());
                case LECTERN -> PortableContraptionControllerRuntime.handleLecternMouseInput(
                        player, payload.pos(), payload.input(), payload.value(), payload.active());
            }
        });
    }

    // Get the controller from open menu
    private static AdvancedContraptionControllerBlockEntity controllerFromOpenMenu(
            ServerPlayer player, AdvancedControllerMouseInputPayload payload) {
        if (!(player.containerMenu instanceof AdvancedContraptionControllerMenu menu)
                || !Objects.equals(menu.getContentPos(), payload.pos())
                || !Objects.equals(menu.getContentSubLevelId(), payload.subLevelId())) {
            return null;
        }
        return menu.getMenuConfigTargetBlockEntity();
    }

    // Encode the advanced controller mouse input
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerMouseInputPayload payload) {
        buffer.writeEnum(payload.target());
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) buffer.writeUUID(payload.subLevelId());
        buffer.writeEnum(payload.hand());
        buffer.writeUtf(payload.input(), 32);
        buffer.writeDouble(payload.value());
        buffer.writeBoolean(payload.active());
    }

    // Decode the advanced controller mouse input
    private static AdvancedControllerMouseInputPayload decode(RegistryFriendlyByteBuf buffer) {
        Target target = buffer.readEnum(Target.class);
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        return new AdvancedControllerMouseInputPayload(
                target, pos, subLevelId, hand, buffer.readUtf(32), buffer.readDouble(), buffer.readBoolean());
    }

    // Define the target values
    public enum Target {
        BLOCK,
        PORTABLE,
        LECTERN
    }
}

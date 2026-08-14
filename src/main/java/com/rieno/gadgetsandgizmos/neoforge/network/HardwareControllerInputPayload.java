package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletRemoteSessions;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerRuntime;
import com.rieno.gadgetsandgizmos.lib.control.hardware.HardwareControllerBindings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Send Hardware Controller Input
public record HardwareControllerInputPayload(Target target, BlockPos pos, UUID subLevelId,
                                             InteractionHand hand, boolean advanced,
                                             Map<String, Double> values) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_VALUES = 96;
    public static final Type<HardwareControllerInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "hardware_controller_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HardwareControllerInputPayload> STREAM_CODEC =
            StreamCodec.of(HardwareControllerInputPayload::encode, HardwareControllerInputPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the hardware controller input
    public HardwareControllerInputPayload {
        Map<String, Double> sanitized = new LinkedHashMap<>();
        if (values != null) {
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                if (sanitized.size() >= MAX_VALUES
                        || !HardwareControllerBindings.isHardwareBinding(entry.getKey())) {
                    continue;
                }
                double val = entry.getValue() == null || !Double.isFinite(entry.getValue())
                        ? 0.0D : Math.max(-1.0D, Math.min(1.0D, entry.getValue()));
                sanitized.put(entry.getKey(), val);
            }
        }
        values = Collections.unmodifiableMap(sanitized);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the block
    public static HardwareControllerInputPayload block(BlockPos pos, UUID subLevelId,
                                                        Map<String, Double> values) {
        return new HardwareControllerInputPayload(Target.BLOCK, pos, subLevelId,
                InteractionHand.MAIN_HAND, false, values);
    }

    // Get the portable
    public static HardwareControllerInputPayload portable(InteractionHand hand, boolean advanced,
                                                           Map<String, Double> values) {
        return new HardwareControllerInputPayload(Target.PORTABLE, BlockPos.ZERO, null,
                hand, advanced, values);
    }

    // Get the lectern
    public static HardwareControllerInputPayload lectern(BlockPos pos, boolean advanced,
                                                          Map<String, Double> values) {
        return new HardwareControllerInputPayload(Target.LECTERN, pos, null,
                InteractionHand.MAIN_HAND, advanced, values);
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

    // Handle the hardware controller input
    public static void handle(HardwareControllerInputPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            switch (payload.target()) {
                case BLOCK -> {
                    AnalogueContraptionControllerBlockEntity controller = SimulatedHelper.findBlockEntity(
                            player.level(), payload.subLevelId(), payload.pos(),
                            AnalogueContraptionControllerBlockEntity.class);
                    boolean openMenuTarget = player.containerMenu instanceof AnalogueContraptionControllerMenu menu
                            && Objects.equals(menu.getContentPos(), payload.pos())
                            && Objects.equals(menu.getContentSubLevelId(), payload.subLevelId());
                    if (controller != null && (openMenuTarget || controller.canPlayerUse(player)
                            || DiagnosticTabletRemoteSessions.isInteractionAuthorized(
                            player, payload.pos(), payload.subLevelId()))) {
                        controller.applyHardwareControllerInput(payload.values());
                    }
                }
                case PORTABLE -> PortableContraptionControllerRuntime.handleHardwareInput(
                        player, payload.hand(), payload.advanced(), payload.values());
                case LECTERN -> PortableContraptionControllerRuntime.handleLecternHardwareInput(
                        player, payload.pos(), payload.advanced(), payload.values());
            }
        });
    }

    // Encode the hardware controller input
    private static void encode(RegistryFriendlyByteBuf buffer, HardwareControllerInputPayload payload) {
        buffer.writeEnum(payload.target());
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeEnum(payload.hand());
        buffer.writeBoolean(payload.advanced());
        buffer.writeVarInt(payload.values().size());
        payload.values().forEach((id, val) -> {
            buffer.writeUtf(id, 64);
            buffer.writeDouble(val);
        });
    }

    // Decode the hardware controller input
    private static HardwareControllerInputPayload decode(RegistryFriendlyByteBuf buffer) {
        Target target = buffer.readEnum(Target.class);
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        boolean advanced = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_VALUES) {
            throw new IllegalArgumentException("Invalid hardware controller value count: " + count);
        }
        Map<String, Double> values = new LinkedHashMap<>();
        for (int idx = 0; idx < count; idx++) {
            values.put(buffer.readUtf(64), buffer.readDouble());
        }
        return new HardwareControllerInputPayload(target, pos, subLevelId, hand, advanced, values);
    }

    // Define the target values
    public enum Target {
        BLOCK,
        PORTABLE,
        LECTERN
    }
}

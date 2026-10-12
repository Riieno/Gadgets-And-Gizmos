package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.control.PlayerActionInputs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Synchronize the owning player's configured escape action
public record ServerboundPlayerActionInputPacket(int held) implements CustomPacketPayload {
    public static final int CLAW_ESCAPE = 1;
    public static final Type<ServerboundPlayerActionInputPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "player_action_input"));
    public static final StreamCodec<ByteBuf, ServerboundPlayerActionInputPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundPlayerActionInputPacket::held, ServerboundPlayerActionInputPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundPlayerActionInputPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlayerActionInputs.update(player, payload.held() & CLAW_ESCAPE);
            }
        });
    }
}

package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Synchronize the belt-wheel endpoint selected by its owner for the connection preview.
public record PhysicsGantryBeltWheelSelectionPayload(boolean active, long position,
                                                     @Nullable UUID subLevelId,
                                                     double fallbackWorldX, double fallbackWorldY,
                                                     double fallbackWorldZ, int maximumDistance) implements CustomPacketPayload {
    public static final Type<PhysicsGantryBeltWheelSelectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "physics_gantry_belt_wheel_selection"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,
            PhysicsGantryBeltWheelSelectionPayload> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
                    PhysicsGantryBeltWheelSelectionPayload::encode,
                    PhysicsGantryBeltWheelSelectionPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PhysicsGantryBeltWheelSelectionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> client = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.PhysicsGantryBeltWheelConnectionParticles");
                client.getMethod("handle", PhysicsGantryBeltWheelSelectionPayload.class).invoke(null, payload);
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PhysicsGantryBeltWheelSelectionPayload payload) {
        buffer.writeBoolean(payload.active());
        buffer.writeLong(payload.position());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeDouble(payload.fallbackWorldX());
        buffer.writeDouble(payload.fallbackWorldY());
        buffer.writeDouble(payload.fallbackWorldZ());
        buffer.writeVarInt(payload.maximumDistance());
    }

    private static PhysicsGantryBeltWheelSelectionPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        long position = buffer.readLong();
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new PhysicsGantryBeltWheelSelectionPayload(active, position, subLevelId,
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt());
    }
}

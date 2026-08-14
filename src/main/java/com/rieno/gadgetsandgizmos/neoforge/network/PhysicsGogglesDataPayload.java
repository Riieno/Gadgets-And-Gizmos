package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.client.render.DiagramDataSource;
import com.rieno.gadgetsandgizmos.lib.client.render.DiagramForceData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Send Physics Goggles Data
public record PhysicsGogglesDataPayload(UUID subLevelId, double mass, Bounds bounds,
                                         List<ForceVector> forces) implements CustomPacketPayload, DiagramDataSource {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<PhysicsGogglesDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "physics_goggles_data"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, PhysicsGogglesDataPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(PhysicsGogglesDataPayload::encode, PhysicsGogglesDataPayload::decode);

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

    // Handle the physics goggles data
    public static void handle(PhysicsGogglesDataPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {

            try {
                Class<?> clientClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.CTPhysicsGogglesClient");
                clientClass.getMethod("applyData", PhysicsGogglesDataPayload.class).invoke(null, payload);
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the physics goggles data
    private static void encode(RegistryFriendlyByteBuf buffer, PhysicsGogglesDataPayload payload) {
        buffer.writeUUID(payload.subLevelId());
        buffer.writeDouble(payload.mass());
        buffer.writeDouble(payload.bounds().minX());
        buffer.writeDouble(payload.bounds().minY());
        buffer.writeDouble(payload.bounds().minZ());
        buffer.writeDouble(payload.bounds().maxX());
        buffer.writeDouble(payload.bounds().maxY());
        buffer.writeDouble(payload.bounds().maxZ());
        buffer.writeVarInt(payload.forces().size());
        for (ForceVector force : payload.forces()) {
            ResourceLocation.STREAM_CODEC.encode(buffer, force.groupId());
            buffer.writeInt(force.color());
            buffer.writeDouble(force.pointX());
            buffer.writeDouble(force.pointY());
            buffer.writeDouble(force.pointZ());
            buffer.writeDouble(force.forceX());
            buffer.writeDouble(force.forceY());
            buffer.writeDouble(force.forceZ());
        }
    }

    // Decode the physics goggles data
    private static PhysicsGogglesDataPayload decode(RegistryFriendlyByteBuf buffer) {
        UUID subLevelId = buffer.readUUID();
        double mass = buffer.readDouble();
        Bounds bounds = new Bounds(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble());
        int size = buffer.readVarInt();
        List<ForceVector> forces = new ArrayList<>(size);
        for (int idx = 0; idx < size; idx++) {
            forces.add(new ForceVector(
                    ResourceLocation.STREAM_CODEC.decode(buffer),
                    buffer.readInt(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()));
        }
        return new PhysicsGogglesDataPayload(subLevelId, mass, bounds, forces);
    }

    // Store the bounds
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        // Create empty bounds
        public static Bounds empty() {
            return new Bounds(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        // Check if the bounds are empty
        public boolean emptyBounds() {
            return minX == maxX && minY == maxY && minZ == maxZ;
        }
    }

    // Store the force vector
    public record ForceVector(ResourceLocation groupId, int color,
                              double pointX, double pointY, double pointZ,
                              double forceX, double forceY, double forceZ) implements DiagramForceData {
        // Get the magnitude
        public double magnitude() {
            return Math.sqrt(forceX * forceX + forceY * forceY + forceZ * forceZ);
        }
    }
}

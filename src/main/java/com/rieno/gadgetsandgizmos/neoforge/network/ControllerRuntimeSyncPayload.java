package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlTelemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;
import java.util.UUID;

// Sync Controller Runtime
public record ControllerRuntimeSyncPayload(BlockPos pos, UUID subLevelId,
                                           int outputSignal, ScmControlTelemetry scmTelemetry)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ControllerRuntimeSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "controller_runtime_sync"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ControllerRuntimeSyncPayload>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
            ControllerRuntimeSyncPayload::encode,
            ControllerRuntimeSyncPayload::decode);

    public ControllerRuntimeSyncPayload(BlockPos pos, UUID subLevelId, int outputSignal) {
        this(pos, subLevelId, outputSignal, null);
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

    // Handle the controller runtime sync
    public static void handle(ControllerRuntimeSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler");
                Method method = handlerClass.getMethod("applyRuntimeSignal", ControllerRuntimeSyncPayload.class);
                method.invoke(null, payload);
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the controller runtime sync
    private static void encode(RegistryFriendlyByteBuf buffer, ControllerRuntimeSyncPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeVarInt(payload.outputSignal());
        buffer.writeBoolean(payload.scmTelemetry() != null);
        if (payload.scmTelemetry() != null) {
            ScmControlTelemetry telemetry = payload.scmTelemetry();
            buffer.writeBoolean(telemetry.active());
            writeAxes(buffer, telemetry.correction());
            writeAxes(buffer, telemetry.demand());
            buffer.writeDouble(telemetry.driveDirection());
            buffer.writeDouble(telemetry.acceleration());
            buffer.writeDouble(telemetry.deceleration());
            buffer.writeDouble(telemetry.brake());
        }
    }

    // Decode the controller runtime sync
    private static ControllerRuntimeSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        int outputSignal = buffer.readVarInt();
        if (!buffer.readBoolean()) {
            return new ControllerRuntimeSyncPayload(pos, subLevelId, outputSignal);
        }
        boolean active = buffer.readBoolean();
        ScmControlTelemetry.Axes correction = readAxes(buffer);
        ScmControlTelemetry.Axes demand = readAxes(buffer);
        ScmControlTelemetry telemetry = new ScmControlTelemetry(active, correction, demand,
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        return new ControllerRuntimeSyncPayload(pos, subLevelId, outputSignal, telemetry);
    }

    private static void writeAxes(RegistryFriendlyByteBuf buffer, ScmControlTelemetry.Axes axes) {
        buffer.writeDouble(axes.pitch());
        buffer.writeDouble(axes.yaw());
        buffer.writeDouble(axes.roll());
        buffer.writeDouble(axes.throttle());
        buffer.writeDouble(axes.strafe());
        buffer.writeDouble(axes.lift());
    }

    private static ScmControlTelemetry.Axes readAxes(RegistryFriendlyByteBuf buffer) {
        return new ScmControlTelemetry.Axes(buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}

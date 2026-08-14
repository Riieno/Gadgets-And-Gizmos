package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Send Diagnostic Tablet App Snapshot
public record DiagnosticTabletAppSnapshotPayload(ResourceLocation appId,
                                                 boolean placedSource,
                                                 @Nullable UUID sourceTabletId,
                                                 @Nullable UUID sourceSubLevelId,
                                                 @Nullable BlockPos sourceBlockPos,
                                                 CompoundTag data)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<DiagnosticTabletAppSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "diagnostic_tablet_app_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiagnosticTabletAppSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(DiagnosticTabletAppSnapshotPayload::encode,
                    DiagnosticTabletAppSnapshotPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet app snapshot
    public DiagnosticTabletAppSnapshotPayload {
        sourceBlockPos = sourceBlockPos == null ? null : sourceBlockPos.immutable();
        data = data == null ? new CompoundTag() : data.copy();
    }

    // Initialize the diagnostic tablet app snapshot
    public DiagnosticTabletAppSnapshotPayload(ResourceLocation appId, CompoundTag data) {
        this(appId, false, null, null, null, data);
    }

    // Initialize the diagnostic tablet app snapshot
    public DiagnosticTabletAppSnapshotPayload(ResourceLocation appId,
                                              TabletActionContext ctx,
                                              CompoundTag data) {
        this(appId, ctx.placedSource(), ctx.sourceTabletId(),
                ctx.sourceSubLevelId(), ctx.sourceBlockPos(), data);
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

    // Handle the diagnostic tablet app snapshot
    public static void handle(DiagnosticTabletAppSnapshotPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> cache = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletClientAppData");
                cache.getMethod("apply", ResourceLocation.class, boolean.class,
                                UUID.class, UUID.class, BlockPos.class, CompoundTag.class)
                        .invoke(null, payload.appId(), payload.placedSource(),
                                payload.sourceTabletId(), payload.sourceSubLevelId(),
                                payload.sourceBlockPos(), payload.data());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the diagnostic tablet app snapshot
    private static void encode(RegistryFriendlyByteBuf buffer,
                               DiagnosticTabletAppSnapshotPayload payload) {
        buffer.writeResourceLocation(payload.appId());
        buffer.writeBoolean(payload.placedSource());
        writeNullableUuid(buffer, payload.sourceTabletId());
        writeNullableUuid(buffer, payload.sourceSubLevelId());
        buffer.writeBoolean(payload.sourceBlockPos() != null);
        if (payload.sourceBlockPos() != null) buffer.writeBlockPos(payload.sourceBlockPos());
        buffer.writeNbt(payload.data());
    }

    // Decode the diagnostic tablet app snapshot
    private static DiagnosticTabletAppSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation appId = buffer.readResourceLocation();
        boolean placedSource = buffer.readBoolean();
        UUID sourceTabletId = readNullableUuid(buffer);
        UUID sourceSubLevelId = readNullableUuid(buffer);
        BlockPos sourceBlockPos = buffer.readBoolean() ? buffer.readBlockPos() : null;
        CompoundTag data = buffer.readNbt();
        return new DiagnosticTabletAppSnapshotPayload(appId, placedSource,
                sourceTabletId, sourceSubLevelId, sourceBlockPos,
                data == null ? new CompoundTag() : data);
    }

    // Write the nullable UUID
    private static void writeNullableUuid(RegistryFriendlyByteBuf buffer, @Nullable UUID val) {
        buffer.writeBoolean(val != null);
        if (val != null) buffer.writeUUID(val);
    }

    // Read the nullable UUID
    private static @Nullable UUID readNullableUuid(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }
}

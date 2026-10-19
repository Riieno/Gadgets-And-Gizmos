package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Deliver the server-authoritative SCM calibration configuration to its modal.
// The separate packet avoids treating the graph snapshot as a control surface.
public record ScmConfigurationSnapshotPayload(
        BlockPos pos, UUID subLevelId, UUID rootSubLevelId, boolean scanning, String status,
        CompoundTag profile, CompoundTag candidates
) implements CustomPacketPayload {
    public static final Type<ScmConfigurationSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "scm_configuration_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScmConfigurationSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(ScmConfigurationSnapshotPayload::encode,
                    ScmConfigurationSnapshotPayload::decode);

    public ScmConfigurationSnapshotPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        status = status == null ? "" : status;
        profile = profile == null ? new CompoundTag() : profile.copy();
        candidates = candidates == null ? new CompoundTag() : candidates.copy();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScmConfigurationSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> screen = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen");
                screen.getMethod("applyScmConfigurationSnapshot", BlockPos.class, UUID.class,
                                UUID.class, boolean.class, String.class, CompoundTag.class, CompoundTag.class)
                        .invoke(null, payload.pos(), payload.subLevelId(), payload.rootSubLevelId(), payload.scanning(),
                                payload.status(), payload.profile(), payload.candidates());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer,
                               ScmConfigurationSnapshotPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeBoolean(payload.rootSubLevelId() != null);
        if (payload.rootSubLevelId() != null) {
            buffer.writeUUID(payload.rootSubLevelId());
        }
        buffer.writeBoolean(payload.scanning());
        buffer.writeUtf(payload.status(), 512);
        GraphNbtPayloadCodec.write(buffer, payload.profile());
        GraphNbtPayloadCodec.write(buffer, payload.candidates());
    }

    private static ScmConfigurationSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        UUID rootSubLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        boolean scanning = buffer.readBoolean();
        String status = buffer.readUtf(512);
        return new ScmConfigurationSnapshotPayload(pos, subLevelId, rootSubLevelId, scanning, status,
                GraphNbtPayloadCodec.read(buffer), GraphNbtPayloadCodec.read(buffer));
    }
}

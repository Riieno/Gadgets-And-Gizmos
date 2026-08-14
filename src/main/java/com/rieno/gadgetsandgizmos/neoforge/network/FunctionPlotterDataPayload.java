package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.NotationDraftStore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Send Function Plotter Data
public record FunctionPlotterDataPayload(
        BlockPos pos,
        UUID subLevelId,
        String action,
        boolean success,
        String message,
        List<NotationDraftStore.Summary> drafts,
        CompoundTag draft,
        CompoundTag scmModel
) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<FunctionPlotterDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "notation_plotter_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FunctionPlotterDataPayload> STREAM_CODEC =
            StreamCodec.of(FunctionPlotterDataPayload::encode, FunctionPlotterDataPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the function plotter data
    public FunctionPlotterDataPayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        action = action == null ? "" : action;
        message = message == null ? "" : message;
        drafts = drafts == null ? List.of() : List.copyOf(drafts);
        draft = draft == null ? new CompoundTag() : draft.copy();
        scmModel = scmModel == null ? new CompoundTag() : scmModel.copy();
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

    // Handle the function plotter data
    public static void handle(FunctionPlotterDataPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> screen = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.FunctionPlotterScreen");
                screen.getMethod("applyServerData", BlockPos.class, UUID.class, String.class,
                                boolean.class, String.class, List.class, CompoundTag.class, CompoundTag.class)
                        .invoke(null, payload.pos(), payload.subLevelId(), payload.action(),
                                payload.success(), payload.message(), payload.drafts(),
                                payload.draft(), payload.scmModel());
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the function plotter data
    private static void encode(RegistryFriendlyByteBuf buffer, FunctionPlotterDataPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) buffer.writeUUID(payload.subLevelId());
        buffer.writeUtf(payload.action(), 32);
        buffer.writeBoolean(payload.success());
        buffer.writeUtf(payload.message(), 512);
        int count = Math.min(NotationDraftStore.MAX_DRAFTS, payload.drafts().size());
        buffer.writeVarInt(count);
        for (int idx = 0; idx < count; idx++) {
            NotationDraftStore.Summary summary = payload.drafts().get(idx);
            buffer.writeUtf(summary.id(), 48);
            buffer.writeUtf(summary.name(), 64);
            buffer.writeLong(summary.updatedAt());
        }
        GraphNbtPayloadCodec.write(buffer, payload.draft());
        GraphNbtPayloadCodec.write(buffer, payload.scmModel());
    }

    // Decode the function plotter data
    private static FunctionPlotterDataPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        String action = buffer.readUtf(32);
        boolean success = buffer.readBoolean();
        String msg = buffer.readUtf(512);
        int count = Math.max(0, Math.min(NotationDraftStore.MAX_DRAFTS, buffer.readVarInt()));
        List<NotationDraftStore.Summary> drafts = new ArrayList<>(count);
        for (int idx = 0; idx < count; idx++) {
            drafts.add(new NotationDraftStore.Summary(
                    buffer.readUtf(48), buffer.readUtf(64), buffer.readLong()));
        }
        return new FunctionPlotterDataPayload(pos, subLevelId, action, success, msg,
                drafts, GraphNbtPayloadCodec.read(buffer), GraphNbtPayloadCodec.read(buffer));
    }
}

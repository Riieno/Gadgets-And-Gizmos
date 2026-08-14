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
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;

import java.util.UUID;

// Open Diagnostic Tablet Remote
public record DiagnosticTabletRemoteOpenPayload(String view, BlockPos controllerPos,
                                                UUID controllerSubLevelId)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<DiagnosticTabletRemoteOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "diagnostic_tablet_remote_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DiagnosticTabletRemoteOpenPayload> STREAM_CODEC =
            StreamCodec.of(DiagnosticTabletRemoteOpenPayload::encode,
                    DiagnosticTabletRemoteOpenPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet remote open
    public DiagnosticTabletRemoteOpenPayload {
        view = view == null ? "graph" : view;
        controllerPos = controllerPos == null ? BlockPos.ZERO : controllerPos.immutable();
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

    // Handle the diagnostic tablet remote open
    public static void handle(DiagnosticTabletRemoteOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if ("interact".equals(payload.view())) {
                try {
                    Class<?> handler = Class.forName(
                            "com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler");
                    handler.getMethod("startRemoteInteractMode", BlockPos.class, UUID.class)
                            .invoke(null, payload.controllerPos(), payload.controllerSubLevelId());
                } catch (ReflectiveOperationException | LinkageError ignored) {
                }
                return;
            }
            if (!"plotter".equals(payload.view())) return;
            try {
                Class<?> minecraftType = Class.forName("net.minecraft.client.Minecraft");
                Object minecraft = minecraftType.getMethod("getInstance").invoke(null);
                Object screen = minecraftType.getField("screen").get(minecraft);
                if (screen == null || !screen.getClass().getName().equals(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen")) return;
                Method open = screen.getClass().getMethod("openFunctionPlotterFromTablet");
                open.invoke(screen);
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        });
    }

    // Encode the diagnostic tablet remote open
    private static void encode(RegistryFriendlyByteBuf buffer,
                               DiagnosticTabletRemoteOpenPayload payload) {
        buffer.writeUtf(payload.view(), 24);
        buffer.writeBlockPos(payload.controllerPos());
        buffer.writeBoolean(payload.controllerSubLevelId() != null);
        if (payload.controllerSubLevelId() != null) buffer.writeUUID(payload.controllerSubLevelId());
    }

    // Decode the diagnostic tablet remote open
    private static DiagnosticTabletRemoteOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        String view = buffer.readUtf(24);
        BlockPos pos = buffer.readBlockPos();
        UUID subLevel = buffer.readBoolean() ? buffer.readUUID() : null;
        return new DiagnosticTabletRemoteOpenPayload(view, pos, subLevel);
    }
}

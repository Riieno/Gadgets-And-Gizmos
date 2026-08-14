package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.neoforge.GraphV2ThemeData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;

// Sync Graph V2 Theme
public record GraphV2ThemeSyncPayload(GraphV2ThemeData.Palette palette) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<GraphV2ThemeSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "graph_v2_theme_sync"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, GraphV2ThemeSyncPayload>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
            GraphV2ThemeSyncPayload::encode, GraphV2ThemeSyncPayload::decode);

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

    // Handle the graph V2 theme sync
    public static void handle(GraphV2ThemeSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> applyClientPalette(payload.palette()));
    }

    // Apply the client palette
    private static void applyClientPalette(GraphV2ThemeData.Palette palette) {
        try {
            Class<?> type = Class.forName(
                    "com.rieno.gadgetsandgizmos.neoforge.client.AdvancedControllerV2Theme",
                    true,
                    GraphV2ThemeSyncPayload.class.getClassLoader());
            Method method = type.getMethod("applyPalette", GraphV2ThemeData.Palette.class);
            method.invoke(null, palette);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Encode the graph V2 theme sync
    private static void encode(RegistryFriendlyByteBuf buffer, GraphV2ThemeSyncPayload payload) {
        GraphV2ThemeData.Palette palette = payload.palette();
        buffer.writeInt(palette.canvasBackground());
        buffer.writeInt(palette.canvasDot());
        buffer.writeInt(palette.titleBackground());
        buffer.writeInt(palette.panelBackground());
        buffer.writeInt(palette.panelRaised());
        buffer.writeInt(palette.panelHovered());
        buffer.writeInt(palette.panelSelected());
        buffer.writeInt(palette.border());
        buffer.writeInt(palette.borderStrong());
        buffer.writeInt(palette.borderSoft());
        buffer.writeInt(palette.primary());
        buffer.writeInt(palette.secondary());
        buffer.writeInt(palette.muted());
        buffer.writeInt(palette.accent());
        buffer.writeInt(palette.accentLight());
        buffer.writeInt(palette.accentDark());
        buffer.writeInt(palette.accentOverlay());
        buffer.writeInt(palette.primaryActionText());
        buffer.writeInt(palette.danger());
    }

    // Decode the graph V2 theme sync
    private static GraphV2ThemeSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new GraphV2ThemeSyncPayload(new GraphV2ThemeData.Palette(
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt()));
    }
}

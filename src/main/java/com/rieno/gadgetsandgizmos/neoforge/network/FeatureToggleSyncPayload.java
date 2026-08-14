package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

// Sync Feature Toggle
public record FeatureToggleSyncPayload(Map<String, Boolean> blocks,
                                       Map<String, Boolean> items,
                                       Map<String, Boolean> entities) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<FeatureToggleSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "feature_toggle_sync"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, FeatureToggleSyncPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(FeatureToggleSyncPayload::encode, FeatureToggleSyncPayload::decode);

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

    // Handle the feature toggle sync
    public static void handle(FeatureToggleSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            CTFeatureToggles.applyServerOverrides(
                    payload.blocks(),
                    payload.items(),
                    payload.entities());
            refreshClientVisibility();
        });
    }

    // Refresh the client visibility
    private static void refreshClientVisibility() {
        try {
            Class<?> type = Class.forName(
                    "com.rieno.gadgetsandgizmos.neoforge.client.CTClientFeatureVisibility",
                    true,
                    FeatureToggleSyncPayload.class.getClassLoader());
            Method method = type.getMethod("refresh");
            method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Encode the feature toggle sync
    private static void encode(RegistryFriendlyByteBuf buffer, FeatureToggleSyncPayload payload) {
        writeMap(buffer, payload.blocks());
        writeMap(buffer, payload.items());
        writeMap(buffer, payload.entities());
    }

    // Decode the feature toggle sync
    private static FeatureToggleSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        return new FeatureToggleSyncPayload(
                readMap(buffer),
                readMap(buffer),
                readMap(buffer));
    }

    // Write the map
    private static void writeMap(RegistryFriendlyByteBuf buffer, Map<String, Boolean> values) {
        buffer.writeVarInt(values.size());
        for (Map.Entry<String, Boolean> entry : values.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeBoolean(Boolean.TRUE.equals(entry.getValue()));
        }
    }

    // Read the map
    private static Map<String, Boolean> readMap(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<String, Boolean> values = new LinkedHashMap<>(size);
        for (int idx = 0; idx < size; idx++) {
            values.put(buffer.readUtf(), buffer.readBoolean());
        }
        return values;
    }
}

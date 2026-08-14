package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphLiveValue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Send Advanced Controller Runtime
public record AdvancedControllerRuntimePayload(BlockPos pos, UUID subLevelId, int containerId,
                                               Map<UUID, String> gogglesTrackerPairs,
                                               Map<String, AdvancedGraphLiveValue> liveInputs,
                                               Map<String, AdvancedGraphLiveValue> liveOutputs,
                                               Map<String, Long> executionPulses)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerRuntimePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_runtime"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerRuntimePayload>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
            AdvancedControllerRuntimePayload::encode,
            AdvancedControllerRuntimePayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced controller runtime
    public AdvancedControllerRuntimePayload {
        gogglesTrackerPairs = gogglesTrackerPairs == null
                ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(gogglesTrackerPairs));
        liveInputs = liveInputs == null ? Map.of() : Map.copyOf(liveInputs);
        liveOutputs = liveOutputs == null ? Map.of() : Map.copyOf(liveOutputs);
        executionPulses = executionPulses == null ? Map.of() : Map.copyOf(executionPulses);
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

    // Handle the advanced controller runtime
    public static void handle(AdvancedControllerRuntimePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName(
                        "com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler");
                Method method = handlerClass.getMethod(
                        "applyAdvancedRuntime", AdvancedControllerRuntimePayload.class);
                method.invoke(null, payload);
            } catch (ReflectiveOperationException ignored) {
            }
        });
    }

    // Encode the advanced controller runtime
    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerRuntimePayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarInt(payload.gogglesTrackerPairs().size());
        for (Map.Entry<UUID, String> entry : payload.gogglesTrackerPairs().entrySet()) {
            buffer.writeUUID(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
        writeValues(buffer, payload.liveInputs());
        writeValues(buffer, payload.liveOutputs());
        buffer.writeVarInt(payload.executionPulses().size());
        for (Map.Entry<String, Long> entry : payload.executionPulses().entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeLong(entry.getValue());
        }
    }

    // Decode the advanced controller runtime
    private static AdvancedControllerRuntimePayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        int containerId = buffer.readVarInt();
        int gogglesPairCount = buffer.readVarInt();
        Map<UUID, String> gogglesTrackerPairs = new LinkedHashMap<>(gogglesPairCount);
        for (int idx = 0; idx < gogglesPairCount; idx++) {
            gogglesTrackerPairs.put(buffer.readUUID(), buffer.readUtf());
        }
        Map<String, AdvancedGraphLiveValue> liveInputs = readValues(buffer);
        Map<String, AdvancedGraphLiveValue> liveOutputs = readValues(buffer);
        int pulseCount = buffer.readVarInt();
        Map<String, Long> executionPulses = new LinkedHashMap<>(pulseCount);
        for (int idx = 0; idx < pulseCount; idx++) {
            executionPulses.put(buffer.readUtf(), buffer.readLong());
        }
        return new AdvancedControllerRuntimePayload(
                pos, subLevelId, containerId, gogglesTrackerPairs,
                liveInputs, liveOutputs, executionPulses);
    }

    // Write the values
    private static void writeValues(RegistryFriendlyByteBuf buffer,
                                    Map<String, AdvancedGraphLiveValue> values) {
        buffer.writeVarInt(values.size());
        for (Map.Entry<String, AdvancedGraphLiveValue> entry : values.entrySet()) {
            AdvancedGraphLiveValue val = entry.getValue();
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(val.type());
            switch (val.type()) {
                case "number" -> buffer.writeDouble(val.numberValue());
                case "boolean" -> buffer.writeBoolean(val.booleanValue());
                default -> buffer.writeUtf(val.textValue());
            }
            buffer.writeVarInt(val.entryCount());
            buffer.writeVarInt(val.entryTypes().size());
            for (Map.Entry<String, String> typeEntry : val.entryTypes().entrySet()) {
                buffer.writeUtf(typeEntry.getKey());
                buffer.writeUtf(typeEntry.getValue());
            }
        }
    }

    // Read the values
    private static Map<String, AdvancedGraphLiveValue> readValues(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<String, AdvancedGraphLiveValue> values = new LinkedHashMap<>(size);
        for (int idx = 0; idx < size; idx++) {
            String key = buffer.readUtf();
            String type = buffer.readUtf();
            AdvancedGraphLiveValue val = switch (type) {
                case "number" -> new AdvancedGraphLiveValue(
                        type, buffer.readDouble(), false, "", buffer.readVarInt(), readEntryTypes(buffer));
                case "boolean" -> new AdvancedGraphLiveValue(
                        type, 0.0D, buffer.readBoolean(), "", buffer.readVarInt(), readEntryTypes(buffer));
                default -> new AdvancedGraphLiveValue(
                        type, 0.0D, false, buffer.readUtf(), buffer.readVarInt(), readEntryTypes(buffer));
            };
            values.put(key, val);
        }
        return values;
    }

    // Read the entry types
    private static Map<String, String> readEntryTypes(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<String, String> entryTypes = new LinkedHashMap<>(size);
        for (int idx = 0; idx < size; idx++) {
            entryTypes.put(buffer.readUtf(), buffer.readUtf());
        }
        return entryTypes;
    }
}

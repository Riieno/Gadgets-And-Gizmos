package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.WorkerPodBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerProfile;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerWorkOrder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.List;

// Apply one Worker Graph roster and configuration to an SCM-linked base station
public record WorkerPodConfigPayload(
        MenuConfigTarget target,
        UUID podId,
        List<UUID> assignedWorkers,
        List<UUID> configuredWorkers,
        boolean applyConfiguration,
        String name,
        WorkerProfile.Job job,
        WorkerResourceType resourceType,
        ResourceLocation resourceId,
        long amount,
        boolean enabled,
        WorkerWorkOrder.Mode mode,
        @Nullable UUID sourceEndpoint,
        @Nullable UUID destinationEndpoint,
        @Nullable UUID processorEndpoint,
        ResourceLocation outputResourceId,
        long outputAmount
) implements CustomPacketPayload {
    public static final Type<WorkerPodConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "worker_pod_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerPodConfigPayload> STREAM_CODEC =
            StreamCodec.of(WorkerPodConfigPayload::encode, WorkerPodConfigPayload::decode);

    // Normalize values before sending or applying them
    public WorkerPodConfigPayload {
        name = name == null || name.isBlank() ? "Worker" : name.strip();
        if (name.length() > 64) name = name.substring(0, 64);
        job = job == null ? WorkerProfile.Job.ANY : job;
        resourceType = resourceType == null ? WorkerResourceType.ITEM : resourceType;
        resourceId = resourceId == null
                ? ResourceLocation.withDefaultNamespace("air") : resourceId;
        amount = Math.max(0L, amount);
        podId = podId == null ? UUID.randomUUID() : podId;
        assignedWorkers = assignedWorkers == null ? List.of() : List.copyOf(assignedWorkers);
        configuredWorkers = configuredWorkers == null ? List.of() : List.copyOf(configuredWorkers);
        mode = mode == null ? WorkerWorkOrder.Mode.TRANSFER : mode;
        outputResourceId = outputResourceId == null ? resourceId : outputResourceId;
        outputAmount = Math.max(0L, outputAmount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Apply the configuration beside the verified open ACC menu
    public static void handle(WorkerPodConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            AnalogueContraptionControllerBlockEntity resolved = MenuBackedBlockEntityResolver.resolve(
                    context, payload.target(), AdvancedContraptionControllerMenu.class,
                    AnalogueContraptionControllerBlockEntity.class);
            AdvancedContraptionControllerBlockEntity controller = payload.target().subLevelId() == null
                    && resolved instanceof AdvancedContraptionControllerBlockEntity advanced ? advanced
                    : SimulatedHelper.findBlockEntity(context.player().level(), payload.target().subLevelId(),
                    payload.target().pos(), AdvancedContraptionControllerBlockEntity.class);
            if (controller == null || controller.getLevel() == null) return;
            WorkerPodBlockEntity pod = WorkerPodBlockEntity.linkedPods(controller).stream()
                    .filter(candidate -> payload.podId().equals(candidate.podId())).findFirst().orElse(null);
            if (pod == null) return;
            pod.bindController(controller);
            pod.assignWorkers(payload.assignedWorkers());
            if (payload.applyConfiguration()) {
                pod.configure(payload.configuredWorkers(), payload.name(), payload.job(), payload.resourceType(),
                        payload.resourceId(), payload.amount(), payload.enabled(), payload.mode(),
                        payload.sourceEndpoint(), payload.destinationEndpoint(), payload.processorEndpoint(),
                        payload.outputResourceId(), payload.outputAmount());
            }
        });
    }

    // Encode the worker configuration
    private static void encode(RegistryFriendlyByteBuf buffer, WorkerPodConfigPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUUID(payload.podId());
        writeUuids(buffer, payload.assignedWorkers());
        writeUuids(buffer, payload.configuredWorkers());
        buffer.writeBoolean(payload.applyConfiguration());
        buffer.writeUtf(payload.name(), 64);
        buffer.writeEnum(payload.job());
        buffer.writeEnum(payload.resourceType());
        buffer.writeResourceLocation(payload.resourceId());
        buffer.writeVarLong(payload.amount());
        buffer.writeBoolean(payload.enabled());
        buffer.writeEnum(payload.mode());
        writeUuid(buffer, payload.sourceEndpoint());
        writeUuid(buffer, payload.destinationEndpoint());
        writeUuid(buffer, payload.processorEndpoint());
        buffer.writeResourceLocation(payload.outputResourceId());
        buffer.writeVarLong(payload.outputAmount());
    }

    // Decode the worker configuration
    private static WorkerPodConfigPayload decode(RegistryFriendlyByteBuf buffer) {
        return new WorkerPodConfigPayload(MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readUUID(), readUuids(buffer), readUuids(buffer),
                buffer.readBoolean(), buffer.readUtf(64), buffer.readEnum(WorkerProfile.Job.class),
                buffer.readEnum(WorkerResourceType.class), buffer.readResourceLocation(),
                buffer.readVarLong(), buffer.readBoolean(), buffer.readEnum(WorkerWorkOrder.Mode.class),
                readUuid(buffer), readUuid(buffer), readUuid(buffer),
                buffer.readResourceLocation(), buffer.readVarLong());
    }

    // Write an optional endpoint id
    private static void writeUuid(RegistryFriendlyByteBuf buffer, @Nullable UUID id) {
        buffer.writeBoolean(id != null);
        if (id != null) buffer.writeUUID(id);
    }

    // Read an optional endpoint id
    private static @Nullable UUID readUuid(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    // Write a bounded worker id list
    private static void writeUuids(RegistryFriendlyByteBuf buffer, List<UUID> ids) {
        int size = Math.min(128, ids == null ? 0 : ids.size());
        buffer.writeVarInt(size);
        for (int idx = 0; idx < size; idx++) buffer.writeUUID(ids.get(idx));
    }

    // Read a bounded worker id list
    private static List<UUID> readUuids(RegistryFriendlyByteBuf buffer) {
        int size = Math.min(128, Math.max(0, buffer.readVarInt()));
        java.util.ArrayList<UUID> ids = new java.util.ArrayList<>(size);
        for (int idx = 0; idx < size; idx++) ids.add(buffer.readUUID());
        return List.copyOf(ids);
    }
}

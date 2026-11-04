package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinSkinLookup;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariant;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariants;
import com.rieno.gadgetsandgizmos.content.WorkerPodBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Apply one explicitly requested Worker Graph mannequin skin change
public record WorkerSkinChangePayload(MenuConfigTarget target, UUID podId, UUID workerId,
                                      String playerName) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<WorkerSkinChangePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "worker_skin_change"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerSkinChangePayload> STREAM_CODEC =
            StreamCodec.of(WorkerSkinChangePayload::encode, WorkerSkinChangePayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Normalize the requested profile name
    public WorkerSkinChangePayload {
        playerName = playerName == null ? "" : playerName.strip();
        if (playerName.length() > 16) playerName = playerName.substring(0, 16);
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

    // Resolve and apply the selected worker skin through the verified open ACC menu
    public static void handle(WorkerSkinChangePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            WorkerPodBlockEntity pod = resolvePod(context, payload);
            if (pod == null) {
                sendResult(player, payload.workerId(), false, "", "Worker Pod unavailable");
                return;
            }
            if (!pod.assignedWorkerIds().contains(payload.workerId())) {
                sendResult(player, payload.workerId(), false, "", "Worker is no longer assigned");
                return;
            }
            PlayerMannequinVariant supporter = PlayerMannequinVariants.byNameTag(payload.playerName());
            if (supporter != null) {
                boolean applied = pod.applyWorkerSkin(payload.workerId(), supporter.displayName().getString(),
                        supporter, "", false);
                sendResult(player, payload.workerId(), applied, supporter.displayName().getString(),
                        applied ? "Using bundled supporter skin" : "Assigned worker unavailable");
                return;
            }
            String playerName = PlayerMannequinSkinLookup.normalizePlayerName(payload.playerName());
            if (playerName.isBlank()) {
                boolean applied = pod.applyWorkerSkin(payload.workerId(), "", null, "", true);
                sendResult(player, payload.workerId(), false, "",
                        applied ? "Player not found" : "Assigned worker unavailable");
                return;
            }
            PlayerMannequinSkinLookup.lookup(playerName).thenAccept(result -> player.server.execute(() -> {
                if (pod.isRemoved()) {
                    sendResult(player, payload.workerId(), false, "", "Worker Pod unavailable");
                    return;
                }
                boolean applied = result.found()
                        ? pod.applyWorkerSkin(payload.workerId(), result.playerName(), null,
                        result.skinUrl(), false)
                        : pod.applyWorkerSkin(payload.workerId(), "", null, "", true);
                boolean success = result.found() && applied;
                String message = applied ? result.message() : "Assigned worker unavailable";
                sendResult(player, payload.workerId(), success, result.playerName(), message);
            }));
        });
    }

    // Resolve the linked Worker Pod through the menu-authorized controller
    private static WorkerPodBlockEntity resolvePod(IPayloadContext context, WorkerSkinChangePayload payload) {
        AnalogueContraptionControllerBlockEntity resolved = MenuBackedBlockEntityResolver.resolve(
                context, payload.target(), AdvancedContraptionControllerMenu.class,
                AnalogueContraptionControllerBlockEntity.class);
        AdvancedContraptionControllerBlockEntity controller = payload.target().subLevelId() == null
                && resolved instanceof AdvancedContraptionControllerBlockEntity advanced ? advanced
                : SimulatedHelper.findBlockEntity(context.player().level(), payload.target().subLevelId(),
                payload.target().pos(), AdvancedContraptionControllerBlockEntity.class);
        if (controller == null || controller.getLevel() == null) return null;
        WorkerPodBlockEntity pod = WorkerPodBlockEntity.linkedPods(controller).stream()
                .filter(candidate -> payload.podId().equals(candidate.podId())).findFirst().orElse(null);
        if (pod != null) pod.bindController(controller);
        return pod;
    }

    // Send one skin-application result back to the requesting client
    private static void sendResult(ServerPlayer player, UUID workerId, boolean success,
                                   String playerName, String message) {
        if (player == null || workerId == null) return;
        PacketDistributor.sendToPlayer(player, new WorkerSkinChangeResultPayload(workerId, success,
                playerName, message));
    }

    // Encode the requested worker skin change
    private static void encode(RegistryFriendlyByteBuf buffer, WorkerSkinChangePayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUUID(payload.podId());
        buffer.writeUUID(payload.workerId());
        buffer.writeUtf(payload.playerName(), 16);
    }

    // Decode the requested worker skin change
    private static WorkerSkinChangePayload decode(RegistryFriendlyByteBuf buffer) {
        return new WorkerSkinChangePayload(MenuConfigTarget.STREAM_CODEC.decode(buffer), buffer.readUUID(),
                buffer.readUUID(), buffer.readUtf(16));
    }
}

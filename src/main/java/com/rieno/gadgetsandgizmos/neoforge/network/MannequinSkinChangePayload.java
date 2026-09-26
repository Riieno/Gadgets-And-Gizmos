package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinSkinLookup;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariant;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariants;
import com.rieno.gadgetsandgizmos.neoforge.CTPlayerEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Apply one explicitly requested Player Mannequin skin change
public record MannequinSkinChangePayload(int entityId, String playerName) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<MannequinSkinChangePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "mannequin_skin_change"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MannequinSkinChangePayload> STREAM_CODEC =
            StreamCodec.of(MannequinSkinChangePayload::encode, MannequinSkinChangePayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound the player-entered skin name before it reaches the server
    public MannequinSkinChangePayload {
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

    // Resolve and apply a requested skin while the player can still use this mannequin
    public static void handle(MannequinSkinChangePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            PlayerMannequinEntity mannequin = resolveMannequin(player, level, payload.entityId(), null);
            if (mannequin == null) {
                sendResult(player, payload.entityId(), MannequinSkinChangeResultPayload.Result.UNAVAILABLE);
                return;
            }

            PlayerMannequinVariant supporter = PlayerMannequinVariants.byNameTag(payload.playerName());
            if (supporter != null) {
                applySupporterSkin(mannequin, supporter);
                sendResult(player, payload.entityId(), MannequinSkinChangeResultPayload.Result.APPLIED);
                return;
            }

            String playerName = PlayerMannequinSkinLookup.normalizePlayerName(payload.playerName());
            if (playerName.isBlank()) {
                sendResult(player, payload.entityId(), MannequinSkinChangeResultPayload.Result.PLAYER_DOES_NOT_EXIST);
                return;
            }

            UUID mannequinId = mannequin.getUUID();
            PlayerMannequinSkinLookup.lookup(playerName).thenAccept(result -> player.server.execute(() -> {
                PlayerMannequinEntity current = resolveMannequin(player, level, payload.entityId(), mannequinId);
                if (current == null) {
                    sendResult(player, payload.entityId(), MannequinSkinChangeResultPayload.Result.UNAVAILABLE);
                    return;
                }
                if (!result.found()) {
                    MannequinSkinChangeResultPayload.Result status = result.status()
                            == PlayerMannequinSkinLookup.Status.PLAYER_DOES_NOT_EXIST
                            ? MannequinSkinChangeResultPayload.Result.PLAYER_DOES_NOT_EXIST
                            : MannequinSkinChangeResultPayload.Result.UNABLE_TO_SET_SKIN;
                    sendResult(player, payload.entityId(), status);
                    return;
                }
                if (!applyRemoteSkin(current, result.skinUrl(), result.slim())) {
                    sendResult(player, payload.entityId(), MannequinSkinChangeResultPayload.Result.UNABLE_TO_SET_SKIN);
                    return;
                }
                sendResult(player, payload.entityId(), MannequinSkinChangeResultPayload.Result.APPLIED);
            }));
        });
    }

    // Revalidate the menu-equivalent interaction conditions before every change
    private static PlayerMannequinEntity resolveMannequin(ServerPlayer player, ServerLevel level,
                                                           int entityId, UUID expectedId) {
        if (player == null || level == null || player.serverLevel() != level) return null;
        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof PlayerMannequinEntity mannequin)
                || !mannequin.isAlive()
                || (expectedId != null && !expectedId.equals(mannequin.getUUID()))
                || !CTPlayerEvents.canUseArmorStandPoseGui(mannequin)
                || player.distanceToSqr(mannequin) > 64.0D) {
            return null;
        }
        return mannequin;
    }

    // Replace only the visible skin with one bundled supporter variant
    private static void applySupporterSkin(PlayerMannequinEntity mannequin, PlayerMannequinVariant variant) {
        mannequin.setVariant(variant);
        mannequin.setRemoteSkinUrl("");
        mannequin.setSteveSkin(false);
        mannequin.setSlimSkin(variant.slim());
    }

    // Replace only the visible skin with a URL validated by both lookup and entity storage
    private static boolean applyRemoteSkin(PlayerMannequinEntity mannequin, String skinUrl, boolean slim) {
        mannequin.setVariant(PlayerMannequinVariants.DEFAULT_ID);
        mannequin.setRemoteSkinUrl(skinUrl);
        mannequin.setSteveSkin(false);
        mannequin.setSlimSkin(slim);
        return !mannequin.remoteSkinUrl().isBlank();
    }

    // Return one outcome to the player who requested the change
    private static void sendResult(ServerPlayer player, int entityId, MannequinSkinChangeResultPayload.Result result) {
        if (player == null) return;
        PacketDistributor.sendToPlayer(player, new MannequinSkinChangeResultPayload(entityId, result));
    }

    // Encode the requested skin change
    private static void encode(RegistryFriendlyByteBuf buffer, MannequinSkinChangePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.playerName(), 16);
    }

    // Decode the requested skin change
    private static MannequinSkinChangePayload decode(RegistryFriendlyByteBuf buffer) {
        return new MannequinSkinChangePayload(buffer.readVarInt(), buffer.readUtf(16));
    }
}
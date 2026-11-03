package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.ShipPermissions;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.UUID;

// Mutate an authenticated menu's live draft independently of save and apply
public record AdvancedControllerDraftPayload(MenuConfigTarget target, int containerId, UUID sessionId,
        long baseRevision, long revision, boolean full, CompoundTag patch) implements CustomPacketPayload{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AdvancedControllerDraftPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "advanced_controller_draft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancedControllerDraftPayload> STREAM_CODEC =
            StreamCodec.of(AdvancedControllerDraftPayload::encode, AdvancedControllerDraftPayload::decode);

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public AdvancedControllerDraftPayload{ patch = patch.copy(); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(AdvancedControllerDraftPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof AdvancedContraptionControllerMenu menu)
                    || menu.containerId != payload.containerId() || !menu.stillValid(player)
                    || !Objects.equals(menu.getContentPos(), payload.target().pos())
                    || !Objects.equals(menu.getContentSubLevelId(), payload.target().subLevelId())) return;
            var controller = menu.getMenuConfigTargetBlockEntity();
            if(controller == null || !ShipPermissions.allows(player, controller, ShipPermission.ACC_GRAPH)
                    || !WorldAccessPolicy.canAccessLocal(player, player.serverLevel(),
                    SableLevelApi.containingId(controller), controller.getBlockPos())) return;
            com.rieno.gadgetsandgizmos.lib.graph.edit.PrivilegedGraphEdits.run(player, () -> menu.editDraftSession(player, payload));
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AdvancedControllerDraftPayload payload){
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeVarInt(payload.containerId());
        buffer.writeUUID(payload.sessionId());
        buffer.writeLong(payload.baseRevision());
        buffer.writeLong(payload.revision());
        buffer.writeBoolean(payload.full());
        GraphNbtPayloadCodec.write(buffer, payload.patch());
    }

    private static AdvancedControllerDraftPayload decode(RegistryFriendlyByteBuf buffer){
        return new AdvancedControllerDraftPayload(MenuConfigTarget.STREAM_CODEC.decode(buffer), buffer.readVarInt(),
                buffer.readUUID(), buffer.readLong(), buffer.readLong(), buffer.readBoolean(), GraphNbtPayloadCodec.read(buffer));
    }
}

package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.config.TabletAppsServerConfig;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem;
import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicClientFiles;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppAccess;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPurchaseScope;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Authenticate private client file chunks using the same tablet source as app actions
public record DigisableSchematicUploadPayload(DiagnosticTabletActionPayload request, int total, int offset, byte[] data)
        implements CustomPacketPayload{
    public static final Type<DigisableSchematicUploadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "digisable_schematic_upload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DigisableSchematicUploadPayload> STREAM_CODEC =
            StreamCodec.of(DigisableSchematicUploadPayload::encode, DigisableSchematicUploadPayload::decode);

    // Copy the supplied chunk at the protocol boundary
    public DigisableSchematicUploadPayload{ data = data.clone(); }
    @Override public byte[] data(){ return data.clone(); }
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep client files private and run their preview only after the whole file arrives
    public static void handle(DigisableSchematicUploadPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(!(ctx.player() instanceof ServerPlayer player) || !authorized(player, payload.request())) return;
            try{
                UUID id = UUID.fromString(payload.request().value());
                if(SchematicClientFiles.receive(player.server, player.getUUID(), id, payload.total(), payload.offset(), payload.data()))
                    DiagnosticTabletActionPayload.handle(payload.request(), ctx);
            }catch(Exception err){
                SchematicClientFiles.cancelUpload(player.server, player.getUUID());
                player.displayClientMessage(Component.literal(err.getMessage() == null ? "Client schematic upload failed" : err.getMessage()), true);
            }
        });
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Require a purchased app and an accessible tablet for every received chunk
    private static boolean authorized(ServerPlayer player, DiagnosticTabletActionPayload request){
        if(!TabletAppsServerConfig.SCHEMATICS.get() || !CTFeatureToggles.isItemEnabled("diagnostic_tablet")
                || !PaidTabletApps.DIGISABLE.id().equals(request.appId()) || !"schematics".equals(request.tabId())
                || !"schematic_preview".equals(request.actionId()) || request.sourceTabletId() == null) return false;
        DiagnosticTabletData.State state;
        if(request.placed()){
            var target = SimulatedHelper.findLoadedBlockEntityExact(player.level(), request.tabletSubLevelId(), request.tabletPos());
            if(!(target instanceof DiagnosticTabletBlockEntity tablet)) return false;
            var point = SimulatedHelper.toGlobalWorldPosition(tablet, tablet.getBlockPos().getCenter());
            if(point == null || player.distanceToSqr(point) > 256
                    || !WorldAccessPolicy.canAccessLocal(player, player.serverLevel(), request.tabletSubLevelId(), request.tabletPos())) return false;
            state = tablet.state();
        }else{
            var stack = player.getItemInHand(request.hand());
            if(!(stack.getItem() instanceof DiagnosticTabletItem)) return false;
            state = DiagnosticTabletData.read(stack);
        }
        return request.sourceTabletId().equals(state.tabletId())
                && TabletStorageApi.storage().installedApps(state.tabletId()).contains(request.appId())
                && TabletAppAccess.canUse(PaidTabletApps.DIGISABLE, TabletAppPurchaseScope.PLAYER, player.getUUID(), state.tabletId());
    }

    // Encode source authentication and one bounded file slice
    private static void encode(RegistryFriendlyByteBuf buffer, DigisableSchematicUploadPayload payload){
        DiagnosticTabletActionPayload.STREAM_CODEC.encode(buffer, payload.request());
        buffer.writeVarInt(payload.total()); buffer.writeVarInt(payload.offset()); buffer.writeByteArray(payload.data());
    }

    // Bound chunk allocation before dispatching the upload
    private static DigisableSchematicUploadPayload decode(RegistryFriendlyByteBuf buffer){
        return new DigisableSchematicUploadPayload(DiagnosticTabletActionPayload.STREAM_CODEC.decode(buffer),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(SchematicClientFiles.CHUNK_BYTES));
    }
}

package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlCompatibility;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShipPermissions;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class AdvancedGraphApplyRefreshTest{
    @Test void applyingFromAnOpenMenuSendsTheFreshSourceSnapshotAndFailedSavesDoNot(){
        for(boolean saved : new boolean[]{true, false}){
            var controller = mock(AdvancedContraptionControllerBlockEntity.class);
            var context = mock(IPayloadContext.class);
            var player = mock(ServerPlayer.class);var level = mock(ServerLevel.class);
            var target = MenuConfigTarget.of(BlockPos.ZERO);
            var applied = new AdvancedGraphDocument();applied.setRevision(2);
            var schedule = new AdvancedGraphDocument();
            when(context.player()).thenReturn(player);when(player.serverLevel()).thenReturn(level);
            when(controller.getBlockPos()).thenReturn(BlockPos.ZERO);
            when(controller.getDraftGraph()).thenReturn(applied);when(controller.getActiveGraph()).thenReturn(applied);
            when(controller.getShippingScheduleDraftGraph()).thenReturn(schedule);when(controller.getShippingScheduleActiveGraph()).thenReturn(schedule);
            when(controller.saveDraft(any(), eq(1))).thenReturn(saved);when(controller.applyDraft()).thenReturn(true);
            doAnswer(call -> { ((Runnable)call.getArgument(0)).run();return CompletableFuture.completedFuture(null); })
                    .when(context).enqueueWork(any(Runnable.class));
            try(var resolver = mockStatic(MenuBackedBlockEntityResolver.class);
                var permissions = mockStatic(ShipPermissions.class);var access = mockStatic(WorldAccessPolicy.class);
                var sable = mockStatic(SableLevelApi.class);var author = mockStatic(FlightControlCompatibility.class);
                var packets = mockStatic(PacketDistributor.class);var snapshots = mockStatic(AdvancedControllerGraphSnapshotPayload.class)){
                resolver.when(() -> MenuBackedBlockEntityResolver.resolve(context, target,
                        AdvancedContraptionControllerMenu.class, AnalogueContraptionControllerBlockEntity.class)).thenReturn(controller);
                permissions.when(() -> ShipPermissions.allows(player, controller, ShipPermission.ACC_GRAPH)).thenReturn(true);
                access.when(() -> WorldAccessPolicy.canAccessLocal(player, level, null, BlockPos.ZERO)).thenReturn(true);
                var payload = new AdvancedContraptionControllerGraphPayload(target, "save_apply", 1, applied.toTag(), "", 7);
                AdvancedContraptionControllerGraphPayload.handle(payload, context);
                if(saved){
                    verify(controller).applyDraft();
                    snapshots.verify(() -> AdvancedControllerGraphSnapshotPayload.send(player, BlockPos.ZERO, null,
                            applied, applied, schedule, schedule));
                }else{
                    verify(controller, never()).applyDraft();snapshots.verifyNoInteractions();
                }
            }
        }
    }
}

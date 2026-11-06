package com.rieno.gadgetsandgizmos.content.tablet;

import net.minecraft.network.chat.Component;
import com.rieno.gadgetsandgizmos.lib.text.UserMessageException;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlock;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

// Resolve loaded app targets and validate a new pairing before persisting it
final class TabletAppTargets{
    private TabletAppTargets(){}

    static BlockEntity resolve(TabletActionContext ctx){
        return ctx.blockPos() == null ? null : SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(), ctx.subLevelId(), ctx.blockPos());
    }

    static BlockEntity support(TabletActionContext ctx){
        if(!ctx.placedSource() || ctx.sourceBlockPos() == null) return null;
        var be = SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(), ctx.sourceSubLevelId(), ctx.sourceBlockPos());
        if(!(be instanceof DiagnosticTabletBlockEntity tablet)) return null;
        return tablet.getLevel().getBlockEntity(tablet.getBlockPos().relative(tablet.getBlockState().getValue(DiagnosticTabletBlock.FACING).getOpposite()));
    }

    static DiagnosticTabletData.Binding pair(TabletActionContext ctx, ResourceLocation app){
        var selections = DiagnosticTabletAppStorage.selections(ctx.player().server, ctx.sourceTabletId(), app);
        var binding = selections.isEmpty() ? DiagnosticTabletAppStorage.selectedBinding(ctx.player().server, ctx.sourceTabletId(), app) : selections.getLast();
        if(binding == null) throw new UserMessageException(Component.translatable("createthrusters.text.use_reader_mode_on_a_target_first"));
        var be = SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(), binding.subLevelId(), binding.pos());
        var body = com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi.subLevel(ctx.player().level(), binding.subLevelId());
        if(binding.subLevelId() != null && body == null) throw new UserMessageException(Component.translatable("createthrusters.text.the_selected_sublevel_is_unavailable"));
        var point = binding.subLevelId() == null ? binding.pos().getCenter()
                : com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi.toWorldPosition(body, binding.pos().getCenter());
        if(point == null || ctx.player().distanceToSqr(point) > 256) throw new UserMessageException(Component.translatable("createthrusters.text.pair_the_target_from_within_16_blocks"));
        if(!WorldAccessPolicy.canAccessLocal(ctx.player(), ctx.player().serverLevel(), binding.subLevelId(), binding.pos())) throw new UserMessageException(Component.translatable("createthrusters.text.this_target_is_protected"));
        if(app.equals(PaidTabletApps.BLOCKMATES.id()) && !(be instanceof com.rieno.gadgetsandgizmos.lib.worker.WorkerOrchestrator)) throw new UserMessageException(Component.translatable("createthrusters.text.blockmates_must_be_linked_to_an_acc_managing_workers"));
        if(app.equals(PaidTabletApps.DIGISABLE.id()) && !(be instanceof com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity)) throw new UserMessageException(Component.translatable("createthrusters.text.pair_digisable_with_an_acc_worker_storage_network"));
        DiagnosticTabletAppStorage.addBinding(ctx.player().server, ctx.sourceTabletId(), app, binding, true);
        DiagnosticTabletAppStorage.clearSelections(ctx.player().server, ctx.sourceTabletId(), app);
        return binding;
    }

    static void requireAccess(TabletActionContext ctx, BlockEntity target){
        if(target == null) throw new UserMessageException(Component.translatable("createthrusters.text.the_target_is_unloaded_or_unavailable"));
        ServerLevel level = ctx.player().serverLevel();
        if(!WorldAccessPolicy.canAccessLocal(ctx.player(), level, SimulatedHelper.getContainingSubLevelId(target), target.getBlockPos())) throw new UserMessageException(Component.translatable("createthrusters.text.this_target_is_protected"));
    }
}

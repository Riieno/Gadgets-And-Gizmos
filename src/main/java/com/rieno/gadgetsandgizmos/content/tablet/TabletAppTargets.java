package com.rieno.gadgetsandgizmos.content.tablet;

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
        if(binding == null) throw new IllegalArgumentException("Use Reader mode on a target first");
        var be = SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(), binding.subLevelId(), binding.pos());
        var body = com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi.subLevel(ctx.player().level(), binding.subLevelId());
        if(binding.subLevelId() != null && body == null) throw new IllegalArgumentException("The selected sublevel is unavailable");
        var point = binding.subLevelId() == null ? binding.pos().getCenter()
                : com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi.toWorldPosition(body, binding.pos().getCenter());
        if(point == null || ctx.player().distanceToSqr(point) > 256) throw new IllegalArgumentException("Pair the target from within 16 blocks");
        if(!WorldAccessPolicy.canAccessLocal(ctx.player(), ctx.player().serverLevel(), binding.subLevelId(), binding.pos())) throw new IllegalArgumentException("This target is protected");
        if(app.equals(PaidTabletApps.BLOCKMATES.id()) && !(be instanceof com.rieno.gadgetsandgizmos.lib.worker.WorkerOrchestrator)) throw new IllegalArgumentException("Blockmates must be linked to an ACC managing workers");
        DiagnosticTabletAppStorage.addBinding(ctx.player().server, ctx.sourceTabletId(), app, binding, true);
        DiagnosticTabletAppStorage.clearSelections(ctx.player().server, ctx.sourceTabletId(), app);
        return binding;
    }

    static void requireAccess(TabletActionContext ctx, BlockEntity target){
        if(target == null) throw new IllegalArgumentException("The target is unloaded or unavailable");
        ServerLevel level = ctx.player().serverLevel();
        if(!WorldAccessPolicy.canAccessLocal(ctx.player(), level, SimulatedHelper.getContainingSubLevelId(target), target.getBlockPos())) throw new IllegalArgumentException("This target is protected");
    }
}
